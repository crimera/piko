/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.settings;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.settings.preference.fragments.BackupPrefActivity;
import app.morphe.extension.instagram.settings.preference.fragments.RestorePrefActivity;
import app.morphe.extension.crimera.downloader.FolderPickerActivity;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.constants.Constants;

@SuppressWarnings("deprecation")
public class ActivityHook {

    private static void launchActivity(Context context, Intent intent){
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Logger.printException(() -> "launchActivity failure", e);
            PikoUtils.logger(e);
        }
    }

    private static void launchActivity(Intent intent){
        launchActivity(PikoUtils.getContext(),intent);
    }

    public static void startPikoActivity(String fragment_name,String title){
        Context context = PikoUtils.getContext();
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(Constants.PIKO_FRAGMENT_NAME, fragment_name);
        intent.putExtra(Constants.PIKO_FRAGMENT_TITLE, title);
        launchActivity(context,intent);
    }

    public static void launchFragment(Context ctx, String bundleKey){
        Intent intent = null;
        if (bundleKey.equals("piko_export_dev_overrides") || bundleKey.equals("piko_export_pref")) {
            intent = new Intent(ctx,BackupPrefActivity.class);
        } else if (bundleKey.equals("piko_import_dev_overrides") || bundleKey.equals("piko_import_id_mapping")  || bundleKey.equals("piko_import_pref")) {
            intent = new Intent(ctx,RestorePrefActivity.class);
        } else if (bundleKey.equals("piko_download_set_path")) {
            intent = new Intent(ctx,FolderPickerActivity.class);
        }
        if(intent!=null){
            intent.putExtra(bundleKey,true);
            launchActivity(ctx,intent);
        }
    }

    public static void handleUrlIntent(Boolean isVideo,String mediaUrl) {
        String dataType = "image/*";
        String exportHeaderString = str("piko_open_image_with");
        if(isVideo){
            dataType = "video/*";
            exportHeaderString = str("piko_open_video_with");
        }

        Uri uri = Uri.parse(mediaUrl);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, dataType);
        Intent chooserIntent = Intent.createChooser(intent, exportHeaderString);
        launchActivity(chooserIntent);
    }

    public static void openLink(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        launchActivity(intent);
    }

    public static void openLink(String url, String packageName) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, url);
        intent.setPackage(packageName);
        launchActivity(intent);
    }


}
