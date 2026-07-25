/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;

import app.morphe.extension.twitter.patches.customise.appIcon.AppIconManager;
import app.morphe.extension.twitter.settings.Settings;

@SuppressWarnings("unused")
public final class DynamicColor {
    private static final String LIGHT_STYLE = "PikoDynamicPaletteLight";
    private static final String DIM_STYLE = "PikoDynamicPaletteDim";
    private static final String LIGHTS_OUT_STYLE = "PikoDynamicPaletteLightsOut";

    private DynamicColor() {
    }

    public static void updateLauncherIcon(boolean enabled) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }

        new AppIconManager(
                app.morphe.extension.shared.Utils.getContext()
        ).applyDynamicColorIcon(enabled);
    }

    public static Activity instantiateFrameworkActivity(
            ClassLoader classLoader,
            String className,
            Intent intent
    ) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return new AppComponentFactory().instantiateActivity(classLoader, className, intent);
    }

    public static void applyThemeStyle(Resources.Theme theme, int originalStyleId) {
        boolean enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && Utils.getBooleanPref(Settings.DYNAMIC_COLOR);

        if (!enabled) {
            return;
        }

        Resources resources = theme.getResources();
        String originalStyleName;
        String resourcePackageName;
        try {
            originalStyleName = resources.getResourceEntryName(originalStyleId);
            resourcePackageName = resources.getResourcePackageName(originalStyleId);
        } catch (Resources.NotFoundException exception) {
            return;
        }

        String styleName = resolveStyleName(
                true,
                Build.VERSION.SDK_INT,
                originalStyleName
        );
        if (styleName == null) {
            return;
        }

        int styleId = resources.getIdentifier(
                styleName,
                "style",
                resourcePackageName
        );
        if (styleId != 0) {
            theme.applyStyle(styleId, true);
        }
    }

    static String resolveStyleName(boolean enabled, int sdkInt, String originalStyleName) {
        if (!enabled || sdkInt < Build.VERSION_CODES.S || originalStyleName == null) {
            return null;
        }
        if (originalStyleName.endsWith("LightsOut")) {
            return LIGHTS_OUT_STYLE;
        }
        if (originalStyleName.endsWith("Dim")) {
            return DIM_STYLE;
        }
        if (originalStyleName.endsWith("Standard")) {
            return LIGHT_STYLE;
        }
        return null;
    }
}
