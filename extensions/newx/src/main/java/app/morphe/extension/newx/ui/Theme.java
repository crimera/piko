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
    private static final String THEME_SETTING = "newx.theme.dark_style";
    private static final String THEME_MATERIAL = "material";
    private static final String THEME_CONTRAST = "contrast";
    private static final String THEME_DIM = "dim";
    // Keep elevated surfaces visible against the darkest base surface. Mirrors the host's
    // LIGHTS_OUT highlight background so extension dialogs match the app's own popups. Kept as a
    // literal so the class has no Android calls during static initialization and stays
    // unit-testable.
    private static final int DARK_ELEVATED_SURFACE = 0xFF121314;
    // Mirrors the host's LIGHTS_OUT container surface (#15181c) for chips and badges.
    private static final int DARK_SURFACE_VARIANT = 0xFF15181C;
    // High-contrast light elevation: light gray panels stay visible against the white base.
    private static final int LIGHT_ELEVATED_SURFACE = 0xFFEEEEEE;
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
        String theme = themeStyle();
        boolean dynamicColors =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !THEME_DIM.equals(theme);
        return new SettingsSnapshot(dynamicColors, theme);
    }

    static String themeStyle() {
        return resolveThemeStyle(
                SettingsRegistry.isRegistered(THEME_SETTING),
                SettingsRegistry.getStringOrDefault(THEME_SETTING, THEME_CONTRAST)
        );
    }

    /**
     * Maps the stored chooser value to a known theme. Unregistered (patch absent), legacy
     * AMOLED, and unknown values resolve to high contrast, which keeps the previous pure-black
     * dark surfaces.
     */
    static String resolveThemeStyle(boolean settingRegistered, String settingValue) {
        if (!settingRegistered) return THEME_CONTRAST;
        if (THEME_MATERIAL.equals(settingValue)) return THEME_MATERIAL;
        if (THEME_DIM.equals(settingValue)) return THEME_DIM;
        return THEME_CONTRAST;
    }

    public static int surface(Context context) {
        return surfaceColor(context, usesDynamicColors(), themeStyle());
    }

    public static int surfaceContainer(Context context) {
        return surfaceContainerColor(context, usesDynamicColors(), themeStyle());
    }

    public static int surfaceContainerHigh(Context context) {
        return surfaceContainerHighColor(context, usesDynamicColors(), themeStyle());
    }

    public static int surfaceVariant(Context context) {
        return surfaceVariantColor(context, usesDynamicColors(), themeStyle());
    }

    private static int surfaceColor(Context context, boolean dynamicColors, String theme) {
        if (isDark(context)) {
            if (THEME_DIM.equals(theme)) return DIM_SURFACE;
            if (THEME_MATERIAL.equals(theme) && dynamicColors) {
                return dynamicColor(context, "surface", Color.BLACK, true);
            }
            return Color.BLACK;
        }
        if (THEME_MATERIAL.equals(theme)) {
            return dynamicColor(context, "surface", Color.rgb(254, 247, 255), dynamicColors);
        }
        // High contrast and the classic Dim theme both use a neutral white light base.
        return Color.WHITE;
    }

    private static int surfaceContainerColor(
            Context context,
            boolean dynamicColors,
            String theme
    ) {
        // The chooser owns the dark surface family, so dynamic colors only tint the accents here.
        return surfaceColor(context, dynamicColors, theme);
    }

    private static int surfaceContainerHighColor(
            Context context,
            boolean dynamicColors,
            String theme
    ) {
        if (isDark(context)) {
            if (THEME_DIM.equals(theme)) return DIM_SURFACE_CONTAINER_HIGH;
            if (THEME_MATERIAL.equals(theme) && dynamicColors) {
                return dynamicColor(context, "surface_container_high", DARK_ELEVATED_SURFACE, true);
            }
            return DARK_ELEVATED_SURFACE;
        }
        if (THEME_MATERIAL.equals(theme)) {
            return dynamicColor(
                    context,
                    "surface_container_high",
                    Color.rgb(243, 237, 247),
                    dynamicColors
            );
        }
        return LIGHT_ELEVATED_SURFACE;
    }

    private static int surfaceVariantColor(
            Context context,
            boolean dynamicColors,
            String theme
    ) {
        if (isDark(context)) {
            if (THEME_DIM.equals(theme)) return DIM_SURFACE_VARIANT;
            if (THEME_MATERIAL.equals(theme) && dynamicColors) {
                return dynamicColor(context, "surface_container", DARK_SURFACE_VARIANT, true);
            }
            return DARK_SURFACE_VARIANT;
        }
        if (THEME_MATERIAL.equals(theme)) {
            return dynamicColor(
                    context,
                    "surface_container_high",
                    Color.rgb(231, 224, 236),
                    dynamicColors
            );
        }
        return LIGHT_ELEVATED_SURFACE;
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
                && !THEME_DIM.equals(themeStyle());
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
        private final String theme;

        private SettingsSnapshot(boolean dynamicColors, String theme) {
            this.dynamicColors = dynamicColors;
            this.theme = theme;
        }

        public boolean usesDynamicColors() {
            return dynamicColors;
        }

        public int checkboxChecked(Context context) {
            return Theme.checkboxChecked(context, dynamicColors);
        }

        public int surface(Context context) {
            return Theme.surfaceColor(context, dynamicColors, theme);
        }

        public int surfaceContainer(Context context) {
            return Theme.surfaceContainerColor(context, dynamicColors, theme);
        }

        public int surfaceContainerHigh(Context context) {
            return Theme.surfaceContainerHighColor(context, dynamicColors, theme);
        }

        public int surfaceVariant(Context context) {
            return Theme.surfaceVariantColor(context, dynamicColors, theme);
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
