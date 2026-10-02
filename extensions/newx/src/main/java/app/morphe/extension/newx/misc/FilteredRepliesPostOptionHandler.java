package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.content.Context;

import java.util.List;

import app.morphe.extension.newx.filteredreplies.FilteredRepliesDialog;
import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.newx.utils.NewXUtils;
import app.morphe.extension.shared.Utils;

/**
 * Handles the "Filtered replies" post-menu option in NewX.
 */
public final class FilteredRepliesPostOptionHandler {
    private static final String OPTION_NAME = NewXPostOptionActions.FILTERED_REPLIES_ACTION;
    private static final String THREAD_FILTER_SETTING_ID =
            "newx.content.verified_account_filtering.thread";
    private static final String FILTERED_REPLIES_MENU_SETTING_ID =
            "newx.content.verified_account_filtering.filtered_replies_menu";
    private static final String URT_POST_CLASS = "com.x.models.timelines.items.UrtTimelinePost";
    private static final String OPTION_LABEL = "Filtered replies";

    private FilteredRepliesPostOptionHandler() {
    }

    public static List<?> addOption(List<?> groups) {
        return NewXPostOptions.addOption(groups, OPTION_NAME, isEnabled());
    }

    public static String labelFor(Object action, Object originalLabel) {
        if (isFilteredRepliesAction(action)) return OPTION_LABEL;
        return originalLabel instanceof String ? (String) originalLabel : null;
    }

    public static boolean usesIcon(Object action) {
        return isFilteredRepliesAction(action);
    }

    public static boolean handleOptionAction(Object presenter, Object action) {
        if (!isFilteredRepliesAction(action)) return false;

        try {
            NewXUtils.PresenterData presenterData = NewXUtils.findPresenterData(presenter, URT_POST_CLASS);
            Context context = presenterData.getContext();
            Object post = presenterData.getValue();
            if (context == null || post == null) {
                Utils.showToastShort("Could not find the selected post");
                return true;
            }

            Activity activity = NewXUtils.findUsableActivity(context);
            if (activity == null) {
                Utils.showToastShort("Could not find the active screen");
                return true;
            }

            String postId = NewXUtils.identifierToString(NewXUtils.invoke(post, "getId"));
            if (postId == null || postId.isEmpty()) {
                Utils.showToastShort("Could not identify the selected post");
                return true;
            }

            NewXUtils.runOnUiThread(() -> FilteredRepliesDialog.show(activity, postId));
            return true;
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to handle filtered replies option", exception);
            Utils.showToastShort("Could not open filtered replies");
            return true;
        }
    }

    private static boolean isEnabled() {
        return SettingsRegistry.getBooleanOrDefault(THREAD_FILTER_SETTING_ID, false)
                && SettingsRegistry.getBooleanOrDefault(FILTERED_REPLIES_MENU_SETTING_ID, true);
    }

    private static boolean isFilteredRepliesAction(Object action) {
        return NewXPostOptions.isAction(action, OPTION_NAME);
    }
}
