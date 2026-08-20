package app.morphe.extension.newx.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** Resolves X/Twitter's effective theme from its own preference state. */
public enum TwitterTheme {
    STANDARD("Twitter.Standard", false),
    DIM("Twitter.Dim", true),
    LIGHTS_OUT("Twitter.LightsOut", true);

    private static final String X_APP_PREFERENCES = "x_app_preferences";
    private static final String DARK_MODE_STATE_KEY = "dark_mode_state";
    private static final String DARK_MODE_APPEARANCE_KEY = "dark_mode_appearance";
    private static final String SYSTEM_NIGHT_MODE = "2";
    private static final String FORCED_DARK_NIGHT_MODE = "1";
    private static final String DIM_APPEARANCE = "dim";
    private static final String LIGHTS_OUT_APPEARANCE = "lights_out";

    private final String styleResourceName;
    private final boolean dark;

    TwitterTheme(String styleResourceName, boolean dark) {
        this.styleResourceName = styleResourceName;
        this.dark = dark;
    }

    public static TwitterTheme fromContext(Context context) {
        if (context == null) return LIGHTS_OUT;

        Context applicationContext = context.getApplicationContext();
        Context preferenceContext = applicationContext == null ? context : applicationContext;
        SharedPreferences preferences = preferenceContext.getSharedPreferences(
                X_APP_PREFERENCES,
                Context.MODE_PRIVATE
        );
        return resolve(
                preferenceString(preferences, DARK_MODE_STATE_KEY, SYSTEM_NIGHT_MODE),
                preferenceString(preferences, DARK_MODE_APPEARANCE_KEY, LIGHTS_OUT_APPEARANCE),
                isSystemDark(preferenceContext)
        );
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
