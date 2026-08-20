package app.morphe.extension.newx.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.TypedValue;

import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.newx.theme.TwitterTheme;

/**
 * Material design system color tokens and metrics for NewX UI components.
 */
public final class Theme {
    private static final String DYNAMIC_COLOR_SETTING = "newx.theme.dynamic_color";
    private static final String AMOLED_BLACK_SETTING = "newx.theme.amoled_black";
    // Keep elevated surfaces visible against the AMOLED base surface.
    private static final int AMOLED_ELEVATED_SURFACE = Color.rgb(19, 24, 29);

    private Theme() {
    }

    public static boolean isDark(Context context) {
        return TwitterTheme.fromContext(context).isDark();
    }

    public static int dpToPx(Context context, float dp) {
        if (context == null) return Math.round(dp);
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        ));
    }

    public static int spToPx(Context context, float sp) {
        if (context == null) return Math.round(sp);
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                context.getResources().getDisplayMetrics()
        ));
    }

    public static SettingsSnapshot snapshot() {
        boolean dynamicColors =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        && SettingsRegistry.getBooleanOrDefault(DYNAMIC_COLOR_SETTING, false);
        boolean amoledBlack = dynamicColors
                && SettingsRegistry.getBooleanOrDefault(AMOLED_BLACK_SETTING, false);
        return new SettingsSnapshot(dynamicColors, amoledBlack);
    }

    public static int surface(Context context) {
        int fallback = isDark(context) ? Color.rgb(20, 18, 24) : Color.rgb(254, 247, 255);
        return dynamicColor(context, "surface", fallback);
    }

    public static int surfaceContainer(Context context) {
        int fallback = isDark(context) ? Color.BLACK : Color.WHITE;
        if (useAmoledBlack(context)) return Color.BLACK;
        return dynamicColor(context, "surface", fallback);
    }

    public static int surfaceContainerHigh(Context context) {
        int fallback = isDark(context) ? Color.rgb(40, 42, 48) : Color.rgb(243, 237, 247);
        if (useAmoledBlack(context)) fallback = AMOLED_ELEVATED_SURFACE;
        return dynamicColor(context, "surface_container_high", fallback);
    }

    public static int surfaceVariant(Context context) {
        int fallback = isDark(context) ? Color.rgb(54, 56, 64) : Color.rgb(231, 224, 236);
        return dynamicColor(context, "surface_container_high", fallback);
    }

    public static int primaryText(Context context) {
        int fallback = isDark(context) ? Color.rgb(217, 217, 217) : Color.rgb(15, 20, 25);
        return dynamicColor(context, "on_surface", fallback);
    }

    public static int secondaryText(Context context) {
        int fallback = isDark(context) ? Color.rgb(124, 131, 138) : Color.rgb(83, 100, 113);
        return dynamicColor(context, "on_surface_variant", fallback);
    }

    public static int primaryAccent(Context context) {
        return dynamicColor(context, "primary", Color.rgb(29, 155, 240));
    }

    public static int onPrimaryAccent(Context context) {
        return dynamicColor(context, "on_primary", Color.WHITE);
    }

    public static int primaryContainer(Context context) {
        int fallback = isDark(context) ? Color.rgb(26, 75, 110) : Color.rgb(218, 238, 255);
        return dynamicColor(context, "primary_container", fallback);
    }

    public static int onPrimaryContainer(Context context) {
        int fallback = isDark(context) ? Color.rgb(205, 232, 255) : Color.rgb(0, 45, 80);
        return dynamicColor(context, "on_primary_container", fallback);
    }

    public static int dividerColor(Context context) {
        int fallback = isDark(context) ? Color.argb(38, 255, 255, 255) : Color.argb(31, 0, 0, 0);
        return dynamicColor(context, "outline_variant", fallback);
    }

    public static int rippleColor(Context context) {
        int alpha = isDark(context) ? 40 : 32;
        return withAlpha(primaryText(context), alpha);
    }

    private static int dynamicColor(Context context, String role, int fallback) {
        if (!usesDynamicColors()) return fallback;
        if (context == null) return fallback;

        String brightness = isDark(context) ? "dark" : "light";
        String resourceName = "m3_sys_color_dynamic_" + brightness + "_" + role;
        int resourceId = context.getResources().getIdentifier(
                resourceName,
                "color",
                context.getPackageName()
        );
        if (resourceId == 0) return fallback;
        return context.getResources().getColor(resourceId, context.getTheme());
    }

    private static boolean usesDynamicColors() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && SettingsRegistry.getBooleanOrDefault(DYNAMIC_COLOR_SETTING, false);
    }

    private static boolean useAmoledBlack(Context context) {
        return usesDynamicColors()
                && isDark(context)
                && SettingsRegistry.getBooleanOrDefault(AMOLED_BLACK_SETTING, false);
    }

    public static final class SettingsSnapshot {
        private final boolean dynamicColors;
        private final boolean amoledBlack;

        private SettingsSnapshot(boolean dynamicColors, boolean amoledBlack) {
            this.dynamicColors = dynamicColors;
            this.amoledBlack = amoledBlack;
        }

        public int surface(Context context) {
            int fallback = Theme.isDark(context)
                    ? Color.rgb(20, 18, 24)
                    : Color.rgb(254, 247, 255);
            return dynamicColor(context, "surface", fallback);
        }

        public int surfaceContainer(Context context) {
            int fallback = Theme.isDark(context) ? Color.BLACK : Color.WHITE;
            if (amoledBlack && Theme.isDark(context)) return Color.BLACK;
            return dynamicColor(context, "surface", fallback);
        }

        public int surfaceContainerHigh(Context context) {
            int fallback = Theme.isDark(context)
                    ? Color.rgb(40, 42, 48)
                    : Color.rgb(243, 237, 247);
            if (amoledBlack && Theme.isDark(context)) fallback = AMOLED_ELEVATED_SURFACE;
            return dynamicColor(context, "surface_container_high", fallback);
        }

        public int surfaceVariant(Context context) {
            int fallback = Theme.isDark(context)
                    ? Color.rgb(54, 56, 64)
                    : Color.rgb(231, 224, 236);
            return dynamicColor(context, "surface_container_high", fallback);
        }

        public int primaryText(Context context) {
            int fallback = Theme.isDark(context)
                    ? Color.rgb(217, 217, 217)
                    : Color.rgb(15, 20, 25);
            return dynamicColor(context, "on_surface", fallback);
        }

        public int secondaryText(Context context) {
            int fallback = Theme.isDark(context)
                    ? Color.rgb(124, 131, 138)
                    : Color.rgb(83, 100, 113);
            return dynamicColor(context, "on_surface_variant", fallback);
        }

        public int primaryAccent(Context context) {
            return dynamicColor(context, "primary", Color.rgb(29, 155, 240));
        }

        public int onPrimaryAccent(Context context) {
            return dynamicColor(context, "on_primary", Color.WHITE);
        }

        public int primaryContainer(Context context) {
            int fallback = Theme.isDark(context)
                    ? Color.rgb(26, 75, 110)
                    : Color.rgb(218, 238, 255);
            return dynamicColor(context, "primary_container", fallback);
        }

        public int onPrimaryContainer(Context context) {
            int fallback = Theme.isDark(context)
                    ? Color.rgb(205, 232, 255)
                    : Color.rgb(0, 45, 80);
            return dynamicColor(context, "on_primary_container", fallback);
        }

        public int dividerColor(Context context) {
            int fallback = Theme.isDark(context)
                    ? Color.argb(38, 255, 255, 255)
                    : Color.argb(31, 0, 0, 0);
            return dynamicColor(context, "outline_variant", fallback);
        }

        public int rippleColor(Context context) {
            int alpha = Theme.isDark(context) ? 40 : 32;
            return Theme.withAlpha(primaryText(context), alpha);
        }

        private int dynamicColor(Context context, String role, int fallback) {
            if (!dynamicColors || context == null) return fallback;

            String brightness = Theme.isDark(context) ? "dark" : "light";
            String resourceName = "m3_sys_color_dynamic_" + brightness + "_" + role;
            int resourceId = context.getResources().getIdentifier(
                    resourceName,
                    "color",
                    context.getPackageName()
            );
            if (resourceId == 0) return fallback;
            return context.getResources().getColor(resourceId, context.getTheme());
        }
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int blend(int color1, int color2, float ratio) {
        float inverseRatio = 1f - ratio;
        float r = Color.red(color1) * inverseRatio + Color.red(color2) * ratio;
        float g = Color.green(color1) * inverseRatio + Color.green(color2) * ratio;
        float b = Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio;
        float a = Color.alpha(color1) * inverseRatio + Color.alpha(color2) * ratio;
        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }
}
