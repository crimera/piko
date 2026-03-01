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


public class ViewStoryMentionsPatch {

    private static Object getMediaObjectFromReelItem(Object classObject)throws Exception{
        return new Entity(classObject).getField("fieldname");
    }

    public static void viewMentions(Context ctx, Object reelItemObject){
        try {
            Object mediaObject = ViewStoryMentionsPatch.getMediaObjectFromReelItem(reelItemObject);
            HashSet<Object> mentionSet = new MediaData(mediaObject).getMentionSet();
            showCopyDialog(ctx,mentionSet);
        } catch (Exception ex){
            Logger.printException(() -> "Failed viewMentions", ex);
        }
        return;
    }

    private static void showCopyDialog(final Context context,HashSet<Object> mentionSet) throws Exception{
        InstagramDialogBox dialog = new InstagramDialogBox(context);

        if(mentionSet!=null) {
            final Object[] snapshot = mentionSet.toArray();

            // Build dialog items from toString()
            CharSequence[] items = new CharSequence[snapshot.length];
            for (int i = 0; i < snapshot.length; i++) {
                Object data = snapshot[i];
                UserData userData = new UserData(data);
                items[i] = userData.getFullname()+" ( @"+userData.getUsername()+")";
            }

            dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    Object data =snapshot[which];
                    Utils.setClipboard(new UserData(data).getUsername());
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