package app.morphe.extension.newx.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.TypedValue;

import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.newx.theme.TwitterTheme;

/**
 * Material design system color tokens and metrics for NewX UI components. Accent roles follow the
 * host's selected Twitter accent; dynamic accent colors remain limited to the patched Blue palette.
 */
public final class Theme {
    private static final String DYNAMIC_COLOR_SETTING = "newx.theme.dynamic_color";
    private static final String AMOLED_BLACK_SETTING = "newx.theme.amoled_black";
    // Keep elevated surfaces visible against the AMOLED base surface. Kept as a literal so the
    // class has no Android calls during static initialization and stays unit-testable.
    private static final int AMOLED_ELEVATED_SURFACE = 0xFF13181D;
    // Classic X "Dim" surfaces. NewX routes every dark mode to its LIGHTS_OUT palette, so the
    // extension-owned screens mirror the dim tokens the patch restores for the Compose palette.
    private static final int DIM_SURFACE = 0xFF15202B;
    private static final int DIM_SURFACE_CONTAINER_HIGH = 0xFF182430;
    private static final int DIM_SURFACE_VARIANT = 0xFF1E2732;

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
        boolean amoledBlack = resolveAmoledBlack(
                SettingsRegistry.isRegistered(AMOLED_BLACK_SETTING),
                SettingsRegistry.getBooleanOrDefault(AMOLED_BLACK_SETTING, false)
        );
        return new SettingsSnapshot(dynamicColors, amoledBlack);
    }

    public static int surface(Context context) {
        return surfaceColor(context, usesDynamicColors());
    }

    public static int surfaceContainer(Context context) {
        return surfaceContainerColor(context, usesDynamicColors(), useAmoledBlack(context));
    }

    public static int surfaceContainerHigh(Context context) {
        return surfaceContainerHighColor(context, usesDynamicColors(), useAmoledBlack(context));
    }

    public static int surfaceVariant(Context context) {
        return surfaceVariantColor(context, usesDynamicColors());
    }

    private static int surfaceColor(Context context, boolean dynamicColors) {
        int fallback = isDark(context) ? DIM_SURFACE : Color.rgb(254, 247, 255);
        return dynamicColor(context, "surface", fallback, dynamicColors);
    }

    private static int surfaceContainerColor(
            Context context,
            boolean dynamicColors,
            boolean amoledBlack
    ) {
        if (amoledBlack && isDark(context)) return Color.BLACK;
        int fallback = isDark(context) ? DIM_SURFACE : Color.WHITE;
        return dynamicColor(context, "surface", fallback, dynamicColors);
    }

    private static int surfaceContainerHighColor(
            Context context,
            boolean dynamicColors,
            boolean amoledBlack
    ) {
        int fallback = isDark(context)
                ? DIM_SURFACE_CONTAINER_HIGH
                : Color.rgb(243, 237, 247);
        if (amoledBlack && isDark(context)) fallback = AMOLED_ELEVATED_SURFACE;
        return dynamicColor(context, "surface_container_high", fallback, dynamicColors);
    }

    private static int surfaceVariantColor(Context context, boolean dynamicColors) {
        int fallback = isDark(context) ? DIM_SURFACE_VARIANT : Color.rgb(231, 224, 236);
        return dynamicColor(context, "surface_container_high", fallback, dynamicColors);
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
        return primaryAccent(context, usesDynamicColors());
    }

    public static int onPrimaryAccent(Context context) {
        return onPrimaryAccent(context, usesDynamicColors());
    }

    public static int primaryContainer(Context context) {
        return primaryContainer(context, usesDynamicColors());
    }

    public static int onPrimaryContainer(Context context) {
        return onPrimaryContainer(context, usesDynamicColors());
    }

    public static int dividerColor(Context context) {
        int fallback = isDark(context) ? Color.argb(38, 255, 255, 255) : Color.argb(31, 0, 0, 0);
        return dynamicColor(context, "outline_variant", fallback);
    }

    public static int rippleColor(Context context) {
        int alpha = isDark(context) ? 40 : 32;
        return withAlpha(primaryText(context), alpha);
    }

    public static int checkboxChecked(Context context) {
        return checkboxChecked(context, usesDynamicColors());
    }

    private static int dynamicColor(Context context, String role, int fallback) {
        return dynamicColor(context, role, fallback, usesDynamicColors());
    }

    private static int dynamicColor(
            Context context,
            String role,
            int fallback,
            boolean enabled
    ) {
        if (!enabled) return fallback;
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

    private static boolean usesDynamicAccent(
            boolean dynamicColors,
            TwitterTheme.Accent accent
    ) {
        return dynamicColors && accent == TwitterTheme.Accent.BLUE;
    }

    private static int primaryAccent(Context context, boolean dynamicColors) {
        TwitterTheme.Accent accent = TwitterTheme.accent(context);
        if (usesDynamicAccent(dynamicColors, accent)) {
            return dynamicColor(context, "primary", accent.primaryColor(), dynamicColors);
        }
        return accent.primaryColor();
    }

    private static int onPrimaryAccent(Context context, boolean dynamicColors) {
        TwitterTheme.Accent accent = TwitterTheme.accent(context);
        if (usesDynamicAccent(dynamicColors, accent)) {
            return dynamicColor(context, "on_primary", accent.onPrimaryColor(), dynamicColors);
        }
        return accent.onPrimaryColor();
    }

    private static int primaryContainer(Context context, boolean dynamicColors) {
        TwitterTheme.Accent accent = TwitterTheme.accent(context);
        int fallback = primaryContainerFallback(context, accent);
        if (usesDynamicAccent(dynamicColors, accent)) {
            return dynamicColor(context, "primary_container", fallback, dynamicColors);
        }
        return fallback;
    }

    private static int onPrimaryContainer(Context context, boolean dynamicColors) {
        TwitterTheme.Accent accent = TwitterTheme.accent(context);
        int fallback = onPrimaryContainerFallback(context, accent);
        if (usesDynamicAccent(dynamicColors, accent)) {
            return dynamicColor(context, "on_primary_container", fallback, dynamicColors);
        }
        return fallback;
    }

    private static int primaryContainerFallback(
            Context context,
            TwitterTheme.Accent accent
    ) {
        if (accent == TwitterTheme.Accent.BLUE) {
            return isDark(context) ? Color.rgb(26, 75, 110) : Color.rgb(218, 238, 255);
        }

        int primary = accent.primaryColor();
        return isDark(context)
                ? blend(Color.BLACK, primary, 0.35f)
                : blend(Color.WHITE, primary, 0.16f);
    }

    private static int onPrimaryContainerFallback(
            Context context,
            TwitterTheme.Accent accent
    ) {
        if (accent == TwitterTheme.Accent.BLUE) {
            return isDark(context) ? Color.rgb(205, 232, 255) : Color.rgb(0, 45, 80);
        }
        return contrastingText(primaryContainerFallback(context, accent));
    }

    private static int contrastingText(int color) {
        int luminance = 299 * Color.red(color)
                + 587 * Color.green(color)
                + 114 * Color.blue(color);
        return luminance > 127500 ? Color.BLACK : Color.WHITE;
    }

    public static boolean usesDynamicColors() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && SettingsRegistry.getBooleanOrDefault(DYNAMIC_COLOR_SETTING, false);
    }

    private static boolean useAmoledBlack(Context context) {
        return isDark(context) && resolveAmoledBlack(
                SettingsRegistry.isRegistered(AMOLED_BLACK_SETTING),
                SettingsRegistry.getBooleanOrDefault(AMOLED_BLACK_SETTING, false)
        );
    }

    /**
     * Whether dark surfaces should resolve to pure black. The AMOLED toggle is contributed by the
     * NewX dynamic color patch, which also injects the dim palette into the host. When that patch
     * is absent the toggle is unregistered and X renders its native LIGHTS_OUT near-black palette
     * for every dark mode, so extension-owned surfaces must stay black instead of falling back to
     * the dim tokens used to mirror the patched host palette.
     */
    static boolean resolveAmoledBlack(boolean settingRegistered, boolean settingValue) {
        return !settingRegistered || settingValue;
    }

    private static int checkboxChecked(Context context, boolean dynamicColors) {
        TwitterTheme.Accent accent = TwitterTheme.accent(context);
        if (usesDynamicAccent(dynamicColors, accent)) {
            int base = isDark(context) ? Color.WHITE : Color.rgb(15, 20, 25);
            return blend(base, primaryAccent(context, dynamicColors), 0.35f);
        }
        if (accent == TwitterTheme.Accent.BLUE) {
            return isDark(context) ? Color.WHITE : Color.rgb(15, 20, 25);
        }
        return accent.primaryColor();
    }

    public static final class SettingsSnapshot {
        private final boolean dynamicColors;
        private final boolean amoledBlack;

        private SettingsSnapshot(boolean dynamicColors, boolean amoledBlack) {
            this.dynamicColors = dynamicColors;
            this.amoledBlack = amoledBlack;
        }

        public boolean usesDynamicColors() {
            return dynamicColors;
        }

        public int checkboxChecked(Context context) {
            return Theme.checkboxChecked(context, dynamicColors);
        }

        public int surface(Context context) {
            return Theme.surfaceColor(context, dynamicColors);
        }

        public int surfaceContainer(Context context) {
            return Theme.surfaceContainerColor(context, dynamicColors, amoledBlack);
        }

        public int surfaceContainerHigh(Context context) {
            return Theme.surfaceContainerHighColor(context, dynamicColors, amoledBlack);
        }

        public int surfaceVariant(Context context) {
            return Theme.surfaceVariantColor(context, dynamicColors);
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
            return Theme.primaryAccent(context, dynamicColors);
        }

        public int onPrimaryAccent(Context context) {
            return Theme.onPrimaryAccent(context, dynamicColors);
        }

        public int primaryContainer(Context context) {
            return Theme.primaryContainer(context, dynamicColors);
        }

        public int onPrimaryContainer(Context context) {
            return Theme.onPrimaryContainer(context, dynamicColors);
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
