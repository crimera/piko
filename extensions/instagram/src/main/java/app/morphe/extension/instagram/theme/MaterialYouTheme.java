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
import android.widget.CompoundButton;
import android.widget.RadioGroup;

import app.morphe.extension.shared.SharedPref;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.utils.IgStr;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

import java.util.Map;
import java.util.WeakHashMap;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

public final class MaterialYouTheme {
    private static final int NATIVE_THEME_DARK = 2;
    private static boolean initializationFailureLogged;
    private static boolean lifecycleCallbacksRegistered;
    private static final ActivityThemeGeneration ACTIVITY_GENERATIONS =
            new ActivityThemeGeneration();
    private static int themeTransitionDepth;
    private static Activity themeTransitionActivity;
    private static boolean themeTransitionOverlayChanged;
    private static boolean themeTransitionPikoRecreate;
    private static int lastComposePrismOverrideArgb = Integer.MIN_VALUE;
    private static int lastComposeSurfaceOverrideArgb = Integer.MIN_VALUE;

    private static final Function1<Boolean, Unit> MATERIAL_YOU_TOGGLE_CALLBACK = value -> {
        requestMaterialYouChange(Utils.getActivity(), Boolean.TRUE.equals(value));
        return Unit.INSTANCE;
    };

    private static final CompoundButton.OnCheckedChangeListener LEGACY_MATERIAL_YOU_TOGGLE_LISTENER =
            (buttonView, isChecked) -> {
                if (!MaterialYouState.shouldRequestToggleChange(
                        isChecked,
                        isMaterialYouEnabled()
                )) {
                    return;
                }
                requestMaterialYouChange(
                        getActivity(buttonView.getContext()),
                        isChecked
                );
            };

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
            ThemeMode mode = availableModeOrFallback(currentMode());
            // The first activity must refresh colors after attaching its overlay.
            lastComposePrismOverrideArgb = Integer.MIN_VALUE;
            lastComposeSurfaceOverrideArgb =
                    MaterialYouState.composeSurfaceOverrideArgb(mode, 0);
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
        ThemeMode mode = availableModeOrFallback(persistedMode);
        int nativeMode = currentInstagramThemeMode();
        int systemNightMask = getSystemUiMode();
        boolean instagramDark = MaterialYouState.isEffectiveDark(
                nativeMode,
                systemNightMask
        );
        boolean resourcesApplied =
                MaterialYouThemeAPI31.setResourcesMode(activity, mode, instagramDark);
        // Compose caches must be refreshed independently of the resource overlay.
        applyComposePrismMode(activity, mode);
        if (resourcesApplied) {
            if (mode != persistedMode) {
                persistMode(mode);
            }
            ACTIVITY_GENERATIONS.recordCreated(activity);
        }
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

    public static boolean isMaterialYouEnabled() {
        return isMaterialYouAvailable()
                && MaterialYouState.hasMaterialYou(availableModeOrFallback(currentMode()));
    }

    public static boolean isAmoledEnabled() {
        return isAmoledAvailable()
                && MaterialYouState.hasAmoled(availableModeOrFallback(currentMode()));
    }

    public static Function1<Boolean, Unit> getMaterialYouToggleCallback() {
        return MATERIAL_YOU_TOGGLE_CALLBACK;
    }

    public static CompoundButton.OnCheckedChangeListener getLegacyMaterialYouToggleListener() {
        return LEGACY_MATERIAL_YOU_TOGGLE_LISTENER;
    }

    public static Function1<Integer, Unit> wrapNativeThemeCallback(
            Function1<Integer, Unit> callback
    ) {
        if (!isAmoledAvailable() || callback instanceof NativeThemeCallback) {
            return callback;
        }
        return new NativeThemeCallback(callback);
    }

    public static void observeComposeNativeThemeMode(int nativeMode) {
        int observedMode = MaterialYouState.sanitizeNativeThemeMode(nativeMode);
        if (currentInstagramThemeMode() != observedMode) {
            persistInstagramThemeMode(observedMode);
        }
    }

    public static Function0<Unit> getAmoledRadioCallback(
            Function1<Integer, Unit> callback
    ) {
        Function1<Integer, Unit> nativeCallback = unwrapNativeThemeCallback(callback);
        // Implemented as an explicit class (not a bare lambda) so D8/R8 cannot
        // fold this Function0 into another synthetic $$ExternalSyntheticLambda
        // class of matching erased shape (e.g. the Logger.printException
        // message supplier below). That collision is what produced the
        // AbstractMethodError on Function0.invoke() when tapping the AMOLED
        // radio item.
        return new AmoledRadioCallback(nativeCallback);
    }

    private static final class AmoledRadioCallback implements Function0<Unit> {
        private final Function1<Integer, Unit> nativeCallback;

        private AmoledRadioCallback(Function1<Integer, Unit> nativeCallback) {
            this.nativeCallback = nativeCallback;
        }

