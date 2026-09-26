package app.morphe.extension.newx.theme;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.newx.settings.SettingsRegistry;

/** Builds packed Compose sRGB colors from the host application's Material You resources. */
public final class DynamicColorPalette {
    public static final int PRIMARY = 0;
    public static final int PRIMARY_TEXT = 1;
    public static final int SECONDARY_TEXT = 2;
    public static final int TERTIARY = 3;
    public static final int ON_PRIMARY = 4;
    public static final int LINK = 5;
    public static final int DIVIDER = 6;
    public static final int CELL_BACKGROUND = 7;
    public static final int CELL_BACKGROUND_TRANSLUCENT = 8;
    public static final int HIGHLIGHT_BACKGROUND = 9;
    public static final int UNREAD = 10;
    public static final int TOMBSTONE = 11;
    public static final int GLASS_BORDER = 12;
    public static final int GLASS_BACKGROUND = 13;
    public static final int GLASS_SHADOW = 14;
    public static final int APP_BACKGROUND = 15;
    public static final int BORDER = 16;

    private static final String DYNAMIC_COLOR_SETTING = "newx.theme.dynamic_color";
    private static final String DYNAMIC_LIKE_SETTING = "newx.theme.dynamic_like";
    private static final String DARK_STYLE_SETTING = "newx.theme.dark_style";
    private static final String DARK_STYLE_AMOLED = "amoled";
    private static final String DARK_STYLE_DIM = "dim";
    private static final String LIGHT_PRIMARY = "m3_sys_color_dynamic_light_primary";
    private static final String LIGHT_ON_PRIMARY = "m3_sys_color_dynamic_light_on_primary";
    private static final String LIGHT_PRIMARY_CONTAINER = "m3_sys_color_dynamic_light_primary_container";
    private static final String LIGHT_SURFACE = "m3_sys_color_dynamic_light_surface";
    private static final String LIGHT_SURFACE_CONTAINER_LOW = "m3_sys_color_dynamic_light_surface_container_low";
    private static final String LIGHT_SURFACE_CONTAINER_HIGH = "m3_sys_color_dynamic_light_surface_container_high";
    private static final String LIGHT_ON_SURFACE = "m3_sys_color_dynamic_light_on_surface";
    private static final String LIGHT_ON_SURFACE_VARIANT = "m3_sys_color_dynamic_light_on_surface_variant";
    private static final String LIGHT_OUTLINE = "m3_sys_color_dynamic_light_outline";
    private static final String LIGHT_OUTLINE_VARIANT = "m3_sys_color_dynamic_light_outline_variant";
    private static final String DARK_PRIMARY = "m3_sys_color_dynamic_dark_primary";
    private static final String DARK_ON_PRIMARY = "m3_sys_color_dynamic_dark_on_primary";
    private static final String DARK_PRIMARY_CONTAINER = "m3_sys_color_dynamic_dark_primary_container";
    private static final String DARK_SURFACE = "m3_sys_color_dynamic_dark_surface";
    private static final String DARK_SURFACE_CONTAINER_LOW = "m3_sys_color_dynamic_dark_surface_container_low";
    private static final String DARK_SURFACE_CONTAINER_HIGH = "m3_sys_color_dynamic_dark_surface_container_high";
    private static final String DARK_ON_SURFACE = "m3_sys_color_dynamic_dark_on_surface";
    private static final String DARK_ON_SURFACE_VARIANT = "m3_sys_color_dynamic_dark_on_surface_variant";
    private static final String DARK_OUTLINE = "m3_sys_color_dynamic_dark_outline";
    private static final String DARK_OUTLINE_VARIANT = "m3_sys_color_dynamic_dark_outline_variant";
    private static final int ALPHA_STANDARD_DIM_TRANSLUCENT = 0xBF;
    private static final int ALPHA_LIGHTS_OUT_TRANSLUCENT = 0x80;
    private static final int ALPHA_GLASS_BACKGROUND = 0xCC;
    private static final int ALPHA_LIGHT_GLASS_SHADOW = 0x26;
    private static final int ALPHA_DARK_GLASS_SHADOW = 0x50;
    private static final int DIM_XDS_BACKGROUND = 0xFF15202B;
    private static final int BLACK_XDS_BACKGROUND = 0xFF000000;
    // The app's own LIGHTS_OUT elevated surfaces. The AMOLED style keeps them so popups, dialogs,
    // sheets, and glass panels stay visible against the pure-black base surface instead of
    // disappearing into it.
    private static final int AMOLED_HIGHLIGHT_BACKGROUND = 0xFF121314;
    private static final int AMOLED_GLASS_BACKGROUND = 0xCC242424;
    // Classic X Dim surfaces, mirrored from NewX's own DIM palette.
    private static final int DIM_CELL_BACKGROUND = 0xFF15202B;
    private static final int DIM_TRANSLUCENT_CELL_BACKGROUND = 0xBF15202B;
    private static final int DIM_HIGHLIGHT_BACKGROUND = 0xFF101922;
    private static final int DIM_GLASS_BACKGROUND = 0xCC15202B;

