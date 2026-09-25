/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.wireguard;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import com.wireguard.android.backend.BackendException;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.android.backend.Statistics;
import com.wireguard.crypto.Key;
import com.wireguard.config.BadConfigException;
import com.wireguard.config.Config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.twitter.settings.Settings;

/** All backend and storage operations run on one worker, never the UI thread. */
public final class WireGuardManager {
    public enum State { DISABLED, NO_CONFIG, NEEDS_VPN_PERMISSION, DISCONNECTED, CONNECTING, VERIFYING, UNCONFIRMED, CONNECTED, ERROR }
    public interface Listener { void changed(); }
    public interface ConfigCallback { void loaded(Config config); }
    public interface TransferCallback { void loaded(long received, long sent); }

    public void readTransfer(TransferCallback callback) {
        worker.execute(() -> {
            long received = -1, sent = -1;
            try {
                if (active && backend != null) {
                    Statistics statistics = backend.getStatistics(tunnel);
                    received = statistics.totalRx();
                    sent = statistics.totalTx();
                }
            } catch (Exception | LinkageError ignored) { }
            final long rx = received, tx = sent;
            main.post(() -> callback.loaded(rx, tx));
        });
    }

    public static final class Snapshot {
        public final State state;
        public final String error;
        public final boolean hasConfig;
        public final boolean active;
        Snapshot(State state, String error, boolean hasConfig, boolean active) {
            this.state = state;
            this.error = error;
            this.hasConfig = hasConfig;
            this.active = active;
        }
    }

    private static volatile WireGuardManager instance;
    private final Context context;
    private final SharedPreferences preferences;
    private final WireGuardStorage storage;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private GoBackend backend;
    private Key[] activePeers = new Key[0];
    private long tunnelStartedAt;
    // Only the visible connection screen requests statistics; no wake locks or probes.
    private final Runnable verificationTick = () -> worker.execute(() -> {
        if (listeners.isEmpty()) return;
        verifyConnection();
        scheduleVerification();
    });

    private void scheduleVerification() {
        main.post(() -> {
            main.removeCallbacks(verificationTick);
            if (!listeners.isEmpty() && snapshot.active) main.postDelayed(verificationTick, 5_000);
        });
    }

    private void verifyConnection() {
        if (!active || backend == null || snapshot.state == State.ERROR) return;
        State state;
        try {
            Statistics statistics = backend.getStatistics(tunnel);
            long[] handshakes = new long[activePeers.length];
            for (int i = 0; i < activePeers.length; i++) {
                Statistics.PeerStats peer = statistics.peer(activePeers[i]);
                handshakes[i] = peer == null ? 0 : peer.latestHandshakeEpochMillis();
            }
            state = switch (WireGuardHealth.evaluate(handshakes, System.currentTimeMillis(),
                    SystemClock.elapsedRealtime() - tunnelStartedAt)) {
                case VERIFIED -> State.CONNECTED;
                case WAITING -> State.VERIFYING;
                case UNCONFIRMED -> State.UNCONFIRMED;
            };
        } catch (Exception | LinkageError ignored) {
            // An unavailable statistics read is not proof that the tunnel failed.
            state = State.UNCONFIRMED;
        }
        if (state != snapshot.state) publish(state, "");
    }
    private boolean active;
    private boolean stopping;
    private boolean started;
    private volatile boolean serviceStarting;
    private volatile boolean serviceStopping;
    private boolean reconnectAfterStop;
    private final AtomicBoolean connectQueued = new AtomicBoolean();
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (prefs, key) -> {
        // Also handle Piko settings restore/reset while a tunnel is running.
        if (key == null || Settings.WIREGUARD_ENABLED.key.equals(key)) {
            worker.execute(() -> {
                if (!enabled()) disconnectInternal();
                else refreshInternal();
            });
        }
    };
    private volatile Snapshot snapshot = new Snapshot(State.DISABLED, "", false, false);
    private final Tunnel tunnel = new Tunnel() {
        public String getName() { return "Piko"; }
        public void onStateChange(State state) {
            active = state == State.UP;
            if (!active && !stopping) publish(WireGuardManager.State.ERROR, "piko_wireguard_error_stopped");
        }
    };

    private WireGuardManager(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(Settings.SHARED_PREF_NAME, Context.MODE_PRIVATE);
        storage = new WireGuardStorage(this.context);
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
    }

    public static synchronized WireGuardManager get(Context context) {
        if (instance == null) instance = new WireGuardManager(context);
        return instance;
    }

