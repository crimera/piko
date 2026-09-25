/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.wireguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wireguard.config.Config;

import app.morphe.extension.twitter.settings.ActivityHook;
import app.morphe.extension.twitter.settings.Settings;

@SuppressWarnings("deprecation")
public final class WireGuardFragment extends PreferenceFragment implements WireGuardManager.Listener {
    private static final int IMPORT = 4711;
    private static final int CONSENT = 4712;
    private WireGuardManager manager;
    private Context themed;
    private Preference status, config, lastError, connect, disconnect, delete, edit, importConfig;
    private SwitchPreference enabled, auto;
    private AlertDialog editorDialog;
    private AlertDialog transferDialog;
    private final android.os.Handler transferHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable transferTick = this::updateTransfer;

    private void showTransfer() {
        if (transferDialog != null) return;
        transferDialog = new AlertDialog.Builder(themed)
                .setTitle(text("piko_wireguard_transfer"))
                .setMessage(text("piko_wireguard_transfer_loading"))
                .setPositiveButton(android.R.string.ok, null).create();
        transferDialog.setOnDismissListener(dialog -> {
            transferHandler.removeCallbacks(transferTick);
            transferDialog = null;
        });
        transferDialog.show();
        updateTransfer();
    }

    private void updateTransfer() {
        final AlertDialog dialog = transferDialog;
        if (dialog == null || !dialog.isShowing()) return;
        manager.readTransfer((rx, tx) -> {
            if (transferDialog != dialog || !dialog.isShowing() || !isResumed()) return;
            String message = rx < 0 || tx < 0 ? text("piko_wireguard_transfer_unavailable")
                    : text("piko_wireguard_transfer_rx") + ": " + String.format(java.util.Locale.getDefault(), "%,d", rx)
                    + " " + text("piko_wireguard_bytes") + "\n"
                    + text("piko_wireguard_transfer_tx") + ": " + String.format(java.util.Locale.getDefault(), "%,d", tx)
                    + " " + text("piko_wireguard_bytes");
            dialog.setMessage(message + "\n\n" + text("piko_wireguard_transfer_help"));
            transferHandler.postDelayed(transferTick, 1_000);
        });
    }
    private EditText editor;
    private boolean replacingImport;
    private boolean consentPending;

