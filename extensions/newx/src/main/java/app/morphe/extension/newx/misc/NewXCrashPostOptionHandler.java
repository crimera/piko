package app.morphe.extension.newx.misc;

import java.util.List;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.shared.Utils;

/** Handles the "Crash app" post-menu option in NewX developer tools. */
public final class NewXCrashPostOptionHandler {
    private static final String OPTION_NAME = NewXPostOptionActions.CRASH_APP_ACTION;
    private static final String CRASH_POST_OPTION_SETTING_ID =
            "newx.advanced.debug_tools.crash_post_option";

    private NewXCrashPostOptionHandler() {
    }

    public static List<?> addOption(List<?> groups) {
        return NewXPostOptions.addOption(groups, OPTION_NAME, isEnabled());
    }

    public static String labelFor(Object action, Object originalLabel) {
        if (isCrashAppAction(action)) return app.morphe.extension.shared.StringRef.str("piko_newx_crash_app_title");
        return originalLabel instanceof String ? (String) originalLabel : null;
    }

    public static boolean usesIcon(Object action) {
        return isCrashAppAction(action);
    }

    public static boolean handleOptionAction(Object presenter, Object action) {
        if (!isCrashAppAction(action)) return false;

        try {
            NewXCrashHandler.testCrash("post menu");
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to crash from NewX post menu", exception);
            Utils.showToastShort(app.morphe.extension.shared.StringRef.str("piko_newx_ui_crash_failed"));
        }
        return true;
    }

    private static boolean isEnabled() {
        return SettingsRegistry.getBooleanOrDefault(CRASH_POST_OPTION_SETTING_ID, false);
    }

    private static boolean isCrashAppAction(Object action) {
        return NewXPostOptions.isAction(action, OPTION_NAME);
    }
}
