/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.preference.fragments.BackupPrefActivity;
import app.morphe.extension.instagram.settings.preference.fragments.RestorePrefActivity;
import app.morphe.extension.crimera.downloader.FolderPickerActivity;
import app.morphe.extension.shared.Logger;

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

    public static void startPikoActivity(){
        Context context = PikoUtils.getContext();
        Intent intent = new Intent(context, SettingsActivity.class);
        launchActivity(context,intent);
    }

    public static void launchFragment(Context ctx, String bundleKey){
        Intent intent = null;
        if (bundleKey.equals(Strings.EXPORT_DEV_OVERRIDES) || bundleKey.equals(Strings.EXPORT_PIKO_PREF)) {
            intent = new Intent(ctx,BackupPrefActivity.class);
        } else if (bundleKey.equals(Strings.IMPORT_DEV_OVERRIDES) || bundleKey.equals(Strings.IMPORT_ID_MAPPING)  || bundleKey.equals(Strings.IMPORT_PIKO_PREF)) {
            intent = new Intent(ctx,RestorePrefActivity.class);
        } else if (bundleKey.equals(Strings.DOWNLOAD_SET_PATH)) {
            intent = new Intent(ctx,FolderPickerActivity.class);
        }
        if(intent!=null){
            intent.putExtra(bundleKey,true);
            launchActivity(ctx,intent);
        }
    }

    public static void handleUrlIntent(Boolean isVideo,String mediaUrl) {
        String dataType = "image/*";
        String exportHeaderString = Strings.OPEN_IMAGE_WITH;
        if(isVideo){
            dataType = "video/*";
            exportHeaderString = Strings.OPEN_VIDEO_WITH;
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
         try {
            launchActivity(intent);
        } catch (Exception e) {
            PikoUtils.toast(Strings.EXTERNAL_DOWNLOADER_PACKAGE_NAME_NOT_FOUND);
            Logger.printException(() -> "openLink failure", e);
        }
    }


}
