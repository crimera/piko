package app.morphe.extension.newx.settings;

import android.app.Activity;

import java.util.List;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

public final class ServerLogExportAction implements SettingsActionHandler {
    @Override
    public void run(Activity activity) {
        if (activity == null) return;

        List<String> entries = NewXLogger.snapshotServerLogs();
        Thread exporter = new Thread(
                () -> export(activity, entries),
                "piko-newx-server-log-export"
        );
        exporter.start();
    }

    private static void export(Activity activity, List<String> entries) {
        try {
            ServerLogExporter.write(activity.getApplicationContext(), entries);
            showToast(activity, "piko_newx_server_logs_saved");
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to export NewX server logs", exception);
            showToast(activity, "piko_newx_server_logs_save_failed");
        }
    }

    private static void showToast(Activity activity, String resourceName) {
        activity.runOnUiThread(() -> Utils.showToastShort(StringRef.str(resourceName)));
    }
}
