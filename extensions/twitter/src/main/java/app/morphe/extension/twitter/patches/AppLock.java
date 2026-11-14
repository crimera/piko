/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 * Copyright 2026 Hushfeed contributors <https://github.com/SysAdminDoc/hushfeed>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.twitter.Pref;

/**
 * Hides the app behind a cover until the device's biometric or screen lock confirms it is you. Each screen is covered
 * rather than redirected, so one opened from a link is still there once unlocked. The lock comes up on a cold start and
 * when the app returns after longer in the background than the chosen delay.
 */
@SuppressWarnings("deprecation")
public final class AppLock {
    private static final String COVER_TAG = "piko_app_lock";
    private static final String CREDENTIAL_TAG = "piko_app_lock_credential";
    // Long enough for a link's trampoline activity to hand over and finish before anything is asked.
    private static final long ASK_DELAY_MS = 250;
    private static final long NEVER = Long.MIN_VALUE;
    private static final String USE_BIOMETRIC = "android.permission.USE_BIOMETRIC";

    private enum Asking { NONE, PROMPT, CREDENTIAL }

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Set<Activity> started = weakSet();
    private static final Set<Activity> covered = weakSet();
    // Windows given FLAG_SECURE on the way out, below Android 13, to clear on return.
    private static final Set<Activity> secured = weakSet();
    // Screens told to leave no recents screenshot, Android 13 and up.
    private static final Set<Activity> unsnapped = weakSet();
    private static final Map<View, Integer> hiddenFromAccessibility = new WeakHashMap<>();
    private static boolean installed;
    private static Boolean mainProcess;
    private static WeakReference<Activity> resumed = new WeakReference<>(null);
    // A new process has been away, which is how a cold start counts.
    private static boolean away = true;
    private static long leftAt = NEVER;
    private static boolean locked;
    private static Asking asking = Asking.NONE;
    // The prompt was closed; only the Unlock button asks again.
    private static boolean declined;
    private static int systemCancels;
    private static boolean toldNoScreenLock;

    private static final Runnable askRunnable = () -> guard(() -> {
        Activity activity = resumed.get();
        if (activity != null && !declined) ask(activity);
    });

