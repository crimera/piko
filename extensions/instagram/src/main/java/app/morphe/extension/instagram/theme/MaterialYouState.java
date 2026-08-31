/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.instagram.theme;

import android.app.UiModeManager;
import android.content.res.Configuration;

final class MaterialYouState {
    private MaterialYouState() {
    }

    static ThemeMode resolveMode(boolean materialYouEnabled, boolean amoledEnabled) {
        if (materialYouEnabled && amoledEnabled) {
            return ThemeMode.AMOLED_MATERIAL_YOU;
        }
        if (materialYouEnabled) {
            return ThemeMode.MATERIAL_YOU;
        }
        return amoledEnabled ? ThemeMode.AMOLED : ThemeMode.BASE;
    }

    static boolean hasMaterialYou(ThemeMode mode) {
        return mode == ThemeMode.MATERIAL_YOU
                || mode == ThemeMode.AMOLED_MATERIAL_YOU;
    }

    static boolean hasAmoled(ThemeMode mode) {
        return mode == ThemeMode.AMOLED
                || mode == ThemeMode.AMOLED_MATERIAL_YOU;
    }

    static int composePrismOverrideArgb(
            ThemeMode mode,
            int materialYouBackgroundArgb
    ) {
        return composeSurfaceOverrideArgb(mode, materialYouBackgroundArgb);
    }

    static int composeSurfaceOverrideArgb(
            ThemeMode mode,
            int materialYouBackgroundArgb
    ) {
        if (hasAmoled(mode)) {
            return 0xff000000;
        }
        return hasMaterialYou(mode) ? materialYouBackgroundArgb : 0;
    }

    static int composeSearchRowOverrideArgb(
            ThemeMode mode,
            int materialYouBackgroundArgb
    ) {
        if (mode == ThemeMode.AMOLED_MATERIAL_YOU) {
            return 0xff000000;
        }
        return mode == ThemeMode.MATERIAL_YOU ? materialYouBackgroundArgb : 0;
    }

    static long composeSearchRowBackground(
            long nativePackedColor,
            int overrideArgb
    ) {
        if (overrideArgb == 0 || overrideArgb == Integer.MIN_VALUE) {
            return nativePackedColor;
        }
        return ((long) overrideArgb) << 32;
    }

    static ThemeMode availableRequestedModeOrFallback(
            ThemeMode requestedMode,
            boolean effectiveDark,
            boolean materialYouAvailable,
            boolean amoledAvailable,
            boolean combinedAvailable
    ) {
        boolean materialYou = hasMaterialYou(requestedMode) && materialYouAvailable;
        boolean amoled = hasAmoled(requestedMode) && amoledAvailable;
        if (effectiveDark && materialYou && amoled && !combinedAvailable) {
            materialYou = false;
        }
        return resolveMode(materialYou, amoled);
    }

    static ThemeMode modeForMaterialYouToggle(boolean enabled, ThemeMode currentMode) {
        return resolveMode(enabled, hasAmoled(currentMode));
    }

    static int targetNightMask(int nativeMode, int liveSystemNightMask) {
        switch (nativeMode) {
            case 1:
                return Configuration.UI_MODE_NIGHT_NO;
            case 2:
                return Configuration.UI_MODE_NIGHT_YES;
            case -1:
                return liveSystemNightMask & Configuration.UI_MODE_NIGHT_MASK;
            default:
                throw new IllegalArgumentException("Unsupported native theme mode: " + nativeMode);
        }
    }