        @Override
        public Unit invoke() {
            ThemeMode targetMode = MaterialYouState.modeForAmoledToggle(
                    true,
                    currentModeForRequest()
            );
            requestNativeThemeChange(
                    Utils.getActivity(),
                    targetMode,
                    NATIVE_THEME_DARK,
                    () -> nativeCallback.invoke(NATIVE_THEME_DARK)
            );
            return Unit.INSTANCE;
        }
    }

    public static RadioGroup.OnCheckedChangeListener wrapLegacyNativeThemeListener(
            RadioGroup.OnCheckedChangeListener listener,
            int packedIds
    ) {
        if (!isAmoledAvailable()) {
            return listener;
        }
        return new LegacyNativeThemeController(
                listener,
                MaterialYouState.unpackLegacyRadioId(packedIds, 0),
                MaterialYouState.unpackLegacyRadioId(packedIds, 1),
                MaterialYouState.unpackLegacyRadioId(packedIds, 2),
                MaterialYouState.unpackLegacyRadioId(packedIds, 3)
        );
    }

    public static String getLegacySelectedRadioId(String nativeId, int packedIds) {
        String amoledId = Integer.toString(
                MaterialYouState.unpackLegacyRadioId(packedIds, 0)
        );
        Integer observedNativeMode =
                MaterialYouState.nativeModeForLegacySelectionId(nativeId, packedIds);
        if (observedNativeMode != null
                && currentInstagramThemeMode() != observedNativeMode) {
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
        return isAmoledEnabled() ? amoledId : nativeId;
    }

    public static String getLegacyRadioTitle(
            String itemId,
            String amoledId,
            String nativeTitle
    ) {
        return resolveLegacyRadioTitle(
                itemId,
                amoledId,
                nativeTitle,
                getAmoledTitle()
        );
    }

    public static String getAmoledTitle() {
        return "AMOLED";
    }

    public static String getMaterialYouTitle() {
        return "Material You";
    }

    public static String resolveLegacyRadioTitle(
            String itemId,
            String amoledId,
            String nativeTitle,
            String amoledTitle
    ) {
        if (itemId != null && itemId.equals(amoledId) && amoledTitle != null) {
            return amoledTitle;
        }
        return nativeTitle;
    }

    public static boolean shouldSelectNativeDark(boolean nativeDarkSelected) {
        return MaterialYouState.shouldSelectNativeDark(
                nativeDarkSelected,
                isAmoledEnabled()
        );
    }

    public static boolean isAvailable() {
        return isMaterialYouAvailable();
    }

    public static boolean isEnabled() {
        return isMaterialYouEnabled();
    }

    public static Function1<Boolean, Unit> getToggleCallback() {
        return getMaterialYouToggleCallback();
    }

    public static CompoundButton.OnCheckedChangeListener getLegacyToggleListener() {
        return getLegacyMaterialYouToggleListener();
    }

    private static void requestMaterialYouChange(Activity activity, boolean enabled) {
        requestNativeThemeChange(
                activity,
                MaterialYouState.modeForMaterialYouToggle(
                        enabled,
                        currentModeForRequest()
                ),
                null,
                null
        );
    }

    private static void requestNativeThemeChange(
            Activity activity,
            ThemeMode mode,
            Integer nativeMode,
            Runnable nativeAction
    ) {
        if (Build.VERSION.SDK_INT < 31
                || activity == null
                || Looper.myLooper() != Looper.getMainLooper()) {
            return;
        }

        applyMode(
                activity,
                availableModeOrFallback(mode),
                nativeMode,
                nativeAction
        );
    }

    private static ThemeMode availableModeOrFallback(ThemeMode mode) {
        return MaterialYouState.availableModeOrFallback(
                mode,
                isMaterialYouAvailable(),
                isAmoledAvailable(),
                MaterialYouThemeAPI31.isReady(ThemeMode.AMOLED_MATERIAL_YOU)
        );
    }

    private static void applyMode(
            Activity activity,
            ThemeMode requestedMode,
            Integer nativeMode,
            Runnable nativeAction
    ) {
        ThemeMode previousMode = availableModeOrFallback(currentMode());
        int previousNativeMode = currentInstagramThemeMode();
        int requestedNativeMode = nativeMode == null
                ? previousNativeMode
                : MaterialYouState.sanitizeNativeThemeMode(nativeMode);
        int systemNightMask = getSystemUiMode();
        boolean previousInstagramDark = MaterialYouState.isEffectiveDark(
                previousNativeMode,
                systemNightMask
        );
        boolean requestedInstagramDark = MaterialYouState.isEffectiveDark(
                requestedNativeMode,
                systemNightMask
        );
        int currentNightMask = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        int targetNightMask = MaterialYouState.targetNightMask(
                requestedNativeMode,
                systemNightMask
        );
        boolean overlayChanged = previousMode != requestedMode
                || previousInstagramDark != requestedInstagramDark;
        boolean pikoRecreate = MaterialYouState.shouldPikoRecreate(
                previousMode,
                requestedMode,
                nativeAction != null,
                currentNightMask,
                targetNightMask
        );
        if (!MaterialYouThemeAPI31.setResourcesMode(
                activity,
                requestedMode,
                requestedInstagramDark
        )) {
            MaterialYouThemeAPI31.setResourcesMode(
                    activity,
                    previousMode,
                    previousInstagramDark
            );
            persistMode(previousMode);
            return;
        }

        if (previousMode != requestedMode && !persistMode(requestedMode)) {
            MaterialYouThemeAPI31.setResourcesMode(
                    activity,
                    previousMode,
                    previousInstagramDark
            );
            persistMode(previousMode);
            return;
        }

        if (MaterialYouState.shouldPersistNativeThemeBeforeAction(
                nativeMode != null,
                nativeAction != null
        ) && !persistInstagramThemeMode(requestedNativeMode)) {
            MaterialYouThemeAPI31.setResourcesMode(
                    activity,
                    previousMode,
                    previousInstagramDark
            );
            persistMode(previousMode);
            persistInstagramThemeMode(previousNativeMode);
            return;
        }

        applyComposePrismMode(activity, requestedMode);
        runThemeTransition(
                activity,
                nativeAction,
                pikoRecreate,
                overlayChanged
        );
        if (MaterialYouState.shouldPersistNativeThemeAfterAction(
                nativeMode != null,
                nativeAction != null
        )) {
            persistInstagramThemeMode(requestedNativeMode);
        }
    }

    private static void applyComposePrismMode(Context context, ThemeMode mode) {
        int materialYouBackgroundArgb = 0;
        if (MaterialYouState.hasMaterialYou(mode)) {
            int resourceId = ResourceUtils.getIdentifier(
                    context,
                    ResourceType.COLOR,
                    "igds_primary_background"
            );
            if (resourceId != 0) {
                try {
                    materialYouBackgroundArgb = context.getColor(resourceId);
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
        lastComposeSurfaceOverrideArgb =
                MaterialYouState.composeSurfaceOverrideArgb(
                        mode,
                        materialYouBackgroundArgb
                );
    }

    private static native void applyComposePrismColor(
            int overrideArgb,
            boolean refreshPalettes
    );

    public static long resolveComposeSearchRowBackground(long nativePackedColor) {
        return resolveComposeSurfaceBackground(nativePackedColor);
    }

    public static long resolveComposeSurfaceBackground(long nativePackedColor) {
        return MaterialYouState.composeSearchRowBackground(
                nativePackedColor,
                lastComposeSurfaceOverrideArgb
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

    @SuppressWarnings("unchecked")
    private static Function1<Integer, Unit> unwrapNativeThemeCallback(
            Function1<Integer, Unit> callback
    ) {
        if (callback instanceof NativeThemeCallback) {
            return ((NativeThemeCallback) callback).delegate;
        }
        return callback;
    }

    private static ThemeMode currentMode() {
        return MaterialYouState.resolveMode(
                Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.MATERIAL_YOU_THEME)),
                Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.AMOLED_THEME))
        );
    }

    private static ThemeMode currentModeForRequest() {
        return availableModeOrFallback(currentMode());
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

    private static boolean persistInstagramThemeMode(int nativeMode) {
        int sanitizedMode = MaterialYouState.sanitizeNativeThemeMode(nativeMode);
        return Boolean.TRUE.equals(
                SharedPref.setStringPref(
                        Settings.INSTAGRAM_THEME_MODE.key,
                        Integer.toString(sanitizedMode)
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
        private final int amoledId;
        private final int lightId;
        private final int darkId;
        private final int systemId;

        private LegacyNativeThemeController(
                RadioGroup.OnCheckedChangeListener delegate,
                int amoledId,
                int lightId,
                int darkId,
                int systemId
        ) {
            this.delegate = delegate;
            this.amoledId = amoledId;
            this.lightId = lightId;
            this.darkId = darkId;
            this.systemId = systemId;
        }

        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            ThemeMode currentMode = currentModeForRequest();
            boolean selectAmoled = checkedId == amoledId;
            int nativeId = selectAmoled ? darkId : checkedId;
            Integer nativeMode = selectAmoled
                    ? NATIVE_THEME_DARK
                    : MaterialYouState.nativeModeForLegacySelection(
                            checkedId,
                            lightId,
                            darkId,
                            systemId
                    );
            if (nativeMode == null) {
                delegate.onCheckedChanged(group, checkedId);
                return;
            }
            ThemeMode targetMode = selectAmoled
                    ? MaterialYouState.modeForAmoledToggle(true, currentMode)
                    : MaterialYouState.modeForNativeThemeSelection(currentMode);
            requestNativeThemeChange(
                    getActivity(group.getContext()),
                    targetMode,
                    nativeMode,
                    () -> delegate.onCheckedChanged(group, nativeId)
            );
        }
    }

}
