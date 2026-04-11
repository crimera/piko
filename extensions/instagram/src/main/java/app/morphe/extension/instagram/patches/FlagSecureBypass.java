/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.morphe.extension.instagram.patches;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

@SuppressWarnings("unused")
public class FlagSecureBypass {
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        if (!SettingsStatus.disableFlagSecure) return;
        initialized = true;

        try {
            Context context = Utils.getContext();
            if (!(context instanceof Application)) return;
            Application app = (Application) context;

            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    clearFlagSecure(activity);
                    attachLayoutListener(activity);
                }

                @Override
                public void onActivityStarted(Activity activity) {
                    clearFlagSecure(activity);
                }

                @Override
                public void onActivityResumed(Activity activity) {
                    clearFlagSecure(activity);
                }

                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                @Override public void onActivityDestroyed(Activity activity) {}
            });
        } catch (Exception e) {
            Logger.printException(() -> "Failed to initialize FLAG_SECURE bypass", e);
        }
    }

    private static void clearFlagSecure(Activity activity) {
        try {
            if (Pref.disableFlagSecure()) {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        } catch (Exception e) {
            Logger.printException(() -> "Failed to clear FLAG_SECURE", e);
        }
    }

    private static void attachLayoutListener(Activity activity) {
        try {
            View decorView = activity.getWindow().getDecorView();
            decorView.getViewTreeObserver().addOnGlobalLayoutListener(
                    () -> clearFlagSecure(activity)
            );
        } catch (Exception e) {
            Logger.printException(() -> "Failed to attach layout listener", e);
        }
    }
}
