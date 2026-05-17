/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.feed;

import android.os.Build;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import app.morphe.extension.shared.Utils;

import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.crimera.ObjectBrowser;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.entity.UserData;

public class MoreOptionsOnPostPatch {
    private static boolean ENABLE_MORE_OPTION;
    private static boolean DEBUG;

    static {
        ENABLE_MORE_OPTION = Pref.moreOptionsOnPost() && SettingsStatus.moreOptionsOnPost;
        DEBUG = Pref.pikoDebug();
    }

    private static String contextFieldName() {
        return "A03";
    }

    private static String mediaFieldName() {
        return "A04";
    }

    private static String currentMediaIndexFieldName() {
        return "A01";
    }

    public static void postOnLongPress(Object postObject) {
        try {
            if (ENABLE_MORE_OPTION) {
                Entity entity = new Entity(postObject);
                Context context = (Context) entity.getField(contextFieldName());
                Object mediaObject = entity.getField(mediaFieldName());
                int currentMediaIndex = (int) entity.getField(currentMediaIndexFieldName());
                MediaData mediaData = new MediaData(mediaObject);

                InstagramDialogBox dialog = new InstagramDialogBox(context);

                ArrayList<String> options = new ArrayList<>();

                options.add(Strings.COPY_POST_DESCRIPTION);
                options.add(Strings.COPY_POST_OWNER_USERNAME);
                options.add(Strings.COPY_POST_OWNER_FULLNAME);
                options.add(Strings.DOWNLOAD_OPTIONS);
                if (DEBUG) options.add(Strings.PIKO_DEBUG);
                CharSequence[] items = options.toArray(new CharSequence[0]);

                dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        try {
                            // Doing like this because options are dynamic.
                            String selectedOption = options.get(which);
                            String stringToCopy = null;

                            if (selectedOption.equals(Strings.COPY_POST_DESCRIPTION)) {
                                stringToCopy = mediaData.getDescriptionText();

                            } else if (selectedOption.equals(Strings.COPY_POST_OWNER_USERNAME)) {
                                UserData userData = mediaData.getUserData();
                                stringToCopy = userData.getUsername();

                            } else if (selectedOption.equals(Strings.COPY_POST_OWNER_FULLNAME)) {
                                UserData userData = mediaData.getUserData();
                                stringToCopy = userData.getFullname();

                            } else if (selectedOption.equals(Strings.DOWNLOAD_OPTIONS)) {
                                DownloadUtils.downloadPost(context, mediaObject, currentMediaIndex);

                            } else if (selectedOption.equals(Strings.PIKO_DEBUG)) {
                                ObjectBrowser.browseObject(context, mediaData);

                            }
                            if (stringToCopy != null && stringToCopy.length() > 0) {
                                Utils.setClipboard(stringToCopy);
                                Utils.showToastShort(Strings.COPIED);
                            }
                        } catch (Exception e) {
                            Logger.printException(() -> "Error at postOnLongPress addDialogMenuItems", e);
                            Utils.showToastShort(e.getMessage());
                        }
                    }
                });

                dialog.setTitle(Strings.POST_OPTIONS);
                dialog.setCancelable(true);
                dialog.setCanceledOnTouchOutside(true);

                Dialog dlg = dialog.getDialog();
                dlg.show();
            }

        } catch (Exception e) {
            Logger.printException(() -> "postOnLongPress failure", e);
        }
    }

}