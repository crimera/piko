package app.morphe.extension.newx.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.HashMap;
import java.util.Map;

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

    // Theme reads run for every post's action bar while scrolling. Cache the resolved strings
    // and drop them whenever any of the three stores changes; system night mode stays live.
    private static final Object CACHE_LOCK = new Object();
    private static final Map<String, String> CACHED_VALUES = new HashMap<>();
    private static final SharedPreferences[] NO_STORES = new SharedPreferences[0];
    // Stores hold listeners weakly; this static reference keeps it registered.
    private static final SharedPreferences.OnSharedPreferenceChangeListener INVALIDATE_CACHE =
            (preferences, key) -> invalidateCache();
    private static Context cachedContext;
    private static SharedPreferences[] watchedStores = NO_STORES;
    private static long cacheGeneration;

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
        Context preferenceContext = preferenceContext(context);
        long generation;
        synchronized (CACHE_LOCK) {
            if (preferenceContext != cachedContext) watch(preferenceContext);
            if (CACHED_VALUES.containsKey(key)) {
                String cached = CACHED_VALUES.get(key);
                return cached == null ? defaultValue : cached;
            }
            generation = cacheGeneration;
        }

        String value = preferenceString(preferences(preferenceContext), key, null);
        if (value == null) {
            value = preferenceString(morphePreferences(preferenceContext), key, null);
        }
        if (value == null) {
            value = preferenceString(legacyPreferences(preferenceContext), key, null);
        }

        synchronized (CACHE_LOCK) {
            // A change landed while reading; the next call reads again.
            if (generation == cacheGeneration && preferenceContext == cachedContext) {
                CACHED_VALUES.put(key, value);
            }
        }
        return value == null ? defaultValue : value;
    }

    private static void watch(Context preferenceContext) {
        for (SharedPreferences store : watchedStores) {
            store.unregisterOnSharedPreferenceChangeListener(INVALIDATE_CACHE);
        }
        SharedPreferences[] stores = {
                preferences(preferenceContext),
                morphePreferences(preferenceContext),
                legacyPreferences(preferenceContext),
        };
        for (SharedPreferences store : stores) {
            store.registerOnSharedPreferenceChangeListener(INVALIDATE_CACHE);
        }
        watchedStores = stores;
        cachedContext = preferenceContext;
        CACHED_VALUES.clear();
        cacheGeneration++;
    }

    private static void invalidateCache() {
        synchronized (CACHE_LOCK) {
            CACHED_VALUES.clear();
            cacheGeneration++;
        }
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
