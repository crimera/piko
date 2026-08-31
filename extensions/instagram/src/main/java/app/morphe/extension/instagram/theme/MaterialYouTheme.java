/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.instagram.theme;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.widget.RadioGroup;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

import java.util.Map;
import java.util.WeakHashMap;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public final class MaterialYouTheme {
    private static final String PIKO_SETTINGS_ACTIVITY_PACKAGE =
            "app.morphe.extension.instagram.settings.";
    private static boolean initializationFailureLogged;
    private static boolean lifecycleCallbacksRegistered;
    private static Boolean observedInstagramDark;
    private static final ActivityThemeGeneration ACTIVITY_GENERATIONS =
            new ActivityThemeGeneration();
    private static int themeTransitionDepth;
    private static Activity themeTransitionActivity;
    private static boolean themeTransitionOverlayChanged;
    private static boolean themeTransitionPikoRecreate;
    private static int lastComposePrismOverrideArgb = Integer.MIN_VALUE;
    private static int lastComposeSearchRowOverrideArgb = Integer.MIN_VALUE;

    private static final Application.ActivityLifecycleCallbacks ACTIVITY_CALLBACKS =
            new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityPreCreated(Activity activity, Bundle savedInstanceState) {
            applyToActivity(activity);
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityResumed(Activity activity) {
            boolean stale = ACTIVITY_GENERATIONS.claimStale(activity);
            if (stale) {
                activity.recreate();
                return;
            }
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            ACTIVITY_GENERATIONS.remove(activity);
        }
    };

    private MaterialYouTheme() {
    }

    public static void initialize(Context context) {
        if (Build.VERSION.SDK_INT < 31) {
            return;
        }

        try {
            Context applicationContext = context.getApplicationContext();
            if (applicationContext == null) {
                applicationContext = context;
            }
            MaterialYouThemeAPI31.initialize(applicationContext);
            ThemeMode mode = currentMode();
            // The first activity must refresh colors after attaching its overlay.
            lastComposePrismOverrideArgb = Integer.MIN_VALUE;
            lastComposeSearchRowOverrideArgb =
                    MaterialYouState.composeSearchRowOverrideArgb(mode, 0);
            registerActivityCallbacks(applicationContext);
        } catch (Exception exception) {
            logInitializationFailureOnce(exception);
        }
    }

    public static void applyToActivity(Activity activity) {
        if (Build.VERSION.SDK_INT < 31
                || activity == null
                || Looper.myLooper() != Looper.getMainLooper()) {
            return;
        }

        ThemeMode persistedMode = currentMode();
        boolean instagramDark = resolveInstagramDark(activity);
        ThemeMode requestedMode = availableRequestedModeOrFallback(
                persistedMode,
                instagramDark
        );
        ThemeMode previousEffectiveMode = MaterialYouState.modeForEffectiveTheme(
                persistedMode,
                instagramDark
        );
        ThemeMode requestedEffectiveMode = MaterialYouState.modeForEffectiveTheme(
                requestedMode,
                instagramDark
        );
        boolean resourcesChanged = previousEffectiveMode != requestedEffectiveMode;
        if (!MaterialYouThemeAPI31.setResourcesMode(
                activity,
                requestedEffectiveMode,
                instagramDark
        )) {
            if (resourcesChanged) {
                MaterialYouThemeAPI31.setResourcesMode(
                        activity,
                        previousEffectiveMode,
                        instagramDark
                );
            }
            return;
        }
        if (requestedMode != persistedMode && !persistMode(requestedMode)) {
            if (resourcesChanged) {
                MaterialYouThemeAPI31.setResourcesMode(
                        activity,
                        previousEffectiveMode,
                        instagramDark
                );
            }
            persistMode(persistedMode);
            return;
        }
        applyComposePrismMode(activity, requestedEffectiveMode);
        ACTIVITY_GENERATIONS.recordCreated(activity);
    }

    public static int getSystemUiMode() {
        return Build.VERSION.SDK_INT >= 31
                ? MaterialYouThemeAPI31.getSystemNightMask()
                : Configuration.UI_MODE_NIGHT_UNDEFINED;
    }

    public static int resolveSystemUiModeCache(int cachedNightMask) {
        int liveSystemNightMask = getSystemUiMode();
        return MaterialYouState.resolveCachedSystemNightMask(
                Build.VERSION.SDK_INT,
                cachedNightMask,
                liveSystemNightMask
        );
    }

    public static boolean isMaterialYouAvailable() {
        return Build.VERSION.SDK_INT >= 31 && MaterialYouThemeAPI31.isReady(ThemeMode.MATERIAL_YOU);
    }

    public static boolean isAmoledAvailable() {
        return Build.VERSION.SDK_INT >= 31 && MaterialYouThemeAPI31.isReady(ThemeMode.AMOLED);
    }

    public static boolean canEnableAmoled(Context context) {
        Activity activity = getActivity(context);
        boolean available = isAmoledAvailable();
        boolean instagramDark = resolveInstagramDark(activity);
        return available && instagramDark;
    }

    public static boolean isMaterialYouEnabled() {
        return isMaterialYouAvailable()
                && MaterialYouState.hasMaterialYou(currentMode());
    }

    public static boolean isAmoledEnabled() {
        return isAmoledAvailable()
                && MaterialYouState.hasAmoled(currentMode());
    }

    public static Function1<Integer, Unit> wrapNativeThemeCallback(
            Function1<Integer, Unit> callback
    ) {
        if (Build.VERSION.SDK_INT < 31
                || callback == null
                || callback instanceof NativeThemeCallback) {
            return callback;
        }
        return new NativeThemeCallback(callback);
    }

    public static void observeComposeNativeThemeMode(int nativeMode) {
        int observedMode = MaterialYouState.sanitizeNativeThemeMode(nativeMode);
        if (MaterialYouState.shouldPersistObservedNativeMode(
                currentInstagramThemeMode(),
                observedMode,
                isInstagramThemeModeSynchronized()
        )) {
            persistInstagramThemeMode(observedMode);
        }
    }

    public static RadioGroup.OnCheckedChangeListener wrapLegacyNativeThemeListener(
            RadioGroup.OnCheckedChangeListener listener,
            int packedIds
    ) {
        if (Build.VERSION.SDK_INT < 31
                || listener == null
                || listener instanceof LegacyNativeThemeController) {
            return listener;
        }
        return new LegacyNativeThemeController(
                listener,
                MaterialYouState.unpackLegacyRadioId(packedIds, 0),
                MaterialYouState.unpackLegacyRadioId(packedIds, 1),
                MaterialYouState.unpackLegacyRadioId(packedIds, 2)
        );
    }

    public static String getLegacySelectedRadioId(String nativeId, int packedIds) {
        Integer observedNativeMode =
                MaterialYouState.nativeModeForLegacySelectionId(nativeId, packedIds);
        if (observedNativeMode != null) {
            int currentNativeMode = currentInstagramThemeMode();
            if (!MaterialYouState.shouldPersistObservedNativeMode(
                    currentNativeMode,
                    observedNativeMode,
                    isInstagramThemeModeSynchronized()
            )) {
                return nativeId;
            }
            if (currentNativeMode == observedNativeMode) {
                persistInstagramThemeMode(observedNativeMode);
                return nativeId;
            }
            Activity activity = Utils.getActivity();
            if (Build.VERSION.SDK_INT >= 31
                    && activity != null
                    && Looper.myLooper() == Looper.getMainLooper()) {
                requestNativeThemeChange(
                        activity,
                        currentModeForRequest(),
                        observedNativeMode,
                        null
                );
            } else {
                persistInstagramThemeMode(observedNativeMode);
            }
        }
        return nativeId;
    }

    public static boolean requestMaterialYouChange(Context context, boolean enabled) {
        return requestMaterialYouChange(getActivity(context), enabled);
    }

    public static boolean requestAmoledChange(Context context, boolean enabled) {
        return requestAmoledChange(getActivity(context), enabled);
    }

    private static boolean requestAmoledChange(Activity activity, boolean enabled) {
        if (enabled && !canEnableAmoled(activity)) {
            return false;
        }
        return requestNativeThemeChange(
                activity,
                MaterialYouState.modeForAmoledToggle(
                        enabled,
                        currentModeForRequest()
                ),
                null,
                null
        );
    }

    private static boolean isActivityDark(Activity activity) {
        return activity != null && MaterialYouState.isActivityUiModeDark(
                activity.getResources().getConfiguration().uiMode
        );
    }

    private static boolean resolveInstagramDark(Activity activity) {
        boolean activityDark = isActivityDark(activity);
        boolean pikoSettingsActivity = activity == null
                || activity.getClass().getName().startsWith(PIKO_SETTINGS_ACTIVITY_PACKAGE);
        observedInstagramDark = MaterialYouState.updateObservedInstagramDark(
                observedInstagramDark,
                pikoSettingsActivity,
                activityDark
        );
        return MaterialYouState.resolveInstagramDark(
                observedInstagramDark,
                currentInstagramThemeMode(),
                isInstagramThemeModeSynchronized(),
                getSystemUiMode()
        );
    }

    private static boolean requestMaterialYouChange(Activity activity, boolean enabled) {
        return requestNativeThemeChange(
                activity,
                MaterialYouState.modeForMaterialYouToggle(
                        enabled,
                        currentModeForRequest()
                ),
                null,
                null
        );
    }

    private static boolean requestNativeThemeChange(
            Activity activity,
            ThemeMode mode,
            Integer nativeMode,
            Runnable nativeAction
    ) {
        if (Build.VERSION.SDK_INT < 31
                || activity == null
                || Looper.myLooper() != Looper.getMainLooper()) {
            return false;
        }

        return applyMode(
                activity,
                mode,
                nativeMode,
                nativeAction
        );
    }

    private static ThemeMode availableRequestedModeOrFallback(
            ThemeMode mode,
            boolean instagramDark
    ) {
        return MaterialYouState.availableRequestedModeOrFallback(
                mode,
                instagramDark,
                isMaterialYouAvailable(),
                isAmoledAvailable(),
                MaterialYouThemeAPI31.isReady(ThemeMode.AMOLED_MATERIAL_YOU)
        );
    }

    private static boolean applyMode(
            Activity activity,
            ThemeMode requestedMode,
            Integer nativeMode,
            Runnable nativeAction
    ) {
        ThemeMode persistedPreviousMode = currentMode();
        int previousNativeMode = currentInstagramThemeMode();
        int requestedNativeMode = nativeMode == null
                ? previousNativeMode
                : MaterialYouState.sanitizeNativeThemeMode(nativeMode);
        int systemNightMask = getSystemUiMode();
        int currentNightMask = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        boolean previousInstagramDark = resolveInstagramDark(activity);
        boolean requestedInstagramDark = nativeMode == null
                ? previousInstagramDark
                : MaterialYouState.isEffectiveDark(
                        requestedNativeMode,
                        systemNightMask
                );
        ThemeMode previousRequestedMode = availableRequestedModeOrFallback(
                persistedPreviousMode,
                previousInstagramDark
        );
        requestedMode = availableRequestedModeOrFallback(
                requestedMode,
                requestedInstagramDark
        );
        ThemeMode previousEffectiveMode = MaterialYouState.modeForEffectiveTheme(
                previousRequestedMode,
                previousInstagramDark
        );
        ThemeMode requestedEffectiveMode = MaterialYouState.modeForEffectiveTheme(
                requestedMode,
                requestedInstagramDark
        );
        int targetNightMask = MaterialYouState.targetNightMask(
                requestedNativeMode,
                systemNightMask
        );
        boolean overlayChanged = previousEffectiveMode != requestedEffectiveMode
                || previousInstagramDark != requestedInstagramDark;
        boolean pikoRecreate = MaterialYouState.shouldPikoRecreate(
                previousEffectiveMode,
                requestedEffectiveMode,
                nativeAction != null,
                currentNightMask,
                targetNightMask
        );
        if (!MaterialYouThemeAPI31.setResourcesMode(
                activity,
                requestedEffectiveMode,
                requestedInstagramDark
        )) {
            if (overlayChanged) {
                MaterialYouThemeAPI31.setResourcesMode(
                        activity,
                        previousEffectiveMode,
                        previousInstagramDark
                );
            }
            return false;
        }
        boolean requestedStateCommitted =
                (previousRequestedMode == requestedMode || persistMode(requestedMode))
                        && (!MaterialYouState.shouldPersistNativeThemeBeforeAction(
                                nativeMode != null,
                                nativeAction != null
                        ) || persistInstagramThemeMode(requestedNativeMode));
        if (!requestedStateCommitted) {
            if (overlayChanged) {
                MaterialYouThemeAPI31.setResourcesMode(
                        activity,
                        previousEffectiveMode,
                        previousInstagramDark
                );
            }
            persistMode(persistedPreviousMode);
            if (nativeMode != null) {
                persistInstagramThemeMode(previousNativeMode);
            }
            return false;
        }
        applyComposePrismMode(activity, requestedEffectiveMode);
        runThemeTransition(
                activity,
                nativeAction,
                pikoRecreate,
                overlayChanged
        );
        observedInstagramDark =
                MaterialYouState.updateObservedInstagramDarkForNativeMode(
                        observedInstagramDark,
                        nativeMode,
                        requestedInstagramDark
                );
        if (MaterialYouState.shouldPersistNativeThemeAfterAction(
                nativeMode != null,
                nativeAction != null
        )) {
            return persistInstagramThemeMode(requestedNativeMode);
        }
        return true;
    }

    private static void applyComposePrismMode(Context context, ThemeMode mode) {
        int materialYouBackgroundArgb = 0;
        if (MaterialYouState.hasMaterialYou(mode)) {
            int backgroundResourceId = ResourceUtils.getIdentifier(
                    context,
                    ResourceType.COLOR,
                    "igds_primary_background"
            );
            if (backgroundResourceId != 0) {
                try {
                    materialYouBackgroundArgb = context.getColor(backgroundResourceId);
                } catch (Resources.NotFoundException ignored) {
                }
            }
        }

        int overrideArgb = MaterialYouState.composePrismOverrideArgb(
                mode,
                materialYouBackgroundArgb
        );
        boolean changed = overrideArgb != lastComposePrismOverrideArgb;
        applyComposePrismColor(overrideArgb, changed);
        lastComposePrismOverrideArgb = overrideArgb;
        lastComposeSearchRowOverrideArgb =
                MaterialYouState.composeSearchRowOverrideArgb(
                        mode,
                        materialYouBackgroundArgb
                );
    }

    private static native void applyComposePrismColor(
            int overrideArgb,
            boolean refreshPalettes
    );

    public static long resolveComposeSearchRowBackground(long nativePackedColor) {
        return MaterialYouState.composeSearchRowBackground(
                nativePackedColor,
                lastComposeSearchRowOverrideArgb
        );
    }

    private static void runThemeTransition(
            Activity activity,
            Runnable nativeAction,
            boolean pikoRecreate,
            boolean overlayChanged
    ) {
        boolean rootTransition = themeTransitionDepth == 0;
        if (rootTransition) {
            themeTransitionActivity = activity;
            themeTransitionOverlayChanged = false;
            themeTransitionPikoRecreate = false;
        }
        themeTransitionOverlayChanged |= overlayChanged;
        themeTransitionPikoRecreate |= pikoRecreate;
        themeTransitionDepth++;
        try {
            if (nativeAction != null) {
                nativeAction.run();
            }
        } finally {
            themeTransitionDepth--;
            if (themeTransitionDepth == 0) {
                Activity refreshActivity = themeTransitionActivity;
                themeTransitionActivity = null;
                boolean changedOverlay = themeTransitionOverlayChanged;
                boolean recreateWithPiko = themeTransitionPikoRecreate;
                themeTransitionOverlayChanged = false;
                themeTransitionPikoRecreate = false;
                if (refreshActivity != null && changedOverlay) {
                    ACTIVITY_GENERATIONS.advance();
                    ACTIVITY_GENERATIONS.recordCreated(refreshActivity);
                }
                if (refreshActivity != null && recreateWithPiko) {
                    refreshActivity.recreate();
                }
            }
        }
    }

    private static ThemeMode currentMode() {
        return MaterialYouState.resolveMode(
                Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.MATERIAL_YOU_THEME)),
                Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.AMOLED_THEME))
        );
    }

    private static ThemeMode currentModeForRequest() {
        return currentMode();
    }

    private static boolean persistMode(ThemeMode mode) {
        boolean materialYouEnabled = MaterialYouState.hasMaterialYou(mode);
        boolean amoledEnabled = MaterialYouState.hasAmoled(mode);
        boolean materialYouWritten = Boolean.TRUE.equals(
                SharedPref.setBooleanPref(Settings.MATERIAL_YOU_THEME.key, materialYouEnabled)
        );
        boolean amoledWritten = Boolean.TRUE.equals(
                SharedPref.setBooleanPref(Settings.AMOLED_THEME.key, amoledEnabled)
        );
        return materialYouWritten && amoledWritten;
    }

    private static int currentInstagramThemeMode() {
        try {
            return MaterialYouState.sanitizeNativeThemeMode(
                    Integer.parseInt(SharedPref.getStringPref(Settings.INSTAGRAM_THEME_MODE))
            );
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean isInstagramThemeModeSynchronized() {
        return Boolean.TRUE.equals(
                SharedPref.getBooleanPref(Settings.INSTAGRAM_THEME_MODE_SYNCHRONIZED)
        );
    }

    private static boolean persistInstagramThemeMode(int nativeMode) {
        int sanitizedMode = MaterialYouState.sanitizeNativeThemeMode(nativeMode);
        boolean modeWritten = Boolean.TRUE.equals(
                SharedPref.setStringPref(
                        Settings.INSTAGRAM_THEME_MODE.key,
                        Integer.toString(sanitizedMode)
                )
        );
        if (!modeWritten) {
            return false;
        }
        return Boolean.TRUE.equals(
                SharedPref.setBooleanPref(
                        Settings.INSTAGRAM_THEME_MODE_SYNCHRONIZED.key,
                        true
                )
        );
    }

    private static Activity getActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }

            Context baseContext = ((ContextWrapper) current).getBaseContext();
            if (baseContext == current) {
                return null;
            }
            current = baseContext;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    private static synchronized void registerActivityCallbacks(Context context) {
        if (lifecycleCallbacksRegistered || !(context instanceof Application)) {
            return;
        }
        ((Application) context).registerActivityLifecycleCallbacks(ACTIVITY_CALLBACKS);
        lifecycleCallbacksRegistered = true;
    }

    private static synchronized void logInitializationFailureOnce(Exception exception) {
        if (initializationFailureLogged) {
            return;
        }
        initializationFailureLogged = true;
        Logger.printException(
                () -> "Failed to initialize theme resources: ",
                exception
        );
    }

    private static final class ActivityThemeGeneration {
        private final Map<Activity, Long> generations = new WeakHashMap<>();
        private long currentGeneration;

        private synchronized void recordCreated(Activity activity) {
            generations.put(activity, currentGeneration);
        }

        private synchronized void advance() {
            currentGeneration++;
        }

        private synchronized boolean claimStale(Activity activity) {
            Long activityGeneration = generations.get(activity);
            if (activityGeneration == null) {
                recordCreated(activity);
                return false;
            }
            if (activityGeneration == currentGeneration) {
                return false;
            }

            generations.put(activity, currentGeneration);
            return true;
        }

        private synchronized void remove(Activity activity) {
            generations.remove(activity);
        }
    }

    private static final class NativeThemeCallback implements Function1<Integer, Unit> {
        private final Function1<Integer, Unit> delegate;

        private NativeThemeCallback(Function1<Integer, Unit> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Unit invoke(Integer nativeMode) {
            ThemeMode currentMode = currentModeForRequest();
            requestNativeThemeChange(
                    Utils.getActivity(),
                    MaterialYouState.modeForNativeThemeSelection(currentMode),
                    nativeMode,
                    () -> delegate.invoke(nativeMode)
            );
            return Unit.INSTANCE;
        }
    }

    private static final class LegacyNativeThemeController
            implements RadioGroup.OnCheckedChangeListener {
        private final RadioGroup.OnCheckedChangeListener delegate;
        private final int lightId;
        private final int darkId;
        private final int systemId;

        private LegacyNativeThemeController(
                RadioGroup.OnCheckedChangeListener delegate,
                int lightId,
                int darkId,
                int systemId
        ) {
            this.delegate = delegate;
            this.lightId = lightId;
            this.darkId = darkId;
            this.systemId = systemId;
        }

        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            ThemeMode currentMode = currentModeForRequest();
            Integer nativeMode = MaterialYouState.nativeModeForLegacySelection(
                    checkedId,
                    lightId,
                    darkId,
                    systemId
            );
            if (nativeMode == null) {
                delegate.onCheckedChanged(group, checkedId);
                return;
            }
            requestNativeThemeChange(
                    getActivity(group.getContext()),
                    MaterialYouState.modeForNativeThemeSelection(currentMode),
                    nativeMode,
                    () -> delegate.onCheckedChanged(group, checkedId)
            );
        }
    }

}