    static String text(Context context, String name) {
        int id = context.getResources().getIdentifier(name, "string", context.getPackageName());
        return id == 0 ? name : context.getString(id);
    }
    private String text(String name) { return text(themed, name); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        themed = ActivityHook.getPreferenceContext(getActivity());
        manager = WireGuardManager.get(getActivity());
        if (state != null) {
            replacingImport = state.getBoolean("replacing_import");
            consentPending = state.getBoolean("consent_pending");
        }
        getPreferenceManager().setSharedPreferencesName(Settings.SHARED_PREF_NAME);
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(themed);
        setPreferenceScreen(screen);
        enabled = toggle("piko_wireguard_enable", manager.enabled(), value -> manager.setEnabled(value));
        auto = toggle("piko_wireguard_auto", manager.autoConnect(), value -> manager.setAutoConnect(value));
        auto.setSummary(text("piko_wireguard_auto_help"));
        row("piko_wireguard_help", "piko_wireguard_description", null);
        category("piko_wireguard_configuration");
        importConfig = row("piko_wireguard_import", null, () -> confirmReplacement(() -> {
            replacingImport = manager.snapshot().hasConfig;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                    .addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(intent, IMPORT); }
            catch (Exception error) { manager.reportError("piko_wireguard_error_picker"); }
        }));
        edit = row("piko_wireguard_edit", "piko_wireguard_edit_help", () -> manager.loadForEditing(this::showEditor));
        config = row("piko_wireguard_current", null, null);
        delete = row("piko_wireguard_delete", null, () -> new AlertDialog.Builder(themed)
                .setTitle(text("piko_wireguard_delete"))
                .setMessage(text("piko_wireguard_delete_confirm"))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> manager.deleteConfiguration()).show());
        category("piko_wireguard_connection");
        status = row("piko_wireguard_status", null, this::showTransfer);
        connect = row("piko_wireguard_connect", null, this::requestConnect);
        disconnect = row("piko_wireguard_disconnect", null, manager::disconnect);
        category("piko_wireguard_diagnostics");
        lastError = row("piko_wireguard_last_error", null, null);
        row("piko_wireguard_licenses", "piko_wireguard_licenses_summary", () -> {
            try (java.io.InputStream input = getActivity().getAssets().open("piko-wireguard/NOTICE")) {
                byte[] bytes = WireGuardStorage.readBounded(input, 32768);
                new AlertDialog.Builder(themed).setTitle(text("piko_wireguard_licenses"))
                        .setMessage(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
                        .setPositiveButton(android.R.string.ok, null).show();
            } catch (Exception error) { manager.reportError("piko_wireguard_error_notice"); }
        });
        changed();
    }

    private interface Toggle { void change(boolean value); }
    private SwitchPreference toggle(String title, boolean checked, Toggle action) {
        SwitchPreference pref = new SwitchPreference(themed);
        pref.setPersistent(false);
        pref.setTitle(text(title));
        pref.setChecked(checked);
        pref.setOnPreferenceChangeListener((p, value) -> { action.change((Boolean) value); return true; });
        getPreferenceScreen().addPreference(pref);
        return pref;
    }

    private void category(String title) {
        PreferenceCategory category = new PreferenceCategory(themed);
        category.setTitle(text(title));
        getPreferenceScreen().addPreference(category);
    }

    private Preference row(String title, String summary, Runnable action) {
        Preference pref = new Preference(themed);
        pref.setPersistent(false);
        pref.setTitle(text(title));
        if (summary != null) pref.setSummary(text(summary));
        if (action != null) pref.setOnPreferenceClickListener(p -> { action.run(); return true; });
        else pref.setSelectable(false);
        getPreferenceScreen().addPreference(pref);
        return pref;
    }

    private void confirmReplacement(Runnable action) {
        if (!manager.snapshot().hasConfig) { action.run(); return; }
        new AlertDialog.Builder(themed).setTitle(text("piko_wireguard_replace"))
                .setMessage(text("piko_wireguard_replace_confirm"))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> action.run()).show();
    }

    private void requestConnect() {
        if (consentPending) return;
        try {
            if (manager.hasOtherVpn()) {
                manager.reportError("piko_wireguard_error_conflict");
                return;
            }
            Intent intent = VpnService.prepare(getActivity());
            if (intent == null) manager.connect();
            else {
                consentPending = true;
                startActivityForResult(intent, CONSENT);
            }
        } catch (Exception | LinkageError error) {
            consentPending = false;
            manager.reportError("piko_wireguard_error_permission");
        }
    }

    @Override public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == CONSENT) {
            consentPending = false;
            if (result == Activity.RESULT_OK) manager.connect();
            else manager.permissionDenied();
        } else if (request == IMPORT && result == Activity.RESULT_OK && data != null && data.getData() != null) {
            manager.importDocument(data.getData(), replacingImport);
            replacingImport = false;
        }
    }

    @Override public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("replacing_import", replacingImport);
        out.putBoolean("consent_pending", consentPending);
        // No configuration, key, or editor content is placed into Activity saved state.
    }

    private void showEditor(Config configuration) {
        if (!isAdded() || getActivity().isFinishing()) return;
        LinearLayout layout = new LinearLayout(themed);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        TextView help = new TextView(themed);
        help.setText(text("piko_wireguard_editor_help"));
        layout.addView(help);
        editor = new EditText(themed);
        editor.setSaveEnabled(false);
        editor.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        editor.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editor.setTypeface(android.graphics.Typeface.MONOSPACE);
        editor.setMinLines(12);
        editor.setText(configuration == null ? "[Interface]\nPrivateKey = \nAddress = \nDNS = \n\n[Peer]\nPublicKey = \nEndpoint = \nAllowedIPs = \n"
                : configuration.toWgQuickString());
        layout.addView(editor);
        ScrollView scroll = new ScrollView(themed);
        scroll.setSaveEnabled(false);
        scroll.addView(layout);
        editorDialog = new AlertDialog.Builder(themed).setTitle(text("piko_wireguard_edit"))
                .setView(scroll).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(text("piko_wireguard_save"), null).create();
        editorDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        editorDialog.setOnDismissListener(dialog -> {
            if (editor != null) editor.getText().clear();
            editor = null;
            editorDialog = null;
        });
        editorDialog.show();
        editorDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            // Keep edits on validation failure so users can correct a field without retyping keys.
            String value = editor.getText().toString();
            try {
                WireGuardConfig.parse(value, getActivity().getPackageName());
                confirmReplacement(() -> {
                    manager.saveText(value, manager.snapshot().hasConfig);
                    if (editorDialog != null) editorDialog.dismiss();
                });
            } catch (com.wireguard.config.BadConfigException error) {
                editor.setError(WireGuardConfig.validationError(error));
            } catch (Exception error) { editor.setError(text("piko_wireguard_error_config")); }
        });
    }

    @Override public void changed() {
        if (status == null || !isAdded()) return;
        WireGuardManager.Snapshot current = manager.snapshot();
        enabled.setChecked(manager.enabled());
        auto.setChecked(manager.autoConnect());
        status.setSummary(text("piko_wireguard_state_" + current.state.name().toLowerCase(java.util.Locale.ROOT)));
        if (current.state == WireGuardManager.State.CONNECTED || current.state == WireGuardManager.State.ERROR
                || current.state == WireGuardManager.State.CONNECTING
                || current.state == WireGuardManager.State.VERIFYING
                || current.state == WireGuardManager.State.UNCONFIRMED) {
            GradientDrawable light = new GradientDrawable();
            light.setShape(GradientDrawable.OVAL);
            light.setColor(current.state == WireGuardManager.State.CONNECTED ? Color.rgb(46, 170, 85)
                    : current.state == WireGuardManager.State.ERROR ? Color.rgb(215, 50, 55) : Color.rgb(224, 158, 30));
            int size = (int) (12 * getResources().getDisplayMetrics().density);
            light.setSize(size, size);
            status.setIcon(light);
        } else status.setIcon(null);
        config.setSummary(text(current.hasConfig ? "piko_wireguard_config_saved" : "piko_wireguard_state_no_config"));
        String[] error = current.error.split("\\|", 2);
        lastError.setSummary(current.error.isEmpty() ? text("piko_wireguard_none")
                : text(error[0]) + (error.length == 2 ? "\n" + error[1] : ""));
        boolean busy = current.state == WireGuardManager.State.CONNECTING;
        connect.setEnabled(manager.enabled() && current.hasConfig && !current.active && !busy);
        disconnect.setEnabled(current.active || busy);
        delete.setEnabled(current.hasConfig && !busy);
        edit.setEnabled(!busy);
        importConfig.setEnabled(!busy);
    }

    @Override public void onResume() { super.onResume(); manager.addListener(this); changed(); }
    @Override public void onPause() {
        if (transferDialog != null) transferDialog.dismiss();
        transferHandler.removeCallbacks(transferTick);
        manager.removeListener(this);
        super.onPause();
    }
    @Override public void onDestroyView() {
        if (editorDialog != null) editorDialog.dismiss();
        if (transferDialog != null) transferDialog.dismiss();
        transferHandler.removeCallbacks(transferTick);
        super.onDestroyView();
    }
}