    private static final Application.ActivityLifecycleCallbacks callbacks =
            new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityStarted(Activity activity) { guard(() -> onStarted(activity)); }
                @Override public void onActivityResumed(Activity activity) { guard(() -> onResumed(activity)); }
                // Before the screen's own pause, so the flag is in as early as possible.
                @Override public void onActivityPrePaused(Activity activity) { guard(() -> hideFromRecents(activity)); }
                @Override public void onActivityPaused(Activity activity) { guard(() -> onPaused(activity)); }
                @Override public void onActivityStopped(Activity activity) { guard(() -> onStopped(activity)); }
                @Override public void onActivityCreated(Activity a, Bundle b) { }
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
                @Override public void onActivityDestroyed(Activity a) { }
            };

    private AppLock() {
    }

    private static Set<Activity> weakSet() {
        return Collections.newSetFromMap(new WeakHashMap<>());
    }

    /** Called from the application's onCreate, before any activity exists, so a link's trampoline is covered too. */
    public static void install(Application application) {
        try {
            if (installed) return;
            installed = true;
            application.registerActivityLifecycleCallbacks(callbacks);
        } catch (Throwable t) {
            Logger.printException(() -> "AppLock install failed", t);
        }
    }

    // Every callback runs inside the app's own lifecycle, where nothing may escape.
    private static void guard(Runnable step) {
        try {
            step.run();
        } catch (Throwable t) {
            Logger.printException(() -> "AppLock failed", t);
        }
    }

    // Lock state isn't shared with screens running in another process.
    private static boolean inMainProcess() {
        if (mainProcess == null) {
            mainProcess = Build.VERSION.SDK_INT < 28 || !Application.getProcessName().contains(":");
        }
        return mainProcess;
    }

    private static void onStarted(Activity activity) {
        if (!inMainProcess()) return;
        started.add(activity);
        boolean enabled = Pref.appLock();
        if (away) {
            away = false;
            if (enabled && !locked && (leftAt == NEVER
                    || SystemClock.elapsedRealtime() - leftAt >= Pref.appLockTimeoutMillis())) {
                lock(activity);
            }
        }
        if (locked && !enabled) unlock();
        // Before the screen's first frame, which comes after its resume.
        if (locked) cover(activity);
        syncRecentsScreenshot(activity, enabled);
    }

    private static void onResumed(Activity activity) {
        if (!inMainProcess()) return;
        resumed = new WeakReference<>(activity);
        if (locked) keepCovered(activity);
        showInRecentsAgain(activity);
        syncRecentsScreenshot(activity, Pref.appLock());
    }

    private static void keepCovered(Activity activity) {
        if (!cover(activity)) {
            // A screen with no content of its own only gets its decor after this resume.
            handler.post(() -> guard(() -> {
                if (locked && !activity.isFinishing()) cover(activity);
            }));
        }
        // The confirm screen answers before the screen that asked resumes, so a request still open was left behind.
        if (asking == Asking.CREDENTIAL) asking = Asking.NONE;
        if (!declined && asking == Asking.NONE) {
            handler.removeCallbacks(askRunnable);
            handler.postDelayed(askRunnable, ASK_DELAY_MS);
        }
    }

    private static void onPaused(Activity activity) {
        if (!inMainProcess()) return;
        if (resumed.get() == activity) resumed = new WeakReference<>(null);
        hideFromRecents(activity);
    }

    private static void onStopped(Activity activity) {
        if (!inMainProcess()) return;
        started.remove(activity);
        // A rotation or the confirm screen stops the activity without the app being left.
        if (!started.isEmpty() || activity.isChangingConfigurations() || asking != Asking.NONE) return;
        away = true;
        leftAt = SystemClock.elapsedRealtime();
        declined = false;
        systemCancels = 0;
    }

    private static void lock(Activity activity) {
        // Without a screen lock there is nothing to check against, and locking would only trap the user.
        if (!deviceSecure(activity)) {
            tellNoScreenLock();
            return;
        }
        locked = true;
        declined = false;
        systemCancels = 0;
    }

    private static void unlock() {
        locked = false;
        asking = Asking.NONE;
        declined = false;
        systemCancels = 0;
        handler.removeCallbacks(askRunnable);
        for (Activity activity : new ArrayList<>(covered)) uncover(activity);
        covered.clear();
    }

    private static void ask(Activity activity) {
        handler.removeCallbacks(askRunnable);
        if (!locked || asking != Asking.NONE || activity == null || activity.isFinishing()) return;
        if (!deviceSecure(activity)) {
            letInWithoutScreenLock();
            return;
        }
        if (Build.VERSION.SDK_INT >= 28
                && activity.checkSelfPermission(USE_BIOMETRIC) == PackageManager.PERMISSION_GRANTED) {
            asking = Asking.PROMPT;
            try {
                Prompt.show(activity);
                return;
            } catch (RuntimeException e) {
                asking = Asking.NONE;
                Logger.printInfo(() -> "AppLock prompt refused, using the confirm screen: " + e);
            }
        }
        askForCredential(activity);
    }

    private static void askForCredential(Activity activity) {
        KeyguardManager keyguard = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        Intent intent = keyguard == null ? null
                : keyguard.createConfirmDeviceCredentialIntent(promptTitle(activity), null);
        if (intent == null) {
            letInWithoutScreenLock();
            return;
        }
        FragmentManager fragments = activity.getFragmentManager();
        if (fragments == null || fragments.isDestroyed()) return;
        asking = Asking.CREDENTIAL;
        CredentialRequest request = new CredentialRequest();
        Bundle arguments = new Bundle();
        arguments.putParcelable(CredentialRequest.INTENT, intent);
        request.setArguments(arguments);
        fragments.beginTransaction().add(request, CREDENTIAL_TAG).commitAllowingStateLoss();
    }

    private static void credentialAnswered(boolean confirmed) {
        asking = Asking.NONE;
        if (confirmed) {
            unlock();
        } else {
            declined = true;
        }
    }

    private static void onPromptError(int code, Activity activity) {
        asking = Asking.NONE;
        switch (code) {
            case BiometricPrompt.BIOMETRIC_ERROR_CANCELED:
                // The system took it down, so ask again on the screen in front, but not in a loop.
                if (++systemCancels > 2) {
                    declined = true;
                } else if (resumed.get() != null) {
                    handler.removeCallbacks(askRunnable);
                    handler.postDelayed(askRunnable, ASK_DELAY_MS);
                }
                return;
            case BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL:
                letInWithoutScreenLock();
                return;
            case BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE:
            case BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT:
            case BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT:
            case BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS:
            case BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT:
                // Android 9's prompt can't offer the screen lock, so the confirm screen does.
                if (Build.VERSION.SDK_INT < 29 && activity != null && !activity.isFinishing()) {
                    askForCredential(activity);
                    return;
                }
                declined = true;
                return;
            default:
                declined = true;
        }
    }

    private static void letInWithoutScreenLock() {
        unlock();
        tellNoScreenLock();
    }

    private static void tellNoScreenLock() {
        if (toldNoScreenLock) return;
        toldNoScreenLock = true;
        PikoUtils.toast(str("piko_app_lock_no_screen_lock"));
    }

    private static boolean deviceSecure(Context context) {
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return keyguard != null && keyguard.isDeviceSecure();
    }

    // The app's own label, so "Bring back twitter" reads right here as well.
    private static CharSequence appName(Context context) {
        return context.getApplicationInfo().loadLabel(context.getPackageManager());
    }

    private static String promptTitle(Context context) {
        return str("piko_app_lock_prompt", appName(context));
    }

    // Kept apart so no API 28 type is loaded on an older device.
    @RequiresApi(28)
    private static final class Prompt {
        static void show(Activity activity) {
            Executor executor = activity.getMainExecutor();
            WeakReference<Activity> owner = new WeakReference<>(activity);
            BiometricPrompt.Builder builder = new BiometricPrompt.Builder(activity)
                    .setTitle(promptTitle(activity));
            if (Build.VERSION.SDK_INT >= 30) {
                builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            } else if (Build.VERSION.SDK_INT == 29) {
                builder.setDeviceCredentialAllowed(true);
            } else {
                builder.setNegativeButton(str("piko_app_lock_use_screen_lock"), executor,
                        (dialog, which) -> guard(() -> {
                            asking = Asking.NONE;
                            Activity shown = owner.get();
                            if (shown != null && !shown.isFinishing()) askForCredential(shown);
                        }));
            }
            builder.build().authenticate(new CancellationSignal(), executor,
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            guard(AppLock::unlock);
                        }

                        @Override
                        public void onAuthenticationError(int code, CharSequence message) {
                            guard(() -> onPromptError(code, owner.get()));
                        }
                    });
        }
    }

    /** Starts the confirm-credential screen and takes its answer, which goes to whoever asked for it. */
    public static final class CredentialRequest extends Fragment {
        static final String INTENT = "piko_confirm_intent";
        static final int REQUEST = 0x4C4B;

        public CredentialRequest() {
        }

        @Override
        public void onCreate(Bundle state) {
            super.onCreate(state);
            // Restored with its screen already up, or already answered: nothing to start.
            if (state != null) return;
            Bundle arguments = getArguments();
            Intent intent = arguments == null ? null : arguments.getParcelable(INTENT);
            try {
                if (intent == null) throw new IllegalStateException("no confirm intent");
                startActivityForResult(intent, REQUEST);
            } catch (RuntimeException e) {
                leave();
                Logger.printInfo(() -> "AppLock has no confirm screen to ask with: " + e);
                guard(AppLock::letInWithoutScreenLock);
            }
        }

        @Override
        public void onActivityResult(int request, int result, Intent data) {
            if (request != REQUEST) return;
            leave();
            guard(() -> credentialAnswered(result == Activity.RESULT_OK));
        }

        private void leave() {
            FragmentManager fragments = getFragmentManager();
            if (fragments != null) fragments.beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }

    /** Covers the screen, or returns false when it has no decor to cover yet. */
    private static boolean cover(Activity activity) {
        ViewGroup decor = decorOf(activity);
        if (decor == null) return false;
        covered.add(activity);
        if (coverIn(decor) != null) return true;

        FrameLayout cover = new FrameLayout(activity);
        cover.setTag(COVER_TAG);
        cover.setBackgroundColor(Color.BLACK);
        // Swallows every touch, so nothing under it can be pressed.
        cover.setClickable(true);
        cover.setFocusable(true);
        // Above anything the app raised on the decor, with no shadow to draw for it.
        cover.setOutlineProvider(null);
        cover.setTranslationZ(dp(activity, 1000));

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        int side = dp(activity, 24);
        column.setPadding(side, side, side, side);

        String titleText = str("piko_app_lock_title", appName(activity));
        if (Build.VERSION.SDK_INT >= 28) cover.setAccessibilityPaneTitle(titleText);
        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setGravity(Gravity.CENTER);
        column.addView(title);

        Button unlock = new Button(activity);
        unlock.setText(str("piko_app_lock_unlock"));
        unlock.setAllCaps(false);
        unlock.setStateListAnimator(null);
        unlock.setTextColor(Color.WHITE);
        unlock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(40, 255, 255, 255));
        background.setCornerRadius(dp(activity, 12));
        background.setStroke(Math.max(1, dp(activity, 1)), Color.argb(90, 255, 255, 255));
        unlock.setBackground(background);
        int padding = dp(activity, 20);
        unlock.setPadding(padding, dp(activity, 14), padding, dp(activity, 14));
        unlock.setMinimumHeight(dp(activity, 48));
        LinearLayout.LayoutParams unlockParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        unlockParams.topMargin = dp(activity, 28);
        unlock.setLayoutParams(unlockParams);
        unlock.setOnClickListener(view -> guard(() -> {
            declined = false;
            systemCancels = 0;
            ask(activity);
        }));
        column.addView(unlock);

        cover.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        decor.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // A screen reader can't reach what is under it either.
        hideBehind(decor, cover);
        return true;
    }

    private static void uncover(Activity activity) {
        ViewGroup decor = decorOf(activity);
        View cover = decor == null ? null : coverIn(decor);
        if (cover == null) return;
        showBehind(decor);
        decor.removeView(cover);
    }

    private static void hideBehind(ViewGroup decor, View cover) {
        for (int index = 0; index < decor.getChildCount(); index++) {
            View child = decor.getChildAt(index);
            if (child == cover) continue;
            hiddenFromAccessibility.put(child, child.getImportantForAccessibility());
            child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
    }

    private static void showBehind(ViewGroup decor) {
        for (int index = 0; index < decor.getChildCount(); index++) {
            View child = decor.getChildAt(index);
            Integer previous = hiddenFromAccessibility.remove(child);
            if (previous != null) child.setImportantForAccessibility(previous);
        }
    }

    // Peeks at the decor because creating it early would refuse a later window feature.
    private static ViewGroup decorOf(Activity activity) {
        Window window = activity.getWindow();
        View decor = window == null ? null : window.peekDecorView();
        return decor instanceof ViewGroup ? (ViewGroup) decor : null;
    }

    private static View coverIn(ViewGroup decor) {
        for (int index = 0; index < decor.getChildCount(); index++) {
            View child = decor.getChildAt(index);
            if (COVER_TAG.equals(child.getTag())) return child;
        }
        return null;
    }

    private static int dp(Context context, int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    private static void syncRecentsScreenshot(Activity activity, boolean enabled) {
        if (Build.VERSION.SDK_INT < 33) return;
        if (enabled) {
            if (unsnapped.add(activity)) activity.setRecentsScreenshotEnabled(false);
        } else if (unsnapped.remove(activity)) {
            activity.setRecentsScreenshotEnabled(true);
        }
    }

    // Android 12 and older can't turn the snapshot off, so the window is marked secure on its way out instead.
    private static void hideFromRecents(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33 || !inMainProcess() || secured.contains(activity) || !Pref.appLock()) return;
        Window window = activity.getWindow();
        if (window == null) return;
        if ((window.getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0) return;
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        secured.add(activity);
    }

    private static void showInRecentsAgain(Activity activity) {
        if (!secured.remove(activity)) return;
        Window window = activity.getWindow();
        if (window != null) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
}
