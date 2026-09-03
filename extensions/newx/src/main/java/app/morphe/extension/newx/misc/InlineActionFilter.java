package app.morphe.extension.newx.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import app.morphe.extension.newx.settings.NewXLogger;

public final class InlineActionFilter {
    private static final ThreadLocal<Set<String>> HIDDEN_ACTION_IDS = new ThreadLocal<>();
    private static final ThreadLocal<Object> PRESENTER = new ThreadLocal<>();

    private InlineActionFilter() {
    }

    public static void prepareHiddenActions(Set<String> hiddenActionIds) {
        HIDDEN_ACTION_IDS.set(hiddenActionIds);
    }

    public static void preparePresenter(Object presenter) {
        PRESENTER.set(presenter);
    }

    public static List<?> filter(List<?> actions) {
        Set<String> hiddenActionIds = HIDDEN_ACTION_IDS.get();
        Object presenter = PRESENTER.get();
        try {
            return filter(actions, hiddenActionIds, presenter);
        } finally {
            HIDDEN_ACTION_IDS.remove();
            PRESENTER.remove();
        }
    }

    public static List<?> filter(
            List<?> actions,
            Set<String> hiddenActionIds,
            Object presenter
    ) {
        if (actions == null) return null;

        List<?> filtered = filterHiddenActions(actions, hiddenActionIds);
        return InlineDownloadButton.addAction(filtered, presenter);
    }

    private static List<?> filterHiddenActions(List<?> actions, Set<String> hiddenActionIds) {
        if (hiddenActionIds == null || hiddenActionIds.isEmpty()) return actions;

        try {
            List<Object> filtered = new ArrayList<>(actions.size());
            for (Object action : actions) {
                if (!shouldHide(action, hiddenActionIds)) filtered.add(action);
            }
            return filtered.size() == actions.size() ? actions : filtered;
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to customize NewX inline actions", exception);
            return actions;
        }
    }

    private static boolean shouldHide(Object action, Set<String> hiddenActionIds) {
        if (action == null) return false;

        String actionName = inlineActionName(action);
        if (actionName == null) return false;
        if (hiddenActionIds.contains(actionName)) return true;
        return switch (actionName) {
            case "Unfavorite" -> hiddenActionIds.contains("Favorite");
            case "UndoRetweet" -> hiddenActionIds.contains("Retweet");
            case "Share" -> hiddenActionIds.contains("TwitterShare");
            case "AddToBookmarks", "RemoveFromBookmarks" ->
                    hiddenActionIds.contains("AddRemoveBookmarks");
            default -> false;
        };
    }

    private static String getActionName(Object action) {
        return null;
    }

    private static String inlineActionName(Object action) {
        String directName = null;
        try {
            directName = getActionName(action);
        } catch (RuntimeException ignored) {
            // Unknown native action shapes retain the legacy parser fallback.
        }
        if (directName != null) return directName;

        String value = action.toString();
        String prefix = "InlineActionEntry(actionType=";
        if (!value.startsWith(prefix)) return value;

        int end = value.indexOf(',', prefix.length());
        if (end < 0) end = value.indexOf(')', prefix.length());
        if (end < 0) return null;
        return value.substring(prefix.length(), end);
    }
}
