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
    private static final String INDEX_SUFFIX = ".index";
    private static final String OFFSET_SUFFIX = ".offset";
    private static final Pattern POSITION_PATTERN = Pattern.compile(
            "ScrollPositionHolder\\(firstVisibleItemIndex=(\\d+), firstVisibleItemScrollOffset=(\\d+)\\)"
    );

    private TimelineScrollPositionStore() {
    }

    @Nullable
    public static int[] restore(Enum<?> timeline) {
        if (timeline == null || !SettingsRegistry.getBooleanOrDefault(
                RESTORE_TIMELINE_POSITION_SETTING,
                true
        )) {
            return null;
        }

        try {
            SharedPreferences preferences = preferences();
            if (preferences == null) return null;

            String key = timeline.name();
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
        if (timeline == null || holder == null ||
                !SettingsRegistry.getBooleanOrDefault(
                        RESTORE_TIMELINE_POSITION_SETTING,
                        true
                )) return;

        try {
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

            String key = timeline.name();
            preferences.edit()
                    .putInt(key + INDEX_SUFFIX, index)
                    .putInt(key + OFFSET_SUFFIX, offset)
                    .apply();
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to save NewX timeline position", exception);
        }
    }

    @Nullable
    private static SharedPreferences preferences() {
        Context context = Utils.getContext();
        if (context == null) return null;
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
