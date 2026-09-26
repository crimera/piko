package app.morphe.extension.newx.settings;

import android.app.Activity;

import app.morphe.extension.newx.misc.NewXCrashHandler;

/** Developer-tools action that crashes the app to exercise crash logging. */
public final class NewXCrashSettingsAction implements SettingsActionHandler {
    @Override
    public void run(Activity activity) {
        NewXCrashHandler.testCrash("settings");
    }
}
