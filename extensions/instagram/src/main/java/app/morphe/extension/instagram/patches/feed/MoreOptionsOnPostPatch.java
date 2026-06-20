/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.feed;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.os.Build;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.Logger;

import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.entity.UserData;

import com.instagram.common.session.UserSession;

public class MoreOptionsOnPostPatch {

    public static void postMoreOptions(Context context,  UserSession userSession, Object mediaObject, int currentMediaIndex) {
        try {
            MediaData mediaData = new MediaData(mediaObject, userSession);

            InstagramDialogBox dialog = new InstagramDialogBox(context);

            ArrayList<String> options = new ArrayList<>();

            options.add(str("piko_copy_post_description"));
            options.add(str("piko_copy_post_owner_username"));
            options.add(str("piko_copy_post_owner_fullname"));
            options.add(str("piko_download_options"));
            CharSequence[] items = options.toArray(new CharSequence[0]);

            dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    try {
                        // Doing like this because options are dynamic.
                        String selectedOption = options.get(which);
                        String stringToCopy = null;

                        if (selectedOption.equals(str("piko_copy_post_description"))) {
                            stringToCopy = mediaData.getDescriptionText();

                        } else if (selectedOption.equals(str("piko_copy_post_owner_username"))) {
                            UserData userData = mediaData.getUserData();
                            stringToCopy = userData.getUsername();

                        } else if (selectedOption.equals(str("piko_copy_post_owner_fullname"))) {
                            UserData userData = mediaData.getUserData();
                            stringToCopy = userData.getFullname();

                        } else if (selectedOption.equals(str("piko_download_options"))) {
                            DownloadUtils.downloadPost(context, userSession, mediaObject, currentMediaIndex);

                        }
                        if (stringToCopy != null && stringToCopy.length() > 0) {
                            Utils.setClipboard(stringToCopy);
                            Utils.showToastShort(str("piko_copied"));
                        }
                    } catch (Exception e) {
                        Logger.printException(() -> "Error at postMoreOptions addDialogMenuItems", e);
                        Utils.showToastShort(e.getMessage());
                    }
                }
            });

            dialog.setTitle(str("piko_post_options"));
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);

            Dialog dlg = dialog.getDialog();
            dlg.show();


        } catch (Exception e) {
            Logger.printException(() -> "postMoreOptions failure", e);
        }
    }

}