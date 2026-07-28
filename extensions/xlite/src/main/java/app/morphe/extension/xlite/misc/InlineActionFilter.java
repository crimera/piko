package app.morphe.extension.xlite.misc;

import com.x.models.InlineActionEntry;
import com.x.models.PostActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import app.morphe.extension.shared.Logger;

public final class InlineActionFilter {
    private InlineActionFilter() {
    }

    public static List<?> filter(List<?> actions, Set<String> hiddenActionIds) {
        if (actions == null) return null;

        List<?> filtered = filterHiddenActions(actions, hiddenActionIds);
        return InlineDownloadButton.addAction(filtered);
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
            Logger.printException(() -> "Failed to customize X-Lite inline actions", exception);
            return actions;
        }
    }

    private static boolean shouldHide(Object action, Set<String> hiddenActionIds) {
        if (action == null) return false;
        if (!(action instanceof InlineActionEntry entry)) {
            return hiddenActionIds.contains(action.toString());
        }

        PostActionType actionType = entry.getActionType();
        if (actionType == null) return false;
        String actionName = actionType.toString();
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
}
