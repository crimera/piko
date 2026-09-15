package app.morphe.extension.newx.misc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import app.morphe.extension.newx.settings.NewXLogger;

/** Removes hidden actions from NewX post-menu option groups. */
public final class PostOptionsFilter {
    private PostOptionsFilter() {
    }

    public static List<?> filter(List<?> groups, Set<String> hiddenItemIds) {
        if (groups == null || groups.isEmpty() || hiddenItemIds == null || hiddenItemIds.isEmpty()) {
            return groups;
        }

        try {
            List<Object> filteredGroups = new ArrayList<>(groups.size());
            boolean changed = false;
            for (Object group : groups) {
                Object filteredGroup = filterGroup(group, hiddenItemIds);
                if (filteredGroup == null) {
                    changed = true;
                    continue;
                }
                if (filteredGroup != group) changed = true;
                filteredGroups.add(filteredGroup);
            }
            return changed ? filteredGroups : groups;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to customize NewX post menu", exception);
            return groups;
        }
    }

    private static Object filterGroup(Object group, Set<String> hiddenItemIds)
            throws ReflectiveOperationException {
        List<List<?>> actionLists = actionLists(group);
        if (actionLists.isEmpty()) return group;

        if (actionLists.size() == 1) {
            List<?> filtered = filterActionList(actionLists.get(0), hiddenItemIds);
            if (filtered.size() == actionLists.get(0).size()) return group;
            if (filtered.isEmpty()) return null;
            Object rebuilt = createOptionGroup(group, filtered);
            return rebuilt != null ? rebuilt : group;
        }

        boolean changed = false;
        boolean allEmpty = true;
        for (List<?> actions : actionLists) {
            List<?> filtered = filterActionList(actions, hiddenItemIds);
            if (filtered.size() != actions.size()) changed = true;
            if (!filtered.isEmpty()) allEmpty = false;
        }
        if (allEmpty) return null;
        if (!changed) return group;

        for (Field field : declaredFields(group)) {
            if (!List.class.isAssignableFrom(field.getType())) continue;
            Object value = field.get(group);
            if (!(value instanceof List<?> list) || !containsEnum(list)) continue;
            List<?> filtered = filterActionList(list, hiddenItemIds);
            if (filtered.size() == list.size()) continue;
            field.set(group, new ArrayList<>(filtered));
            changed = true;
        }
        return group;
    }

    private static List<?> filterActionList(List<?> actions, Set<String> hiddenItemIds) {
        if (actions.isEmpty()) return actions;

        List<Object> filtered = new ArrayList<>(actions.size());
        for (Object action : actions) {
            if (!shouldHide(action, hiddenItemIds)) filtered.add(action);
        }
        return filtered.size() == actions.size() ? actions : filtered;
    }

    private static boolean shouldHide(Object action, Set<String> hiddenItemIds) {
        if (!(action instanceof Enum<?> enumAction)) return false;

        String actionName = enumAction.name();
        if (hiddenItemIds.contains(actionName)) return true;
        return switch (actionName) {
            case "Unfollow" -> hiddenItemIds.contains("Follow");
            case "Unmute" -> hiddenItemIds.contains("Mute");
            case "Unblock" -> hiddenItemIds.contains("Block");
            case "ReportDsa" -> hiddenItemIds.contains("Report");
            case "TwitterShare", "PromotedShareVia" -> hiddenItemIds.contains("Share");
            case "RemoveFromBookmarks" -> hiddenItemIds.contains("AddToBookmarks");
            case "SeeFewer", "NotRelevant", "NotCredible", "NotAboutTopic" ->
                    hiddenItemIds.contains("IDontLikeThisTweet");
            case "UnmuteConversation" -> hiddenItemIds.contains("MuteConversation");
            case "Unpin", "PinReply", "UnpinReply" -> hiddenItemIds.contains("Pin");
            case "EditUnavailable", "EditWithTwitterBlue" -> hiddenItemIds.contains("Edit");
            case "UndoRetweet" -> hiddenItemIds.contains("Retweet");
            case "Unfavorite" -> hiddenItemIds.contains("Favorite");
            case "UndoDislike" -> hiddenItemIds.contains("Dislike");
            case "ContributeToBirdwatch" -> hiddenItemIds.contains("RequestCommunityNote");
            case "BoostPostAgain" -> hiddenItemIds.contains("BoostPost");
            case "AddHighlight", "RemoveHighlight" -> hiddenItemIds.contains("ToggleHighlight");
            default -> false;
        };
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

    private static List<Field> declaredFields(Object group) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = group.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                fields.add(field);
            }
        }
        return fields;
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

    private static boolean containsEnum(List<?> values) {
        for (Object value : values) {
            if (value instanceof Enum<?>) return true;
        }
        return false;
    }
}
