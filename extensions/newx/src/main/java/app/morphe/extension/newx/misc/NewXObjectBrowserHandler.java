package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.content.Context;

import java.util.List;

import app.morphe.extension.crimera.ObjectBrowser;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.newx.utils.NewXUtils;

/** Adds a "Browse Tweet Object" debug action to NewX post menus. */
public final class NewXObjectBrowserHandler {
    private static final String OPTION_NAME = NewXPostOptionActions.BROWSE_OBJECT_ACTION;
    private static final String SETTING_ID = "newx.content.browse_tweet_object";
    private static final String URT_POST_CLASS = "com.x.models.timelines.items.UrtTimelinePost";
    private static final String OPTION_LABEL = "Browse Tweet Object";

    private NewXObjectBrowserHandler() {
    }

    public static List<?> addOption(List<?> groups) {
        return NewXPostOptions.addOption(groups, OPTION_NAME, isEnabled());
    }

    public static String labelFor(Object action, Object originalLabel) {
        if (isBrowseObjectAction(action)) return OPTION_LABEL;
        return originalLabel instanceof String ? (String) originalLabel : null;
    }

    public static boolean usesIcon(Object action) {
        return isBrowseObjectAction(action);
    }

    public static boolean handleOptionAction(Object presenter, Object action) {
        if (!isBrowseObjectAction(action)) return false;

        try {
            NewXUtils.PresenterData presenterData = NewXUtils.findPresenterData(presenter, URT_POST_CLASS);
            Context context = presenterData.getContext();
            Object post = presenterData.getValue();
            if (context == null || post == null) return fail("Could not find the selected post");

            Activity activity = NewXUtils.findUsableActivity(context);
            if (activity == null) return fail("Could not find the active screen");

            NewXUtils.runOnUiThread(() -> ObjectBrowser.browseObject(activity, post));
            return true;
        } catch (IllegalAccessException | RuntimeException exception) {
            return fail("Could not find the selected post");
        }
    }

    private static boolean fail(String message) {
        Utils.showToastShort(message);
        return true;
    }

    private static boolean isEnabled() {
        return SettingsRegistry.getBooleanOrDefault(SETTING_ID, false);
    }

    private static boolean isBrowseObjectAction(Object action) {
        return NewXPostOptions.isAction(action, OPTION_NAME);
    }
}