    static boolean isEffectiveDark(int nativeMode, int liveSystemNightMask) {
        return targetNightMask(nativeMode, liveSystemNightMask)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static boolean isActivityUiModeDark(int activityUiMode) {
        return (activityUiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static Boolean updateObservedInstagramDark(
            Boolean observedInstagramDark,
            boolean pikoSettingsActivity,
            boolean activityDark
    ) {
        return pikoSettingsActivity
                ? observedInstagramDark
                : activityDark;
    }

    static Boolean updateObservedInstagramDarkForNativeMode(
            Boolean observedInstagramDark,
            Integer nativeMode,
            boolean requestedInstagramDark
    ) {
        return nativeMode == null
                ? observedInstagramDark
                : requestedInstagramDark;
    }

    static boolean resolveInstagramDark(
            Boolean observedInstagramDark,
            int nativeMode,
            boolean nativeModeSynchronized,
            int systemNightMask
    ) {
        int sanitizedNativeMode = sanitizeNativeThemeMode(nativeMode);
        if (sanitizedNativeMode == 1 || sanitizedNativeMode == 2) {
            return isEffectiveDark(sanitizedNativeMode, systemNightMask);
        }
        if (nativeModeSynchronized) {
            return isEffectiveDark(sanitizedNativeMode, systemNightMask);
        }
        return observedInstagramDark != null
                ? observedInstagramDark
                : isEffectiveDark(sanitizedNativeMode, systemNightMask);
    }

    static int sanitizeNativeThemeMode(int nativeMode) {
        return nativeMode == -1 || nativeMode == 1 || nativeMode == 2
                ? nativeMode
                : -1;
    }

    static boolean shouldPersistObservedNativeMode(
            int currentNativeMode,
            int observedNativeMode,
            boolean nativeModeSynchronized
    ) {
        return currentNativeMode != observedNativeMode || !nativeModeSynchronized;
    }

    static boolean shouldPersistNativeThemeBeforeAction(
            boolean hasNativeMode,
            boolean hasNativeAction
    ) {
        return hasNativeMode && !hasNativeAction;
    }

    static boolean shouldPersistNativeThemeAfterAction(
            boolean hasNativeMode,
            boolean hasNativeAction
    ) {
        return hasNativeMode && hasNativeAction;
    }

    static int resolveSystemNightMask(int systemNightMode, int resourceNightMask) {
        switch (systemNightMode) {
            case UiModeManager.MODE_NIGHT_NO:
                return Configuration.UI_MODE_NIGHT_NO;
            case UiModeManager.MODE_NIGHT_YES:
                return Configuration.UI_MODE_NIGHT_YES;
            default:
                return resourceNightMask & Configuration.UI_MODE_NIGHT_MASK;
        }
    }

    static int resolveCachedSystemNightMask(
            int sdkInt,
            int cachedNightMask,
            int liveSystemNightMask
    ) {
        if (sdkInt < 31) {
            return cachedNightMask;
        }

        int normalizedLiveMask =
                liveSystemNightMask & Configuration.UI_MODE_NIGHT_MASK;
        if (normalizedLiveMask == Configuration.UI_MODE_NIGHT_NO
                || normalizedLiveMask == Configuration.UI_MODE_NIGHT_YES) {
            return normalizedLiveMask;
        }
        return cachedNightMask;
    }

    static boolean shouldPikoRecreate(
            ThemeMode previousMode,
            ThemeMode requestedMode,
            boolean hasNativeAction,
            int currentNightMask,
            int targetNightMask
    ) {
        boolean overlayChanged = previousMode != requestedMode;
        boolean nativeConfigurationWillChange =
                hasNativeAction && currentNightMask != targetNightMask;
        return overlayChanged && !nativeConfigurationWillChange;
    }

    static Integer nativeModeForLegacySelection(
            int checkedId,
            int lightId,
            int darkId,
            int systemId
    ) {
        if (checkedId == lightId) {
            return 1;
        }
        if (checkedId == darkId) {
            return 2;
        }
        if (checkedId == systemId) {
            return -1;
        }
        return null;
    }

    static Integer nativeModeForLegacySelectionId(
            String selectedId,
            int packedIds
    ) {
        if (selectedId == null) {
            return null;
        }

        final int parsedId;
        try {
            parsedId = Integer.parseInt(selectedId);
        } catch (NumberFormatException ignored) {
            return null;
        }

        return nativeModeForLegacySelection(
                parsedId,
                unpackLegacyRadioId(packedIds, 0),
                unpackLegacyRadioId(packedIds, 1),
                unpackLegacyRadioId(packedIds, 2)
        );
    }

    static int unpackLegacyRadioId(int packedIds, int byteIndex) {
        if (byteIndex < 0 || byteIndex > 2) {
            throw new IllegalArgumentException("Legacy theme radio byte index must be 0..2");
        }
        return (packedIds >>> (byteIndex * 8)) & 0xff;
    }

    static ThemeMode modeForAmoledToggle(boolean enabled, ThemeMode currentMode) {
        return resolveMode(hasMaterialYou(currentMode), enabled);
    }

    static ThemeMode modeForEffectiveTheme(ThemeMode currentMode, boolean effectiveDark) {
        return effectiveDark
                ? currentMode
                : modeForAmoledToggle(false, currentMode);
    }

    static ThemeMode modeForNativeThemeSelection(ThemeMode currentMode) {
        return currentMode;
    }

}
