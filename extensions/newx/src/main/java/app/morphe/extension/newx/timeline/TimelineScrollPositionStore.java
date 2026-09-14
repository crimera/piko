package app.morphe.extension.newx.timeline;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.newx.settings.SettingsRegistry;

public final class TimelineScrollPositionStore {
    private static final String PREFERENCES_NAME = "piko_newx_timeline_positions";
    private static final String RESTORE_TIMELINE_POSITION_SETTING =
            "newx.timeline.restore_position";
    private static final String RESTORE_PROFILE_POSITION_SETTING =
            "newx.profile.restore_position";
    private static final String INDEX_SUFFIX = ".index";
    private static final String OFFSET_SUFFIX = ".offset";
    private static final String PROFILE_KEY_PREFIX = "profile.";
    private static final Pattern POSITION_PATTERN = Pattern.compile(
            "ScrollPositionHolder\\(firstVisibleItemIndex=(\\d+), firstVisibleItemScrollOffset=(\\d+)\\)"
    );
    private static final Object SAVE_LOCK = new Object();
    private static volatile SharedPreferences cachedPreferences;
    private static SharedPreferences lastSavedPreferences;
    private static String lastSavedKey;
    private static int lastSavedIndex = -1;
    private static int lastSavedOffset = -1;

    private TimelineScrollPositionStore() {
    }

    @Nullable
    public static int[] restore(Enum<?> timeline) {
        return restore(timeline, null);
    }

    @Nullable
    public static int[] restore(Enum<?> timeline, @Nullable String profileId) {
        try {
            boolean restoreTimelinePosition = SettingsRegistry.getBooleanOrDefault(
                    RESTORE_TIMELINE_POSITION_SETTING,
                    true
            );
            boolean restoreProfilePosition = SettingsRegistry.getBooleanOrDefault(
                    RESTORE_PROFILE_POSITION_SETTING,
                    false
            );
            String timelineName = timeline == null ? null : timeline.name();
            String key = storageKey(
                    timelineName,
                    profileId,
                    restoreTimelinePosition,
                    restoreProfilePosition
            );
            if (NewXLogger.isLoggingEnabled()) {
                NewXLogger.logger("NewX restore timeline=" + timelineName + " profileId=" + profileId
                        + " key=" + key + " restoreTimeline=" + restoreTimelinePosition
                        + " restoreProfile=" + restoreProfilePosition);
            }
            if (key == null) return null;

            SharedPreferences preferences = preferences();
            if (preferences == null) {
                if (NewXLogger.isLoggingEnabled()) {
                    NewXLogger.logger("NewX restore miss timeline=" + timelineName + " reason=no-preferences");
                }
                return null;
            }

            if (!preferences.contains(key + INDEX_SUFFIX)) {
                if (NewXLogger.isLoggingEnabled()) {
                    NewXLogger.logger("NewX restore miss timeline=" + timelineName + " key=" + key
                            + " reason=no-entry");
                }
                return null;
            }

            int index = preferences.getInt(key + INDEX_SUFFIX, 0);
            int offset = preferences.getInt(key + OFFSET_SUFFIX, 0);
            if (NewXLogger.isLoggingEnabled()) {
                NewXLogger.logger("NewX restore hit timeline=" + timelineName + " key=" + key
                        + " index=" + index + " offset=" + offset);
            }
            return new int[]{index, offset};
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to restore NewX timeline position", exception);
            return null;
        }
    }

    public static void save(Enum<?> timeline, Object holder) {
        save(timeline, null, holder);
    }

    public static void save(
            Enum<?> timeline,
            @Nullable String profileId,
            Object holder
    ) {
        if (holder == null) {
            if (NewXLogger.isLoggingEnabled()) {
                NewXLogger.logger("NewX save skip reason=null-holder");
            }
            return;
        }

        try {
            boolean restoreTimelinePosition = SettingsRegistry.getBooleanOrDefault(
                    RESTORE_TIMELINE_POSITION_SETTING,
                    true
            );
            boolean restoreProfilePosition = SettingsRegistry.getBooleanOrDefault(
                    RESTORE_PROFILE_POSITION_SETTING,
                    false
            );
            String timelineName = timeline == null ? null : timeline.name();
            String key = storageKey(
                    timelineName,
                    profileId,
                    restoreTimelinePosition,
                    restoreProfilePosition
            );
            if (key == null) {
                if (NewXLogger.isLoggingEnabled()) {
                    NewXLogger.logger("NewX save skip timeline=" + timelineName + " profileId=" + profileId
                            + " reason=unsupported-key restoreTimeline=" + restoreTimelinePosition
                            + " restoreProfile=" + restoreProfilePosition);
                }
                return;
            }

            SharedPreferences preferences = preferences();
            if (preferences == null) {
                if (NewXLogger.isLoggingEnabled()) {
                    NewXLogger.logger("NewX save skip timeline=" + timelineName + " key=" + key
                            + " reason=no-preferences");
                }
                return;
            }

            int index = -1;
            int offset = -1;

            Matcher matcher = POSITION_PATTERN.matcher(holder.toString());
            if (matcher.matches()) {
                index = Integer.parseInt(matcher.group(1));
                offset = Integer.parseInt(matcher.group(2));
            } else {
                Field[] fields = holder.getClass().getDeclaredFields();
                int found = 0;
                for (Field field : fields) {
                    if (field.getType() == int.class && !Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        if (found == 0) {
                            index = field.getInt(holder);
                            found++;
                        } else if (found == 1) {
                            offset = field.getInt(holder);
                            found++;
                            break;
                        }
                    }
                }
                if (found < 2) {
                    if (NewXLogger.isLoggingEnabled()) {
                        NewXLogger.logger("NewX save skip timeline=" + timelineName + " key=" + key
                                + " reason=unparseable-holder");
                    }
                    return;
                }
            }

            if (index < 0 || offset < 0) {
                if (NewXLogger.isLoggingEnabled()) {
                    NewXLogger.logger("NewX save skip timeline=" + timelineName + " key=" + key
                            + " reason=negative-position");
                }
                return;
            }

            if (NewXLogger.isLoggingEnabled()) {
                NewXLogger.logger("NewX save timeline=" + timelineName + " key=" + key
                        + " index=" + index + " offset=" + offset);
            }
            preferences.edit()
                    .putInt(key + INDEX_SUFFIX, index)
                    .putInt(key + OFFSET_SUFFIX, offset)
                    .apply();
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to save NewX timeline position", exception);
        }
    }

