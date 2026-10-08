package app.morphe.extension.newx.theme;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.crimera.settings.SettingsRegistry;

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

    public static final int XDS_FOREGROUND_PRIMARY = 0;
    public static final int XDS_FOREGROUND_SECONDARY = 1;
    public static final int XDS_FOREGROUND_TERTIARY = 2;

    private static final String DYNAMIC_LIKE_SETTING = "newx.theme.dynamic_like";
    private static final String THEME_SETTING = "newx.theme.dark_style";
    private static final String THEME_DEFAULT = "default";
    private static final String THEME_MATERIAL = "material";
    private static final String THEME_CONTRAST = "contrast";
    private static final String THEME_DIM = "dim";
    /** Framework equivalents of the 13-step ramp, indexed by NewX tone (primary100 .. primary0). */
    private static final String[] ACCENT_FRAMEWORK_NAMES = {
            "system_accent1_0", "system_accent1_10", "system_accent1_50", "system_accent1_100",
            "system_accent1_200", "system_accent1_300", "system_accent1_400", "system_accent1_500",
            "system_accent1_600", "system_accent1_700", "system_accent1_800", "system_accent1_900",
            "system_accent1_1000",
    };
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
    private static final long COMPOSE_COLOR_SPACE_MASK = 0x3FL;
    private static final long SRGB_WHITE = 0xFFFFFFFF00000000L;
    // Compose Color.Unspecified: zero components in the "None" color space (id 0x10).
    private static final long COMPOSE_UNSPECIFIED = 0x10L;
    // Default-flag bit of the shared icon composable that selects the ambient tint.
    private static final int ICON_DEFAULT_TINT_BIT = 0x8;
    // Opaque #E5EAEC author handle color (Compose encoding of sRGB with alpha 1).
    private static final long SRGB_HANDLE_GRAY = 0xFFE5EAEC00000000L;
    private static final int ALPHA_STANDARD_DIM_TRANSLUCENT = 0xBF;
    private static final int ALPHA_GLASS_BACKGROUND = 0xCC;
    private static final int ALPHA_LIGHT_GLASS_SHADOW = 0x26;
    private static final int ALPHA_DARK_GLASS_SHADOW = 0x50;
    private static final int DIM_XDS_BACKGROUND = 0xFF15202B;
    private static final int BLACK_XDS_BACKGROUND = 0xFF000000;
    // High-contrast light surfaces: base surfaces are pure white, elevated surfaces keep a
    // light gray so popups, dialogs, and sheets stay visible against the white base.
    private static final int CONTRAST_LIGHT_BACKGROUND = 0xFFFFFFFF;
    private static final int CONTRAST_LIGHT_TRANSLUCENT_BACKGROUND = 0x80FFFFFF;
    private static final int CONTRAST_LIGHT_ELEVATED_BACKGROUND = 0xFFEEEEEE;
    private static final int CONTRAST_LIGHT_GLASS_BACKGROUND = 0xCCEEEEEE;
    // High-contrast dark surfaces: base surfaces are pure black, elevated surfaces keep the
    // app's own LIGHTS_OUT family so popups, dialogs, sheets, and glass panels stay visible
    // against the black base.
    private static final int CONTRAST_DARK_TRANSLUCENT_BACKGROUND = 0x80000000;
    private static final int CONTRAST_DARK_ELEVATED_BACKGROUND = 0xFF121314;
    private static final int CONTRAST_DARK_GLASS_BACKGROUND = 0xCC242424;
    private DynamicColorPalette() {
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static boolean isEnabled() {
        return isSupported() && !useDimTheme() && !useDefaultTheme();
    }

    /** Whether the theme chooser selected Material You system surfaces. */
    public static boolean useMaterialBackground() {
        return THEME_MATERIAL.equals(themeStyle());
    }

    /** Whether the theme chooser selected the classic static Dim theme. */
    public static boolean useDimTheme() {
        return THEME_DIM.equals(themeStyle());
    }

    /** Whether the theme chooser selected the unmodified original NewX colors. */
    public static boolean useDefaultTheme() {
        return THEME_DEFAULT.equals(themeStyle());
    }

    static String themeStyle() {
        return normalizeThemeStyle(
                SettingsRegistry.getStringOrDefault(THEME_SETTING, THEME_CONTRAST)
        );
    }

    /**
     * Maps the stored chooser value to a known theme. The legacy AMOLED value and unknown
     * values resolve to high contrast, which keeps their pure-black dark backgrounds.
     */
    static String normalizeThemeStyle(String style) {
        if (THEME_DEFAULT.equals(style)) return THEME_DEFAULT;
        if (THEME_MATERIAL.equals(style)) return THEME_MATERIAL;
        if (THEME_DIM.equals(style)) return THEME_DIM;
        return THEME_CONTRAST;
    }

    /**
     * Supplies the XDS dark-surface color used by transparent chrome and its haze tint. Material
     * You follows the dynamic surface, high contrast is pure black, and Dim keeps the classic
     * dim blue. With dynamic colors off there is no system surface to resolve, so Material You
     * keeps the native chrome.
     */
    public static long xdsChromeBackground(long originalColor) {
        Context context = Utils.getContext();
        if (context == null) {
            throw new IllegalStateException("NewX XDS colors need the initialized host context");
        }

        if (useDefaultTheme()) return originalColor;
        if (useMaterialBackground()) {
            if (!isEnabled()) return originalColor;
            return color(DARK_SURFACE);
        }
        return resolveXdsChromeBackground(
                TwitterTheme.fromContext(context),
                themeStyle(),
                originalColor
        );
    }

    /**
     * The chrome background always matches the base surface of the selected fixed theme:
     * high-contrast chrome is pure black and Dim chrome is the classic dim blue.
     */
    static long resolveXdsChromeBackground(
            TwitterTheme theme,
            String themeStyle,
            long originalColor
    ) {
        if (theme == TwitterTheme.STANDARD) return originalColor;
        return pack(THEME_DIM.equals(themeStyle) ? DIM_XDS_BACKGROUND : BLACK_XDS_BACKGROUND);
    }

    public static long light(int token) {
        requireSupported();
        requireDynamicTheme();
        if (!useMaterialBackground()) return contrastLightColor(token);
        return paletteColor(token, false);
    }

    /**
     * The DIM factory resolves through the same chooser-owned background family as LIGHTS_OUT.
     * The static Dim theme never reaches this method: its guard keeps the native palette.
     */
    public static long dark(int token) {
        requireSupported();
        requireDynamicTheme();
        if (useMaterialBackground()) return paletteColor(token, true);
        return contrastDarkColor(token);
    }

    /** LIGHTS_OUT resolves through the same chooser-owned background family as the DIM factory. */
    public static long lightsOut(int token) {
        requireSupported();
        requireDynamicTheme();
        if (useMaterialBackground()) return paletteColor(token, true);
        return contrastDarkColor(token);
    }

    /**
     * High-contrast light backgrounds are pure white with light-gray elevation; every other
     * token keeps the Material You role it has on the light palette.
     */
    static long contrastLightColor(int token) {
        return switch (token) {
            case CELL_BACKGROUND, APP_BACKGROUND -> pack(CONTRAST_LIGHT_BACKGROUND);
            case CELL_BACKGROUND_TRANSLUCENT -> pack(CONTRAST_LIGHT_TRANSLUCENT_BACKGROUND);
            case HIGHLIGHT_BACKGROUND -> pack(CONTRAST_LIGHT_ELEVATED_BACKGROUND);
            case GLASS_BACKGROUND -> pack(CONTRAST_LIGHT_GLASS_BACKGROUND);
            default -> paletteColor(token, false);
        };
    }

    /**
     * High-contrast dark backgrounds are pure black with LIGHTS_OUT elevation; every other
     * token keeps the Material You role it has on the dark palette. The Material You theme
     * bypasses this method and resolves every token from the dynamic dark palette.
     */
    static long contrastDarkColor(int token) {
        return switch (token) {
            case CELL_BACKGROUND, APP_BACKGROUND -> pack(BLACK_XDS_BACKGROUND);
            case CELL_BACKGROUND_TRANSLUCENT -> pack(CONTRAST_DARK_TRANSLUCENT_BACKGROUND);
            case HIGHLIGHT_BACKGROUND -> pack(CONTRAST_DARK_ELEVATED_BACKGROUND);
            case GLASS_BACKGROUND -> pack(CONTRAST_DARK_GLASS_BACKGROUND);
            default -> paletteColor(token, true);
        };
    }

    /**
     * XDS components such as nested quote bodies read text colors straight from the XDS scheme,
     * bypassing the Horizon palette. The scheme builds once per variant, so light/dark comes
     * from the scheme, not the current system theme.
     */
    public static long xdsForeground(int role, boolean isLight, long originalColor) {
        if (!isEnabled()) return originalColor;
        return switch (role) {
            case XDS_FOREGROUND_PRIMARY ->
                    dynamicColor(!isLight, LIGHT_ON_SURFACE, DARK_ON_SURFACE);
            case XDS_FOREGROUND_SECONDARY, XDS_FOREGROUND_TERTIARY ->
                    dynamicColor(!isLight, LIGHT_ON_SURFACE_VARIANT, DARK_ON_SURFACE_VARIANT);
            default -> throw new IllegalArgumentException("Unknown NewX XDS foreground role: " + role);
        };
    }

    /**
     * The media viewer chrome draws its name, caption, and follow label as plain sRGB white over
     * video. Only that exact white is replaced, so every other text color keeps its value.
     */
    public static long mediaTextColor(long originalColor) {
        if (!isEnabled()) return originalColor;
        if (originalColor == SRGB_WHITE) return color(DARK_ON_SURFACE);
        // The author handle is a fixed opaque gray (#E5EAEC) that reads as secondary text.
        if (originalColor == SRGB_HANDLE_GRAY) return color(DARK_ON_SURFACE_VARIANT);
        return originalColor;
    }


    /**
     * Icon tint for the hooked media icon call sites. The hooked sites pass either explicit sRGB
     * white, Compose Unspecified ({@code Color.Unspecified} is the "None" color space id 0x10 with
     * zero components), or raw zero with the icon's default bit set (ambient LocalContentColor).
     * Raw zero is only passed with the default bit set at those sites, so it is replaced as the
     * ambient case. Only call sites patched to route through this hook are affected.
     */
    public static long mediaIconTint(long originalColor) {
        if (!isEnabled()) return originalColor;
        if (originalColor == SRGB_WHITE
                || originalColor == COMPOSE_UNSPECIFIED
                || originalColor == 0L) {
            return color(DARK_ON_SURFACE);
        }
        return originalColor;
    }

    /**
     * Clears the icon default-tint bit so the explicit palette tint from {@link #mediaIconTint}
     * takes effect instead of the ambient LocalContentColor. Hooked sites without the bit are
     * unchanged.
     */
    public static int mediaIconDefaults(int defaults) {
        if (!isEnabled()) return defaults;
        return defaults & ~ICON_DEFAULT_TINT_BIT;
    }

    /** Uses Material 3's lower-emphasis on-surface-variant role for normal action icons. */
    public static long inlineActionTint(long originalColor) {
        if (!isEnabled()) return originalColor;
        return dynamicColor(isDarkTheme(), LIGHT_ON_SURFACE_VARIANT, DARK_ON_SURFACE_VARIANT);
    }

    /**
     * Action counts take an explicit content color only where the host overrides the default
     * (the media viewer passes plain white); an unspecified color keeps the default, which
     * already follows the tint. Only sRGB values are explicit: Compose encodes the unspecified
     * color with a different color space.
     */
    public static long inlineActionContentTint(long originalColor) {
        if (!isEnabled() || (originalColor & COMPOSE_COLOR_SPACE_MASK) != 0) return originalColor;
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
        return pack(requiredColor(resourceName, ACCENT_FRAMEWORK_NAMES[tone]));
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
        return isEnabled() && SettingsRegistry.getBooleanOrDefault(DYNAMIC_LIKE_SETTING, false);
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

    /**
     * Guards the dynamic palette entry points. The static Dim and Default themes keep the
     * native palette, so reaching them means the injected guard failed closed.
     */
    private static void requireDynamicTheme() {
        if (!useDimTheme() && !useDefaultTheme()) return;
        throw new IllegalStateException("NewX dynamic palette requires a dynamic theme");
    }

    private static int requiredColor(String resourceName) {
        return requiredColor(resourceName, null);
    }

    /**
     * Reads an app color resource, optionally falling back to the Android 12 framework palette.
     * 12.33 alpha.02 shrank the app's {@code material_dynamic_primary*} ramp out of its resources,
     * but each tone is the same value as the framework's {@code system_accent1_*} color.
     */
    private static int requiredColor(String resourceName, String frameworkFallbackName) {
        Context context = Utils.getContext();
        if (context == null) {
            throw new IllegalStateException(
                    "NewX dynamic color needs the initialized host application context"
            );
        }

        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier(resourceName, "color", context.getPackageName());
        if (resourceId == 0 && frameworkFallbackName != null) {
            resourceId = resources.getIdentifier(frameworkFallbackName, "color", "android");
        }
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