    private DynamicColorPalette() {
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static boolean isEnabled() {
        return isSupported() && SettingsRegistry.getBooleanOrDefault(DYNAMIC_COLOR_SETTING, false);
    }

    /**
     * Whether the dark style chooser selected the AMOLED style. Unknown or unset values resolve to
     * the AMOLED default so dark surfaces never silently fall back to the dim family.
     */
    public static boolean useAmoledBlack() {
        return DARK_STYLE_AMOLED.equals(darkStyle());
    }

    static String darkStyle() {
        String style = SettingsRegistry.getStringOrDefault(DARK_STYLE_SETTING, DARK_STYLE_AMOLED);
        return DARK_STYLE_DIM.equals(style) ? DARK_STYLE_DIM : DARK_STYLE_AMOLED;
    }

    /** Supplies the XDS dark-surface color used by transparent chrome and its haze tint. */
    public static long xdsChromeBackground(long originalColor) {
        Context context = Utils.getContext();
        if (context == null) {
            throw new IllegalStateException("NewX XDS colors need the initialized host context");
        }

        return resolveXdsChromeBackground(
                TwitterTheme.fromContext(context),
                useAmoledBlack(),
                originalColor
        );
    }

    /**
     * The chrome background always matches the base surface of the selected dark style: AMOLED
     * chrome is pure black and Dim chrome is the classic dim blue.
     */
    static long resolveXdsChromeBackground(
            TwitterTheme theme,
            boolean amoledBlack,
            long originalColor
    ) {
        if (theme == TwitterTheme.STANDARD) return originalColor;
        return pack(amoledBlack ? BLACK_XDS_BACKGROUND : DIM_XDS_BACKGROUND);
    }

    public static long light(int token) {
        requireSupported();
        return paletteColor(token, false);
    }

    /** The DIM factory has no separate AMOLED decision: the chooser owns the surface family. */
    public static long dark(int token) {
        requireSupported();
        return darkStyleColor(token, useAmoledBlack());
    }

    /** LIGHTS_OUT supplies the one dark-style decision made by its injected factory. */
    public static long lightsOut(int token, boolean amoledBlack) {
        requireSupported();
        return darkStyleColor(token, amoledBlack);
    }

    /**
     * Resolves the background family of the selected dark style. NewX routes both "Dim" and
     * "Lights out" to the LIGHTS_OUT factory, so AMOLED keeps pure-black base surfaces with the
     * app's own lights-out elevated surfaces, while Dim restores the classic blue-gray dim family.
     * Every other token keeps the Material You role it has on the dark palette.
     */
    static long darkStyleColor(int token, boolean amoledBlack) {
        return switch (token) {
            case CELL_BACKGROUND, APP_BACKGROUND ->
                    amoledBlack ? black(0xFF) : pack(DIM_CELL_BACKGROUND);
            case CELL_BACKGROUND_TRANSLUCENT ->
                    amoledBlack
                            ? black(ALPHA_LIGHTS_OUT_TRANSLUCENT)
                            : pack(DIM_TRANSLUCENT_CELL_BACKGROUND);
            case HIGHLIGHT_BACKGROUND ->
                    amoledBlack
                            ? pack(AMOLED_HIGHLIGHT_BACKGROUND)
                            : pack(DIM_HIGHLIGHT_BACKGROUND);
            case GLASS_BACKGROUND ->
                    amoledBlack
                            ? pack(AMOLED_GLASS_BACKGROUND)
                            : pack(DIM_GLASS_BACKGROUND);
            default -> paletteColor(token, true);
        };
    }

    /** Uses Material 3's lower-emphasis on-surface-variant role for normal action icons. */
    public static long inlineActionTint(long originalColor) {
        if (!isEnabled()) return originalColor;
        return dynamicColor(isDarkTheme(), LIGHT_ON_SURFACE_VARIANT, DARK_ON_SURFACE_VARIANT);
    }

    /** Uses Material 3 primary for selected action icons. */
    public static long inlineActionActiveTint(long originalColor) {
        if (!isLikeThemingEnabled()) return originalColor;
        return dynamicColor(isDarkTheme(), LIGHT_PRIMARY, DARK_PRIMARY);
    }

    /**
     * Uses Material 3 on-surface for tab labels, icons, and selected indicators (XDS foreground
     * roles). Tab slots encode selection emphasis in alpha, so the incoming alpha is kept.
     */
    public static long tabTint(long originalColor) {
        if (!isEnabled()) return originalColor;
        return tabRole(isDarkTheme(), LIGHT_ON_SURFACE, DARK_ON_SURFACE, originalColor);
    }

    /** Lower-emphasis tab slots (XDS secondary) map to on-surface-variant with alpha kept. */
    public static long tabSecondaryTint(long originalColor) {
        if (!isEnabled()) return originalColor;
        return tabRole(
                isDarkTheme(),
                LIGHT_ON_SURFACE_VARIANT,
                DARK_ON_SURFACE_VARIANT,
                originalColor
        );
    }

    private static long tabRole(boolean dark, String lightName, String darkName, long originalColor) {
        int alpha = (int) (originalColor >>> 56) << 24;
        return pack((requiredColor(dark ? darkName : lightName) & 0x00FFFFFF) | alpha);
    }

    /** Lottie heart assets embed red fills and cannot inherit Compose's dynamic content color. */
    public static boolean inlineLikeAnimation(boolean originalValue) {
        return !isLikeThemingEnabled() && originalValue;
    }

    public static long accentTone0(long originalColor, boolean enabled) {
        return accentTone(0, originalColor, enabled);
    }

    public static long accentTone1(long originalColor, boolean enabled) {
        return accentTone(1, originalColor, enabled);
    }

    public static long accentTone2(long originalColor, boolean enabled) {
        return accentTone(2, originalColor, enabled);
    }

    public static long accentTone3(long originalColor, boolean enabled) {
        return accentTone(3, originalColor, enabled);
    }

    public static long accentTone4(long originalColor, boolean enabled) {
        return accentTone(4, originalColor, enabled);
    }

    public static long accentTone5(long originalColor, boolean enabled) {
        return accentTone(5, originalColor, enabled);
    }

    public static long accentTone6(long originalColor, boolean enabled) {
        return accentTone(6, originalColor, enabled);
    }

    public static long accentTone7(long originalColor, boolean enabled) {
        return accentTone(7, originalColor, enabled);
    }

    public static long accentTone8(long originalColor, boolean enabled) {
        return accentTone(8, originalColor, enabled);
    }

    public static long accentTone9(long originalColor, boolean enabled) {
        return accentTone(9, originalColor, enabled);
    }

    public static long accentTone10(long originalColor, boolean enabled) {
        return accentTone(10, originalColor, enabled);
    }

    public static long accentTone11(long originalColor, boolean enabled) {
        return accentTone(11, originalColor, enabled);
    }

    public static long accentTone12(long originalColor, boolean enabled) {
        return accentTone(12, originalColor, enabled);
    }

    /** Replaces NewX's 13-step blue ramp while retaining its original pre-Android-12 value. */
    private static long accentTone(int tone, long originalColor, boolean enabled) {
        if (!enabled) return originalColor;

        String resourceName = switch (tone) {
            case 0 -> "material_dynamic_primary100";
            case 1 -> "material_dynamic_primary99";
            case 2 -> "material_dynamic_primary95";
            case 3 -> "material_dynamic_primary90";
            case 4 -> "material_dynamic_primary80";
            case 5 -> "material_dynamic_primary70";
            case 6 -> "material_dynamic_primary60";
            case 7 -> "material_dynamic_primary50";
            case 8 -> "material_dynamic_primary40";
            case 9 -> "material_dynamic_primary30";
            case 10 -> "material_dynamic_primary20";
            case 11 -> "material_dynamic_primary10";
            case 12 -> "material_dynamic_primary0";
            default -> throw new IllegalArgumentException(
                    "Unknown NewX dynamic accent tone: " + tone
            );
        };
        return color(resourceName);
    }

    private static long paletteColor(int token, boolean dark) {
        return switch (token) {
            case PRIMARY, LINK -> dynamicColor(dark, LIGHT_PRIMARY, DARK_PRIMARY);
            case PRIMARY_TEXT -> dynamicColor(dark, LIGHT_ON_SURFACE, DARK_ON_SURFACE);
            case SECONDARY_TEXT, TERTIARY ->
                    dynamicColor(dark, LIGHT_ON_SURFACE_VARIANT, DARK_ON_SURFACE_VARIANT);
            // Tombstone is a container background in the Compose post interstitial, not text.
            case TOMBSTONE -> dynamicColor(
                    dark,
                    LIGHT_SURFACE_CONTAINER_HIGH,
                    DARK_SURFACE_CONTAINER_HIGH
            );
            case ON_PRIMARY -> dynamicColor(dark, LIGHT_ON_PRIMARY, DARK_ON_PRIMARY);
            case DIVIDER, BORDER -> dynamicColor(dark, LIGHT_OUTLINE_VARIANT, DARK_OUTLINE_VARIANT);
            // Surfaces on the dark paths come from the dark style chooser; only the light palette
            // reads the Material You surface roles here.
            case CELL_BACKGROUND, APP_BACKGROUND ->
                    dynamicColor(dark, LIGHT_SURFACE, DARK_SURFACE);
            case CELL_BACKGROUND_TRANSLUCENT -> colorWithAlpha(
                    dark ? DARK_SURFACE_CONTAINER_LOW : LIGHT_SURFACE_CONTAINER_LOW,
                    ALPHA_STANDARD_DIM_TRANSLUCENT
            );
            case HIGHLIGHT_BACKGROUND ->
                    dynamicColor(dark, LIGHT_SURFACE_CONTAINER_HIGH, DARK_SURFACE_CONTAINER_HIGH);
            case UNREAD -> dynamicColor(dark, LIGHT_PRIMARY_CONTAINER, DARK_PRIMARY_CONTAINER);
            case GLASS_BORDER -> dynamicColor(dark, LIGHT_OUTLINE, DARK_OUTLINE);
            case GLASS_BACKGROUND -> colorWithAlpha(
                    dark ? DARK_SURFACE : LIGHT_SURFACE,
                    ALPHA_GLASS_BACKGROUND
            );
            case GLASS_SHADOW -> black(
                    dark ? ALPHA_DARK_GLASS_SHADOW : ALPHA_LIGHT_GLASS_SHADOW
            );
            default -> throw new IllegalArgumentException("Unknown NewX dynamic color token: " + token);
        };
    }

    private static boolean isLikeThemingEnabled() {
        return isEnabled() && SettingsRegistry.getBooleanOrDefault(DYNAMIC_LIKE_SETTING, true);
    }

    private static long dynamicColor(boolean dark, String lightName, String darkName) {
        return color(dark ? darkName : lightName);
    }

    private static boolean isDarkTheme() {
        Context context = Utils.getContext();
        if (context == null) {
            throw new IllegalStateException(
                    "NewX dynamic color needs the initialized host application context"
            );
        }
        return TwitterTheme.fromContext(context).isDark();
    }

    private static long colorWithAlpha(String resourceName, int alpha) {
        return pack((requiredColor(resourceName) & 0x00FFFFFF) | (alpha << 24));
    }

    private static long color(String resourceName) {
        return pack(requiredColor(resourceName));
    }

    private static long black(int alpha) {
        return pack(alpha << 24);
    }

    private static int requiredColor(String resourceName) {
        Context context = Utils.getContext();
        if (context == null) {
            throw new IllegalStateException(
                    "NewX dynamic color needs the initialized host application context"
            );
        }

        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier(resourceName, "color", context.getPackageName());
        if (resourceId == 0) {
            throw new IllegalStateException(
                    "NewX dynamic color resource is missing on Android 12+: " + resourceName
            );
        }
        return resources.getColor(resourceId, context.getTheme());
    }

    private static long pack(int argb) {
        return ((long) argb) << 32;
    }

    private static void requireSupported() {
        if (isSupported()) return;
        throw new IllegalStateException("NewX dynamic color requires Android 12 or newer");
    }
}
