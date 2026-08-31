/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.download;

import static app.morphe.extension.instagram.utils.IgStr.str;

import androidx.fragment.app.Fragment;

import java.io.File;

import android.content.Context;
import android.app.Dialog;
import android.content.DialogInterface;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.utils.InstaUtils;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.constants.Constants;

public class DownloadMapping {

    private static final Context context = Utils.getContext();
    private static final String mappingsPath = context.getFilesDir() + "/mobileconfig/id_name_mapping.json";

    private static void getMappingsDialogBox(Context context) {
        InstagramDialogBox dialog = new InstagramDialogBox(context);

        dialog.setNegativeButton(str("piko_cancel"),null);

        dialog.setPositiveButton(str("piko_download_id_mapping"), new DialogInterface.OnClickListener() {
            public final void onClick(DialogInterface dialogInterface, int i) {
                DownloadMapping.downloadMapping();
            }
        });


        dialog.setTitle(str("piko_missing_mapping_file"));
        dialog.setMessage(str("piko_missing_mapping_file_desc"));
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
        String host = Constants.PIKO_MAPPINGS_PATH;
        String appVersionName = Utils.getAppVersionName();
        String mappings_filename = appVersionName + ".json";
        File destinationFile = new File(DownloadMapping.mappingsPath);
        InstaUtils.downloadFile(host, mappings_filename, destinationFile, ()->Utils.restartApp(context));
    }

}
