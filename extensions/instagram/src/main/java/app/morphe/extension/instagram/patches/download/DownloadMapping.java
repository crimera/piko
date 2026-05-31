/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.download;

import android.os.Handler;
import android.os.Looper;
import androidx.fragment.app.Fragment;

import java.net.HttpURLConnection;
import java.io.File;

import android.content.Context;
import android.app.Dialog;
import android.content.DialogInterface;

import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.shared.requests.Route;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.constants.UI;
import static app.morphe.extension.shared.requests.Route.Method.GET;

public class DownloadMapping {

    private static final Context context = Utils.getContext();
    private static final String mappingsPath = context.getFilesDir() + "/mobileconfig/id_name_mapping.json";

    private static void getMappingsDialogBox(Context context) {
        InstagramDialogBox dialog = new InstagramDialogBox(context);

        dialog.setNegativeButton(Strings.CANCEL,null);

        dialog.setPositiveButton(Strings.DOWNLOAD_ID_MAPPING, new DialogInterface.OnClickListener() {
            public final void onClick(DialogInterface dialogInterface, int i) {
                DownloadMapping.downloadMapping();
            }
        });


        dialog.setTitle(Strings.MISSING_MAPPING_FILE);
        dialog.setMessage(Strings.MISSING_MAPPING_FILE_DESC);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        Dialog dlg = dialog.getDialog();
        dlg.show();
    }

    private static boolean isMappingFileExists() {
        try {
            File mappingFile = new File(DownloadMapping.mappingsPath);
            if (mappingFile != null && mappingFile.exists() && mappingFile.isFile()) {
                long fileSizeInBytes = mappingFile.length();
                long limitInBytes = 10 * 1024; // 10 KB

                // Sometimes the app creates a dummy mappings file.
                // So checking the file size.
                return fileSizeInBytes > limitInBytes;
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
            PikoUtils.toast(e.getMessage());
        }
        return false;

    }

    public static void checkMappings(Fragment fragment) {
        try {
            if (!DownloadMapping.isMappingFileExists()) {
                Context context = fragment.requireContext();
                DownloadMapping.getMappingsDialogBox(context);
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }

    public static void downloadMapping() {
        Context context = Utils.getContext();

        if (!Utils.isNetworkConnected()) {
            PikoUtils.toast(Strings.NO_INTERNET);
            return;
        }
        Utils.runOnBackgroundThread(() -> {
            String appVersionName = Utils.getAppVersionName();
            String mappings_filename = appVersionName + ".json";

            try {
                String url = "https://github.com/crimera/piko/raw/refs/heads/dev/docs/mappings/";

                Route route = new Route(GET, mappings_filename);
                HttpURLConnection connection = Requester.getConnectionFromRoute(url, route);
                String response = Requester.parseString(connection);

                File destinationFile = new File(DownloadMapping.mappingsPath);
                PikoUtils.writeFile(destinationFile, response.getBytes(), false);

                new Handler(Looper.getMainLooper()).post(() -> {
                    PikoUtils.toast(Strings.DOWNLOADED_MEDIA + mappings_filename);
                    Utils.restartApp(context);
                });

            } catch (Exception e) {
                PikoUtils.logger(e);
                PikoUtils.toast(Strings.DOWNLOAD_FAILED_MEDIA + mappings_filename);
            }

        });

    }

}
