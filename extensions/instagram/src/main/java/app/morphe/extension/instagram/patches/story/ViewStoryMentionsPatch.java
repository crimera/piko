/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches.story;


import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.ActivityHook;


public class ViewStoryMentionsPatch {

    public static void viewMentions(Context ctx, Object mediaObject){
        try {
            HashSet<UserData> mentionSet = new MediaData(mediaObject).getMentionSet();
            showCopyDialog(ctx,mentionSet);
        } catch (Exception ex){
            Logger.printException(() -> "Failed viewMentions", ex);
        }
        return;
    }

    private static void showCopyDialog(final Context context,HashSet<UserData> mentionSet) throws Exception{
        InstagramDialogBox dialog = new InstagramDialogBox(context);

        if(mentionSet!=null) {
            final Object[] snapshot = mentionSet.toArray();

            // Build dialog items from toString()
            CharSequence[] items = new CharSequence[snapshot.length];

            for (int i = 0; i < snapshot.length; i++) {
                UserData userData = (UserData) snapshot[i];
                items[i] = userData.getFullname()+" ( @"+userData.getUsername()+")";
            }

            dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    UserData userData = (UserData) snapshot[which];
                    String username = userData.getUsername();
                    ActivityHook.openLink("instagram://user?username="+username);
                }
            });
        }else{
            dialog.setMessage(Strings.VSM_NO_MENTIONS);
        }

        dialog.setTitle(Strings.VSM_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Dialog dlg = dialog.getDialog();
        dlg.show();
    }
}