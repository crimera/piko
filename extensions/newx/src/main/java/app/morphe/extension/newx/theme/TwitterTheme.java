package app.morphe.extension.newx.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** Resolves X/Twitter's effective theme and accent from its own preference state. */
public enum TwitterTheme {
    STANDARD("Twitter.Standard", false),
    DIM("Twitter.Dim", true),
    LIGHTS_OUT("Twitter.LightsOut", true);

    // NewX's host theme manager uses the app's standard "<package>_preferences" store.
    // Keep the Morphe and pre-bridge stores as compatibility fallbacks for older targets.
    private static final String ACCENT_COLOR_KEY = "xlite_accent_color";
    private static final String HOST_PREFERENCES_SUFFIX = "_preferences";
    private static final String MORPHE_PREFERENCES = "morphe_prefs";
    private static final String LEGACY_HOST_PREFERENCES = "x_app_preferences";
    private static final String DARK_MODE_STATE_KEY = "dark_mode_state";
    private static final String DARK_MODE_APPEARANCE_KEY = "dark_mode_appearance";
    private static final String SYSTEM_NIGHT_MODE = "2";
    private static final String FORCED_DARK_NIGHT_MODE = "1";
    private static final String DIM_APPEARANCE = "dim";
    private static final String LIGHTS_OUT_APPEARANCE = "lights_out";

    public enum Accent {
        BLUE("blue", 0xFF1D9BF0, 0xFFFFFFFF),
        YELLOW("yellow", 0xFFFFD400, 0xFF000000),
        PINK("pink", 0xFFF91880, 0xFFFFFFFF),
        PURPLE("purple", 0xFF7856FF, 0xFFFFFFFF),
        ORANGE("orange", 0xFFFF7A00, 0xFF000000),
        GREEN("green", 0xFF00BA7C, 0xFF000000);

        private final String preferenceValue;
        private final int primaryColor;
        private final int onPrimaryColor;

        Accent(String preferenceValue, int primaryColor, int onPrimaryColor) {
            this.preferenceValue = preferenceValue;
            this.primaryColor = primaryColor;
            this.onPrimaryColor = onPrimaryColor;
        }

        public int primaryColor() {
            return primaryColor;
        }

        public int onPrimaryColor() {
            return onPrimaryColor;
        }
    }

    private final String styleResourceName;
    private final boolean dark;

    TwitterTheme(String styleResourceName, boolean dark) {
        this.styleResourceName = styleResourceName;
        this.dark = dark;
    }

    public static Accent accent(Context context) {
        if (context == null) return Accent.BLUE;
        return resolveAccent(hostPreferenceString(
                context,
                ACCENT_COLOR_KEY,
                Accent.BLUE.preferenceValue
        ));
    }

    public static TwitterTheme fromContext(Context context) {
        if (context == null) return LIGHTS_OUT;

        return resolve(
                hostPreferenceString(context, DARK_MODE_STATE_KEY, SYSTEM_NIGHT_MODE),
                hostPreferenceString(context, DARK_MODE_APPEARANCE_KEY, LIGHTS_OUT_APPEARANCE),
                isSystemDark(preferenceContext(context))
        );
    }

    static Accent resolveAccent(String preferenceValue) {
        if (preferenceValue != null) {
            for (Accent accent : Accent.values()) {
                if (accent.preferenceValue.equals(preferenceValue)) return accent;
            }
        }
        return Accent.BLUE;
    }

    private static SharedPreferences preferences(Context context) {
        Context preferenceContext = preferenceContext(context);
        return sharedPreferences(
                preferenceContext,
                preferenceContext.getPackageName() + HOST_PREFERENCES_SUFFIX
        );
    }

    private static SharedPreferences morphePreferences(Context context) {
        return sharedPreferences(context, MORPHE_PREFERENCES);
    }

    private static SharedPreferences legacyPreferences(Context context) {
        return sharedPreferences(context, LEGACY_HOST_PREFERENCES);
    }

    private static SharedPreferences sharedPreferences(Context context, String name) {
        return preferenceContext(context).getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    private static String hostPreferenceString(
            Context context,
            String key,
            String defaultValue
    ) {
        String value = preferenceString(preferences(context), key, null);
        if (value == null) {
            value = preferenceString(morphePreferences(context), key, null);
        }
        return value == null
                ? preferenceString(legacyPreferences(context), key, defaultValue)
                : value;
    }

    private static Context preferenceContext(Context context) {
        Context applicationContext = context.getApplicationContext();
        return applicationContext == null ? context : applicationContext;
    }


    static TwitterTheme resolve(
            String nightMode,
            String darkModeAppearance,
            boolean systemDark
    ) {
        boolean darkTheme = FORCED_DARK_NIGHT_MODE.equals(nightMode)
                || (SYSTEM_NIGHT_MODE.equals(nightMode) && systemDark);
        if (!darkTheme) return STANDARD;
        if (DIM_APPEARANCE.equals(darkModeAppearance)) return DIM;
        if (LIGHTS_OUT_APPEARANCE.equals(darkModeAppearance)) return LIGHTS_OUT;
        return STANDARD;
    }

    public String styleResourceName() {
        return styleResourceName;
    }

    public boolean isDark() {
        return dark;
    }

    private static String preferenceString(
            SharedPreferences preferences,
            String key,
            String defaultValue
    ) {
        try {
            return preferences.getString(key, defaultValue);
        } catch (ClassCastException ignored) {
            return defaultValue;
        }
    }

    private static boolean isSystemDark(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

}
