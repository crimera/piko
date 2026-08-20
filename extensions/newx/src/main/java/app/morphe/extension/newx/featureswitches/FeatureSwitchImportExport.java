package app.morphe.extension.newx.featureswitches;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
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
import app.morphe.extension.newx.settings.SettingsActionHandler;

public final class FeatureSwitchImportExport {
    private static final int IMPORT_REQUEST_CODE = 0x5046;
    private static final int EXPORT_REQUEST_CODE = 0x5056;
    private static final int MAX_IMPORT_BYTES = 1024 * 1024;

    private FeatureSwitchImportExport() {
    }

    public static final class ImportAction implements SettingsActionHandler {
        @Override
        public void run(Activity activity) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            activity.startActivityForResult(intent, IMPORT_REQUEST_CODE);
        }
    }

    public static final class ExportAction implements SettingsActionHandler {
        @Override
        public void run(Activity activity) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, exportFileName());
            activity.startActivityForResult(intent, EXPORT_REQUEST_CODE);
        }
    }

    public static boolean handleActivityResult(
            Activity activity,
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {
        if (requestCode != IMPORT_REQUEST_CODE && requestCode != EXPORT_REQUEST_CODE) return false;
        if (resultCode != Activity.RESULT_OK) return true;

        Uri uri = data == null ? null : data.getData();
        if (uri == null) {
            Utils.showToastShort(str("piko_newx_feature_switch_file_not_selected"));
            return true;
        }

        if (requestCode == IMPORT_REQUEST_CODE) {
            importOverrides(activity, uri);
            return true;
        }

        exportOverrides(activity, uri);
        return true;
    }

    private static void importOverrides(Activity activity, Uri uri) {
        try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Could not open feature switch import");
            FeatureSwitchStore.shared().importOverrides(readUtf8(input));
            Utils.showToastShort(str("piko_newx_feature_switch_import_success"));
        } catch (Exception exception) {
            Logger.printException(() -> "Failed to import NewX feature switches", exception);
            Utils.showToastShort(str("piko_newx_feature_switch_import_failed"));
        }
    }

    private static void exportOverrides(Activity activity, Uri uri) {
        try (OutputStream output = activity.getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IOException("Could not open feature switch export");
            String serialized = new JSONObject(
                    FeatureSwitchStore.shared().exportOverrides()
            ).toString(2);
            output.write(serialized.getBytes(StandardCharsets.UTF_8));
            Utils.showToastShort(str("piko_newx_feature_switch_export_success"));
        } catch (Exception exception) {
            Logger.printException(() -> "Failed to export NewX feature switches", exception);
            Utils.showToastShort(str("piko_newx_feature_switch_export_failed"));
        }
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int totalBytes = 0;
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            totalBytes += bytesRead;
            if (totalBytes > MAX_IMPORT_BYTES) {
                throw new IOException("Feature switch import is too large");
            }
            output.write(buffer, 0, bytesRead);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static String exportFileName() {
        String timestamp = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US).format(new Date());
        return "piko_newx_feature_switches_" + timestamp + ".json";
    }
}