    // Called at the application hook before any activity. No UI or backend work here.
    public static void initialize(Context context) {
        try {
            String processName = null;
            if (Build.VERSION.SDK_INT >= 28) processName = Application.getProcessName();
            else {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                for (ActivityManager.RunningAppProcessInfo process : am.getRunningAppProcesses()) {
                    if (process.pid == Process.myPid()) processName = process.processName;
                }
            }
            if (!context.getPackageName().equals(processName)) return;
            WireGuardManager manager = get(context);
            manager.worker.execute(() -> {
                if (manager.started) return;
                manager.started = true;
                try {
                    if (manager.enabled() && manager.autoConnect()) manager.connectInternal();
                    else manager.refreshInternal();
                } catch (Exception | LinkageError error) { manager.fail("piko_wireguard_error_start"); }
            });
        } catch (Exception | LinkageError ignored) {
            // A disabled or unavailable VPN must never prevent X startup.
        }
    }

    public Snapshot snapshot() { return snapshot; }
    public boolean enabled() { return booleanPreference(Settings.WIREGUARD_ENABLED.key); }
    public boolean autoConnect() { return booleanPreference(Settings.WIREGUARD_AUTO_CONNECT.key); }
    private boolean booleanPreference(String key) {
        try { return preferences.getBoolean(key, false); }
        catch (ClassCastException error) { return false; }
    }
    public void addListener(Listener listener) { listeners.add(listener); refresh(); }
    public void removeListener(Listener listener) {
        listeners.remove(listener);
        if (listeners.isEmpty()) main.removeCallbacks(verificationTick);
    }
    public void setAutoConnect(boolean value) { preferences.edit().putBoolean(Settings.WIREGUARD_AUTO_CONNECT.key, value).apply(); }

    public void setEnabled(boolean value) {
        preferences.edit().putBoolean(Settings.WIREGUARD_ENABLED.key, value).apply();
    }

    private void publish(State state, String error) {
        snapshot = new Snapshot(state, error, storage.exists(), active);
        main.post(() -> { for (Listener listener : listeners) listener.changed(); });
    }

    private void fail(String error) {
        // Only fixed resource identifiers are passed to the logger, never exceptions/configs.
        Logger.printInfo(() -> "WireGuard: " + error);
        publish(State.ERROR, error);
    }

    public void refresh() { worker.execute(this::refreshInternal); }
    private void refreshInternal() {
        try {
            if (active && !enabled()) { disconnectInternal(); return; }
            if (active) {
                verifyConnection();
                scheduleVerification();
                return;
            }
            if (snapshot.state == State.ERROR) return;
            if (!enabled()) publish(State.DISABLED, "");
            else if (!storage.exists()) publish(State.NO_CONFIG, "");
            else if (VpnService.prepare(context) != null) publish(State.NEEDS_VPN_PERMISSION, "");
            else publish(State.DISCONNECTED, "");
        } catch (Exception | LinkageError error) { fail("piko_wireguard_error_start"); }
    }

    public void permissionDenied() {
        worker.execute(() -> publish(State.NEEDS_VPN_PERMISSION, "piko_wireguard_error_permission"));
    }
    public void reportError(String resource) { worker.execute(() -> fail(resource)); }
    public void connect() {
        if (!connectQueued.compareAndSet(false, true)) return;
        worker.execute(() -> {
            try { connectInternal(); }
            finally { connectQueued.set(false); }
        });
    }

    public boolean hasOtherVpn() {
        if (snapshot.active) return false;
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        for (Network network : connectivity.getAllNetworks()) {
            NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
        }
        return false;
    }

    private void connectInternal() {
        if (active) return;
        if (serviceStopping) { reconnectAfterStop = true; return; }
        try {
            if (!enabled()) { publish(State.DISABLED, ""); return; }
            if (!storage.exists()) { publish(State.NO_CONFIG, ""); return; }
            if (VpnService.prepare(context) != null) {
                publish(State.NEEDS_VPN_PERMISSION, "piko_wireguard_error_permission");
                return;
            }
            if (hasOtherVpn()) { fail("piko_wireguard_error_conflict"); return; }
            publish(State.CONNECTING, "");
            Config config;
            try { config = storage.load(); }
            catch (Exception error) { fail("piko_wireguard_error_storage"); return; }
            if (backend == null) try { backend = new GoBackend(new ContextWrapper(context) {
                @Override public ComponentName startService(Intent intent) {
                    // GoBackend explicitly starts its nested service. Redirect that one intent
                    // to our subclass, which adds the Android foreground lifecycle.
                    if (intent.getComponent() != null && intent.getComponent().getClassName()
                            .equals(GoBackend.VpnService.class.getName())) {
                        serviceStarting = true;
                        try { return context.startForegroundService(new Intent(context, WireGuardVpnService.class)); }
                        catch (RuntimeException error) { serviceStarting = false; throw error; }
                    }
                    return super.startService(intent);
                }
            }); } catch (Exception | LinkageError error) {
                fail("piko_wireguard_error_native"); return;
            }
            active = backend.setState(tunnel, Tunnel.State.UP, config) == Tunnel.State.UP;
            if (active) {
                activePeers = config.getPeers().stream().map(peer -> peer.getPublicKey()).toArray(Key[]::new);
                tunnelStartedAt = SystemClock.elapsedRealtime();
                publish(State.VERIFYING, "");
                if (!listeners.isEmpty()) {
                    verifyConnection();
                    scheduleVerification();
                }
            }
            else fail("piko_wireguard_error_start");
        } catch (BackendException error) {
            String message = switch (error.getReason()) {
                case VPN_NOT_AUTHORIZED -> "piko_wireguard_error_permission";
                case DNS_RESOLUTION_FAILURE -> "piko_wireguard_error_dns";
                default -> "piko_wireguard_error_start";
            };
            fail(message);
            stopService();
        } catch (LinkageError error) {
            fail("piko_wireguard_error_native"); stopService();
        } catch (Exception error) {
            fail("piko_wireguard_error_start"); stopService();
        }
    }

