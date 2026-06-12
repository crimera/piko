/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches.story;

import static app.morphe.extension.instagram.utils.IgStr.str;

import java.util.HashSet;
import android.app.Dialog;
import android.content.Context;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.MediaData;


public class ViewStoryMentionsPatch {

    public static void viewMentions(Context ctx, Object mediaObject){
        try {
            HashSet<UserData> mentionSet = new MediaData(mediaObject).getMentionSet();
            showCopyDialog(ctx,mentionSet);
        } catch (Exception ex){
            Logger.printException(() -> "Failed viewMentions", ex);
        }
    }

    private static void showCopyDialog(final Context context,HashSet<UserData> mentionSet) {
        InstagramDialogBox dialog = new InstagramDialogBox(context);

        if(mentionSet!=null) {
            final Object[] snapshot = mentionSet.toArray();

            // Build dialog items from toString()
            CharSequence[] items = new CharSequence[snapshot.length];

            for (int i = 0; i < snapshot.length; i++) {
                UserData userData = (UserData) snapshot[i];
                items[i] = userData.getFullname()+" ( @"+userData.getUsername()+")";
            }

            dialog.addDialogMenuItems(items, (d, which) -> {
                UserData userData = (UserData) snapshot[which];
                String username = userData.getUsername();
                PikoUtils.openUrl("instagram://user?username="+username);
            });
        }else{
            dialog.setMessage(str("piko_vsm_no_mentions"));
        }

        dialog.setTitle(str("piko_vsm_title"));
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Dialog dlg = dialog.getDialog();
        dlg.show();
    }
}