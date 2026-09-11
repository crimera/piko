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

    private TimelineScrollPositionStore() {
    }

    @Nullable
    public static int[] restore(Enum<?> timeline) {
        return restore(timeline, null);
    }

    @Nullable
    public static int[] restore(Enum<?> timeline, @Nullable String profileId) {
        try {
            String key = storageKey(
                    timeline == null ? null : timeline.name(),
                    profileId,
                    SettingsRegistry.getBooleanOrDefault(
                            RESTORE_TIMELINE_POSITION_SETTING,
                            true
                    ),
                    SettingsRegistry.getBooleanOrDefault(
                            RESTORE_PROFILE_POSITION_SETTING,
                            false
                    )
            );
            if (key == null) return null;

            SharedPreferences preferences = preferences();
            if (preferences == null) return null;

            if (!preferences.contains(key + INDEX_SUFFIX)) return null;

            return new int[]{
                    preferences.getInt(key + INDEX_SUFFIX, 0),
                    preferences.getInt(key + OFFSET_SUFFIX, 0),
            };
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
        if (holder == null) return;

        try {
            String key = storageKey(
                    timeline == null ? null : timeline.name(),
                    profileId,
                    SettingsRegistry.getBooleanOrDefault(
                            RESTORE_TIMELINE_POSITION_SETTING,
                            true
                    ),
                    SettingsRegistry.getBooleanOrDefault(
                            RESTORE_PROFILE_POSITION_SETTING,
                            false
                    )
            );
            if (key == null) return;

            SharedPreferences preferences = preferences();
            if (preferences == null) return;

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
                if (found < 2) return;
            }

            if (index < 0 || offset < 0) return;

            preferences.edit()
                    .putInt(key + INDEX_SUFFIX, index)
                    .putInt(key + OFFSET_SUFFIX, offset)
                    .apply();
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to save NewX timeline position", exception);
        }
    }

    /** Returns whether X's process-local position map is valid for this timeline type. */
    public static boolean useInMemoryPosition(Enum<?> timeline) {
        return timeline != null && isHomeTimeline(timeline.name());
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
        return "FOR_YOU".equals(timelineName) || "FOLLOWING".equals(timelineName);
    }

    @Nullable
    private static SharedPreferences preferences() {
        Context context = Utils.getContext();
        if (context == null) return null;
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
