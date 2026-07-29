package app.morphe.extension.xlite.settings;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.ui.CustomDialog;
import app.morphe.extension.xlite.postfilter.PostFilterRuleStore;

public final class SettingsBackupRestore {
    private static final int BACKUP_REQUEST_CODE = 0x5042;
    private static final int RESTORE_REQUEST_CODE = 0x5052;
    private static final int MAX_BACKUP_BYTES = 1024 * 1024;

    static {
        Setting.addImportExportCallback(new Setting.ImportExportCallback() {
            @Override
            public void settingsImported(@Nullable Activity activity) {
                if (activity == null) return;
                Utils.runOnMainThreadDelayed(() -> promptForRestart(activity), 350);
            }

            @Override
            public void settingsExported(@Nullable Activity activity) {
            }
        });
    }

    private SettingsBackupRestore() {
    }

    public static final class BackupAction implements SettingsActionHandler {
        @Override
        public void run(Activity activity) {
            ensureAllSettingsLoaded();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, backupFileName());
            activity.startActivityForResult(intent, BACKUP_REQUEST_CODE);
        }
    }

    public static final class RestoreAction implements SettingsActionHandler {
        @Override
        public void run(Activity activity) {
            ensureAllSettingsLoaded();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            activity.startActivityForResult(intent, RESTORE_REQUEST_CODE);
        }
    }

    public static boolean handleActivityResult(
            Activity activity,
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {
        if (requestCode != BACKUP_REQUEST_CODE && requestCode != RESTORE_REQUEST_CODE) return false;
        if (resultCode != Activity.RESULT_OK) return true;

        Uri uri = data == null ? null : data.getData();
        if (uri == null) {
            Utils.showToastShort(str("piko_xlite_backup_restore_no_file"));
            return true;
        }

        if (requestCode == BACKUP_REQUEST_CODE) {
            writeBackup(activity, uri);
            return true;
        }

        restoreBackup(activity, uri);
        return true;
    }

    private static void writeBackup(Activity activity, Uri uri) {
        try (OutputStream output = activity.getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IOException("Could not open backup destination");
            output.write(exportValidJson(activity).getBytes(StandardCharsets.UTF_8));
            Utils.showToastShort(str("piko_xlite_backup_success"));
        } catch (Exception exception) {
            Logger.printException(() -> "Failed to back up X-Lite settings", exception);
            Utils.showToastShort(str("piko_xlite_backup_failed"));
        }
    }

    private static void restoreBackup(Activity activity, Uri uri) {
        try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Could not open settings backup");
            String json = readUtf8(input);
            new JSONObject(json);
            Setting.importFromJSON(activity, json);
        } catch (Exception exception) {
            Logger.printException(() -> "Failed to restore X-Lite settings", exception);
            Utils.showToastShort(str("piko_xlite_restore_failed"));
        }
    }

    private static String exportValidJson(Activity activity) throws Exception {
        String exported = Setting.exportToJson(activity).trim();
        if (exported.endsWith(",")) {
            exported = exported.substring(0, exported.length() - 1).trim();
        }
        if (exported.isEmpty()) return new JSONObject().toString(2);
        if (!exported.startsWith("{")) exported = "{" + exported + "}";
        return new JSONObject(exported).toString(2);
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int totalBytes = 0;
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            totalBytes += bytesRead;
            if (totalBytes > MAX_BACKUP_BYTES) throw new IOException("Settings backup is too large");
            output.write(buffer, 0, bytesRead);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static void ensureAllSettingsLoaded() {
        PostFilterRuleStore.shared();
    }

    private static String backupFileName() {
        String timestamp = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US).format(new Date());
        return "piko_xlite_settings_" + timestamp + ".json";
    }

    private static void promptForRestart(Activity activity) {
        Dialog dialog = CustomDialog.create(
                activity,
                str("piko_xlite_restart_title"),
                str("piko_xlite_restart_summary"),
                null,
                str("piko_xlite_restart_now"),
                () -> Utils.restartApp(activity),
                () -> { },
                null,
                null,
                true
        ).first;
        dialog.show();
    }
}
