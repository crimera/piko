package app.morphe.extension.newx.misc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.morphe.extension.newx.settings.NewXLogger;

/** Shared reflection support for custom NewX post-menu options. */
public final class NewXPostOptions {
    private NewXPostOptions() {
    }

    public static List<?> addOption(List<?> groups, String optionName, boolean enabled) {
        if (!enabled || groups == null || groups.isEmpty() || optionName == null) return groups;

        try {
            Object action = findAction(groups, optionName);
            if (action == null || containsAction(groups, action)) return groups;

            List<?> groupedOptions = appendToCustomGroup(groups, action);
            if (groupedOptions != null) return groupedOptions;

            Object group = createOptionGroup(groups.get(0), action);
            if (group == null) return groups;

            ArrayList<Object> copy = new ArrayList<>(groups);
            copy.add(group);
            return copy;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to add NewX post-menu option " + optionName, exception);
            return groups;
        }
    }

    private static List<?> appendToCustomGroup(List<?> groups, Object action)
            throws ReflectiveOperationException {
        Object lastGroup = groups.get(groups.size() - 1);
        List<?> customActions = findCustomActionList(lastGroup);
        if (customActions == null) return null;

        ArrayList<Object> combinedActions = new ArrayList<>(customActions);
        combinedActions.add(action);

        Object group = createOptionGroup(lastGroup, combinedActions);
        if (group == null) return null;

        ArrayList<Object> copy = new ArrayList<>(groups);
        copy.set(copy.size() - 1, group);
        return copy;
    }

    private static List<?> findCustomActionList(Object group) throws ReflectiveOperationException {
        for (List<?> actionList : actionLists(group)) {
            for (Object action : actionList) {
                if (isCustomAction(action)) return actionList;
            }
        }
        return null;
    }

    private static boolean isCustomAction(Object action) {
        if (!(action instanceof Enum<?> enumAction)) return false;

        String actionName = enumAction.name();
        return NewXPostOptionActions.BROWSE_OBJECT_ACTION.equals(actionName)
                || NewXPostOptionActions.SHARE_IMAGE_ACTION.equals(actionName)
                || NewXPostOptionActions.FILTERED_REPLIES_ACTION.equals(actionName)
                || NewXPostOptionActions.CRASH_APP_ACTION.equals(actionName);
    }

    public static boolean isAction(Object action, String optionName) {
        return action instanceof Enum<?> && optionName != null && optionName.equals(((Enum<?>) action).name());
    }

    private static Object findAction(List<?> groups, String optionName) throws ReflectiveOperationException {
        for (Object group : groups) {
            for (List<?> actionList : actionLists(group)) {
                for (Object action : actionList) {
                    if (!(action instanceof Enum<?> enumAction)) continue;

                    Object candidate = enumValue(enumAction, optionName);
                    if (candidate != null) return candidate;
                }
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Enum<?> exemplar, String optionName) {
        try {
            return Enum.valueOf((Class) exemplar.getDeclaringClass(), optionName);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean containsAction(List<?> groups, Object action) throws ReflectiveOperationException {
        for (Object group : groups) {
            for (List<?> actionList : actionLists(group)) {
                if (actionList.contains(action)) return true;
            }
        }
        return false;
    }

    private static Object createOptionGroup(Object exemplar, Object action) throws ReflectiveOperationException {
        return createOptionGroup(exemplar, Collections.singletonList(action));
    }

    private static Object createOptionGroup(Object exemplar, List<?> actions)
            throws ReflectiveOperationException {
        for (Constructor<?> constructor : exemplar.getClass().getDeclaredConstructors()) {
            if (constructor.getParameterCount() != 1) continue;
            if (!List.class.isAssignableFrom(constructor.getParameterTypes()[0])) continue;

            constructor.setAccessible(true);
            return constructor.newInstance(actions);
        }
        return null;
    }

    private static List<List<?>> actionLists(Object group) throws ReflectiveOperationException {
        if (group == null) return Collections.emptyList();

        List<List<?>> actionLists = new ArrayList<>();
        for (Class<?> type = group.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType())) continue;

                field.setAccessible(true);
                Object value = field.get(group);
                if (value instanceof List<?> list && containsEnum(list)) actionLists.add(list);
            }
        }
        return actionLists;
    }

    private static boolean containsEnum(List<?> values) {
        for (Object value : values) {
            if (value instanceof Enum<?>) return true;
        }
        return false;
    }
}
