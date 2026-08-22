package app.morphe.extension.newx.timeline;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
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
                false
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
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to restore NewX timeline position", exception);
            return null;
        }
    }

    public static void save(Enum<?> timeline, Object holder) {
        if (timeline == null || holder == null ||
                !SettingsRegistry.getBooleanOrDefault(
                        RESTORE_TIMELINE_POSITION_SETTING,
                        false
                )) return;

        try {
            SharedPreferences preferences = preferences();
            if (preferences == null) return;

            Matcher matcher = POSITION_PATTERN.matcher(holder.toString());
            if (!matcher.matches()) return;

            int index = Integer.parseInt(matcher.group(1));
            int offset = Integer.parseInt(matcher.group(2));
            String key = timeline.name();
            preferences.edit()
                    .putInt(key + INDEX_SUFFIX, index)
                    .putInt(key + OFFSET_SUFFIX, offset)
                    .commit();
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to save NewX timeline position", exception);
        }
    }

    @Nullable
    private static SharedPreferences preferences() {
        Context context = Utils.getContext();
        if (context == null) return null;
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
