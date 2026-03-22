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

import app.morphe.extension.instagram.settings.SettingsActivity;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.preference.fragments.BackupPrefActivity;
import app.morphe.extension.instagram.settings.preference.fragments.RestorePrefActivity;

@SuppressWarnings("deprecation")
public class ActivityHook {

    public static void startPikoActivity() throws Exception {
        Context context = Utils.getContext();
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void callFragment(Context ctx, String bundleKey){
        Intent intent = null;
        if (bundleKey.equals(Strings.EXPORT_DEV_OVERRIDES)) {
            intent = new Intent(ctx,BackupPrefActivity.class);
        } else if (bundleKey.equals(Strings.IMPORT_DEV_OVERRIDES) || bundleKey.equals(Strings.IMPORT_ID_MAPPING)) {
            intent = new Intent(ctx,RestorePrefActivity.class);
        }
        if(intent!=null){
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(bundleKey,true);
            ctx.startActivity(intent);
        }
    }

}
