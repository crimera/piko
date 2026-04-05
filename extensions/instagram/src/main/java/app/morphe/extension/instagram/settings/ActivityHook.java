/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.os.Bundle;
import android.net.Uri;

import app.morphe.extension.instagram.settings.SettingsActivity;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.preference.fragments.BackupPrefActivity;
import app.morphe.extension.instagram.settings.preference.fragments.RestorePrefActivity;
import app.morphe.extension.shared.Logger;

@SuppressWarnings("deprecation")
public class ActivityHook {

    private static void launchActivity(Context context, Intent intent){
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Logger.printException(() -> "launchActivity failure", e);
        }
    }

    private static void launchActivity(Intent intent){
        launchActivity(Utils.getContext(),intent);
    }

    public static void startPikoActivity(){
        Context context = Utils.getContext();
        Intent intent = new Intent(context, SettingsActivity.class);
        launchActivity(context,intent);
    }

    public static void launchFragment(Context ctx, String bundleKey){
        Intent intent = null;
        if (bundleKey.equals(Strings.EXPORT_DEV_OVERRIDES) || bundleKey.equals(Strings.EXPORT_PIKO_PREF)) {
            intent = new Intent(ctx,BackupPrefActivity.class);
        } else if (bundleKey.equals(Strings.IMPORT_DEV_OVERRIDES) || bundleKey.equals(Strings.IMPORT_ID_MAPPING)  || bundleKey.equals(Strings.IMPORT_PIKO_PREF)) {
            intent = new Intent(ctx,RestorePrefActivity.class);
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

}