    /** Saves an already decoded position without allocating or reflectively parsing the holder. */
    public static void save(
            Enum<?> timeline,
            @Nullable String profileId,
            int index,
            int offset
    ) {
        boolean loggingEnabled = NewXLogger.isLoggingEnabled();
        if (index < 0 || offset < 0) {
            if (loggingEnabled) {
                NewXLogger.logger("NewX save skip reason=negative-position");
            }
            return;
        }

        try {
            boolean restoreTimelinePosition = SettingsRegistry.getBooleanOrDefault(
                    RESTORE_TIMELINE_POSITION_SETTING,
                    true
            );
            boolean restoreProfilePosition = SettingsRegistry.getBooleanOrDefault(
                    RESTORE_PROFILE_POSITION_SETTING,
                    false
            );
            String timelineName = timeline == null ? null : timeline.name();
            String key = storageKey(
                    timelineName,
                    profileId,
                    restoreTimelinePosition,
                    restoreProfilePosition
            );
            if (key == null) return;

            SharedPreferences preferences = preferences();
            if (preferences == null) return;

            synchronized (SAVE_LOCK) {
                if (preferences == lastSavedPreferences && key.equals(lastSavedKey)
                        && index == lastSavedIndex && offset == lastSavedOffset) {
                    return;
                }
                preferences.edit()
                        .putInt(key + INDEX_SUFFIX, index)
                        .putInt(key + OFFSET_SUFFIX, offset)
                        .apply();
                lastSavedPreferences = preferences;
                lastSavedKey = key;
                lastSavedIndex = index;
                lastSavedOffset = offset;
            }

            if (loggingEnabled) {
                NewXLogger.logger("NewX save timeline=" + timelineName + " key=" + key
                        + " index=" + index + " offset=" + offset);
            }
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to save NewX timeline position", exception);
        }
    }

    /** Returns whether X's process-local position map is valid for this timeline type. */
    public static boolean useInMemoryPosition(Enum<?> timeline) {
        String timelineName = timeline == null ? null : timeline.name();
        boolean useInMemory = timeline != null && isHomeTimeline(timelineName);
        if (NewXLogger.isLoggingEnabled()) {
            NewXLogger.logger("NewX in-memory timeline=" + timelineName + " useInMemory=" + useInMemory);
        }
        return useInMemory;
    }

    static String storageKey(
            @Nullable String timelineName,
            @Nullable String profileId,
            boolean restoreTimelinePosition,
            boolean restoreProfilePosition
    ) {
        if (timelineName == null) return null;
        if (isHomeTimeline(timelineName)) {
            return restoreTimelinePosition ? timelineName : null;
        }
        if (!restoreProfilePosition || !timelineName.startsWith("USER_PROFILE_")) return null;
        if (profileId == null || profileId.trim().isEmpty()) return null;
        return PROFILE_KEY_PREFIX + timelineName + "." + profileId.trim();
    }

    private static boolean isHomeTimeline(String timelineName) {
        return "FOR_YOU".equals(timelineName) || "FOLLOWING".equals(timelineName)
                || "RANKED_FOLLOWING".equals(timelineName);
    }

    @Nullable
    private static SharedPreferences preferences() {
        SharedPreferences cached = cachedPreferences;
        if (cached != null) return cached;

        Context context = Utils.getContext();
        if (context == null) return null;

        synchronized (TimelineScrollPositionStore.class) {
            cached = cachedPreferences;
            if (cached != null) return cached;
            cached = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
            cachedPreferences = cached;
            return cached;
        }
    }
}
