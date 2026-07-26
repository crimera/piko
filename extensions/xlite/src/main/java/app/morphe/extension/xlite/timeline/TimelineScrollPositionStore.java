package app.morphe.extension.xlite.timeline;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.xlite.api.XLiteSettings.Keys;
import app.morphe.extension.xlite.settings.SettingsRegistry;

public final class TimelineScrollPositionStore {
    private static final String PREFERENCES_NAME = "piko_xlite_timeline_positions";
    private static final String INDEX_SUFFIX = ".index";
    private static final String OFFSET_SUFFIX = ".offset";
    private static final Pattern POSITION_PATTERN = Pattern.compile(
            "ScrollPositionHolder\\(firstVisibleItemIndex=(\\d+), firstVisibleItemScrollOffset=(\\d+)\\)"
    );

    private TimelineScrollPositionStore() {
    }

    public static void restore(Object store) {
        if (!SettingsRegistry.getBoolean(Keys.RESTORE_TIMELINE_POSITION)) return;

        try {
            SharedPreferences preferences = preferences();
            if (preferences == null) return;

            Field mapField = findMapField(store.getClass());
            Method getter = findGetter(store.getClass());
            Class<?> timelineType = getter.getParameterTypes()[0];
            Class<?> holderType = getter.getReturnType();
            Constructor<?> holderConstructor = holderType.getDeclaredConstructor(int.class, int.class);
            holderConstructor.setAccessible(true);
            mapField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<Object, Object> positions = (Map<Object, Object>) mapField.get(store);
            Object[] timelineTypes = timelineType.getEnumConstants();
            if (timelineTypes == null) return;

            for (Object value : timelineTypes) {
                Enum<?> timeline = (Enum<?>) value;
                String key = timeline.name();
                if (!preferences.contains(key + INDEX_SUFFIX)) continue;

                int index = preferences.getInt(key + INDEX_SUFFIX, 0);
                int offset = preferences.getInt(key + OFFSET_SUFFIX, 0);
                positions.put(timeline, holderConstructor.newInstance(index, offset));
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Logger.printException(() -> "Failed to restore X-Lite timeline position", exception);
        }
    }

    public static void save(Enum<?> timeline, Object holder) {
        if (!SettingsRegistry.getBoolean(Keys.RESTORE_TIMELINE_POSITION)) return;

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
            Logger.printException(() -> "Failed to save X-Lite timeline position", exception);
        }
    }

    @Nullable
    private static SharedPreferences preferences() {
        Context context = Utils.getContext();
        if (context == null) return null;
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private static Field findMapField(Class<?> storeType) throws NoSuchFieldException {
        for (Field field : storeType.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType()) && !Modifier.isStatic(field.getModifiers())) {
                return field;
            }
        }
        throw new NoSuchFieldException("Timeline position map");
    }

    private static Method findGetter(Class<?> storeType) throws NoSuchMethodException {
        for (Method method : storeType.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 1 || !parameters[0].isEnum()) continue;
            if (method.getReturnType() == void.class) continue;
            return method;
        }
        throw new NoSuchMethodException("Timeline position getter");
    }
}