    public void disconnect() { worker.execute(this::disconnectInternal); }
    private boolean disconnectInternal() {
        reconnectAfterStop = false;
        main.removeCallbacks(verificationTick);
        stopping = true;
        try {
            if (backend != null) backend.setState(tunnel, Tunnel.State.DOWN, null);
            active = false;
            if (!stopService()) return false;
            publish(enabled() ? State.DISCONNECTED : State.DISABLED, "");
            refreshInternal();
            return true;
        } catch (Exception | LinkageError error) {
            fail("piko_wireguard_error_stop"); return false;
        } finally { stopping = false; }
    }

    private boolean stopService() {
        try {
            if (serviceStarting) serviceStopping = true;
            context.stopService(new Intent(context, WireGuardVpnService.class));
            return true;
        } catch (RuntimeException ignored) {
            fail("piko_wireguard_error_stop");
            return false;
        }
    }

    // Serialize upstream native cleanup with setState, including unexpected revocation.
    void serviceDestroyed(Runnable cleanup) {
        worker.execute(() -> {
            boolean wasActive = active;
            stopping = true;
            try { cleanup.run(); active = false; }
            catch (Exception | LinkageError error) { fail("piko_wireguard_error_stop"); }
            finally { stopping = false; serviceStarting = false; serviceStopping = false; }
            if (wasActive) fail("piko_wireguard_error_stopped");
            if (reconnectAfterStop) {
                reconnectAfterStop = false;
                connectInternal();
            }
        });
    }

    public void importDocument(Uri uri, boolean replace) {
        worker.execute(() -> {
            byte[] bytes = null;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                bytes = WireGuardStorage.readBounded(input, WireGuardConfig.MAX_BYTES);
                saveInternal(WireGuardConfig.parse(new String(bytes, StandardCharsets.UTF_8), context.getPackageName()), replace);
            } catch (BadConfigException error) { failValidation(error); }
            catch (Exception | LinkageError error) { fail("piko_wireguard_error_import"); }
            finally { if (bytes != null) Arrays.fill(bytes, (byte) 0); }
        });
    }

    public void saveText(String text, boolean replace) {
        worker.execute(() -> {
            try { saveInternal(WireGuardConfig.parse(text, context.getPackageName()), replace); }
            catch (BadConfigException error) { failValidation(error); }
            catch (Exception | LinkageError error) { fail("piko_wireguard_error_storage"); }
        });
    }

    private void saveInternal(Config config, boolean replace) throws Exception {
        if (storage.exists() && !replace) { fail("piko_wireguard_error_replace"); return; }
        // Validate before stopping; a malformed replacement leaves the existing tunnel intact.
        if (!disconnectInternal()) return;
        storage.save(config);
        publish(State.DISCONNECTED, "");
        refreshInternal();
    }

    private void failValidation(BadConfigException error) {
        // This value is rendered as plain text, not used as a format string.
        publish(State.ERROR, "piko_wireguard_error_config|" + WireGuardConfig.validationError(error));
    }

    public void loadForEditing(ConfigCallback callback) {
        worker.execute(() -> {
            try {
                Config config = storage.exists() ? storage.load() : null;
                main.post(() -> callback.loaded(config));
            } catch (Exception | LinkageError error) { fail("piko_wireguard_error_storage"); }
        });
    }

    public void deleteConfiguration() {
        worker.execute(() -> {
            if (!disconnectInternal()) return;
            try {
                storage.delete();
                publish(enabled() ? State.NO_CONFIG : State.DISABLED, "");
            } catch (Exception | LinkageError error) { fail("piko_wireguard_error_storage"); }
        });
    }
}
