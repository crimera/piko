package app.morphe.extension.newx.settings;

import android.app.Activity;

import java.util.List;

import app.morphe.extension.crimera.logging.LogExporter;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

public final class ServerLogExportAction implements SettingsActionHandler {
    private static final LogExporter EXPORTER = new LogExporter(
            "Piko NewX server logs",
            "piko-server-logs.txt",
            "(No server errors were captured.)"
    );

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
            EXPORTER.write(activity.getApplicationContext(), entries);
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
