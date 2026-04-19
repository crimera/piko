/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.morphe.extension.instagram.patches.userprofile;

import android.content.Context;
import android.app.Dialog;
import android.content.DialogInterface;
import android.view.ViewGroup;
import java.util.ArrayList;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.entity.ProfileInfo;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.crimera.ObjectBrowser;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.entity.InstagramButton;
import app.morphe.extension.instagram.entity.InstagramButtonStyleEnum;

import com.instagram.igds.components.button.IgdsButton;

public class ProfileMoreOption {
    private static boolean DEBUG;

    static {
        DEBUG = Pref.pikoDebug();
    }

    private static void moreOptionsDailogueBox(Context context, UserData userData) {

        try {
            InstagramDialogBox dialog = new InstagramDialogBox(context);

            ArrayList<String> options = new ArrayList<>();
            options.add(Strings.COPY_USERNAME);
            options.add(Strings.COPY_FULL_NAME);
            options.add(Strings.COPY_USER_ID);
            options.add(Strings.COPY_BIO);
            options.add(Strings.DOWNLOAD_PROFILE_PICTURE);
            if (DEBUG) options.add(Strings.PIKO_DEBUG);

            CharSequence[] items = options.toArray(new CharSequence[0]);

            dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    try {
                        // Doing like this because options are dynamic.
                        String selectedOption = options.get(which);
                        boolean toCopy = false;
                        String text = null;

                        if (selectedOption.equals(Strings.COPY_USERNAME)) {
                            text = userData.getUsername();
                            toCopy = true;

                        } else if (selectedOption.equals(Strings.COPY_FULL_NAME)) {
                            text = userData.getFullname();
                            toCopy = true;

                        } else if (selectedOption.equals(Strings.COPY_USER_ID)) {
                            text = userData.getUserId();
                            toCopy = true;

                        } else if (selectedOption.equals(Strings.COPY_BIO)) {
                            text = userData.getBio();
                            toCopy = true;

                        } else if (selectedOption.equals(Strings.DOWNLOAD_PROFILE_PICTURE)) {
                            String url = userData.getProfilePictureUrl();
                            String username = userData.getUsername();
                            String downloadFilename = "dp.jpg";
                            DownloadUtils.downloadFile(context, url, username, downloadFilename);
                            toCopy = false;

                        } else if (selectedOption.equals(Strings.PIKO_DEBUG)) {
                            ObjectBrowser.browseObject(context, userData.getObject());

                        }

                        if (toCopy && text != null && text.length() > 0) {
                            Utils.setClipboard(text);
                            Utils.showToastShort(Strings.COPIED);
                        }
                    } catch (Exception e) {
                        Logger.printException(() -> "Error at moreOptionsDailogueBox onclick", e);
                        Utils.showToastShort(e.getMessage());
                    }
                }
            });
            dialog.setTitle(Strings.MORE_PROFILE_OPTIONS);
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);

            Dialog dlg = dialog.getDialog();
            dlg.show();
        } catch (Exception e) {
            Logger.printException(() -> "Error at moreOptionsDailogueBox", e);
            Utils.showToastShort(e.getMessage());
        }
    }

    public static void addProfileMoreOptionsButton(ViewGroup viewGroup, Object object) {
        try {
            ProfileInfo profileInfo = new ProfileInfo(object);
            UserData userData = profileInfo.getUserData();

            Context context = viewGroup.getContext();
            InstagramButton button = new InstagramButton(context);
            button.setText(Strings.MORE_PROFILE_OPTIONS);
            button.setStyle(InstagramButtonStyleEnum.PRIMARY);
            button.setOnClickListener(() ->
                    moreOptionsDailogueBox(context, userData)
            );

            int marginPx = Utils.dipToPixels(12);
            button.setMargins(marginPx, marginPx, marginPx, marginPx);

            IgdsButton igdsButton = button.getIgdsButton();
            viewGroup.addView(igdsButton);
            igdsButton.bringToFront();
            viewGroup.requestLayout();
            viewGroup.invalidate();
        } catch (Exception e) {
            Logger.printException(() -> "Failed to add profile more button: ", e);
        }

    }

}

