/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.userprofile;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.ProfileInfo;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.crimera.ObjectBrowser;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.instagram.entity.InstagramBottomSheet;
import app.morphe.extension.instagram.entity.InstagramButton;
import app.morphe.extension.instagram.entity.InstagramButtonStyleEnum;
import app.morphe.extension.shared.ui.Dim;

import com.instagram.igds.components.button.IgdsButton;

public class ProfileMoreOption {
    private static boolean DEBUG;

    static {
        DEBUG = Pref.pikoDebug();
    }

    public static void moreOptionsDailogueBox(Context context, UserData userData) {
        try {
            InstagramBottomSheet sheet = new InstagramBottomSheet(context);
            sheet.setTitle(str("piko_more_profile_options"));

            sheet.addItem(str("piko_copy_username"),
                    InstagramBottomSheet.IconSpec.person(Color.parseColor("#5B4EE0")),
                    () -> copyText(userData.getUsername()));

            sheet.addItem(str("piko_copy_full_name"),
                    InstagramBottomSheet.IconSpec.text("Aa", Color.parseColor("#2F6FE0")),
                    () -> copyText(userData.getFullname()));

            sheet.addItem(str("piko_copy_user_id"),
                    InstagramBottomSheet.IconSpec.text("ID", Color.parseColor("#159C82")),
                    () -> copyText(userData.getUserId()));

            sheet.addItem(str("piko_copy_bio"),
                    InstagramBottomSheet.IconSpec.document(Color.parseColor("#A66A2E")),
                    () -> copyText(userData.getBio()));

            sheet.addItem(str("piko_download_profile_picture"),
                    InstagramBottomSheet.IconSpec.download(Color.parseColor("#8C2E3C")),
                    () -> {
                        String url = userData.getProfilePictureUrl();
                        String username = userData.getUsername();
                        String downloadFilename = username + "_dp.jpg";
                        String subFolder = DownloadUtils.getSubfolderName(username);
                        DownloadUtils.downloadMediaUrl(context, url, subFolder, downloadFilename);
                    });

            boolean isAutoDownloadTarget = Pref.isAutoDownloadTarget(userData.getUserId());
            sheet.addItem(
                    isAutoDownloadTarget
                            ? str("piko_auto_download_stories_disable")
                            : str("piko_auto_download_stories_enable"),
                    InstagramBottomSheet.IconSpec.refresh(Color.parseColor("#189188")),
                    () -> {
                        if (isAutoDownloadTarget) {
                            Pref.removeAutoDownloadTarget(userData.getUserId());
                            Utils.showToastShort(str("piko_auto_download_stories_disabled_toast"));
                        } else {
                            Pref.addAutoDownloadTarget(userData.getUserId(), userData.getUsername());
                            Utils.showToastShort(str("piko_auto_download_stories_enabled_toast"));
                        }
                    });

            if (DEBUG) {
                sheet.addItem(str("piko_debug"), () ->
                        ObjectBrowser.browseObject(context, userData.getObject()));
            }

            sheet.show();
        } catch (Exception e) {
            Logger.printException(() -> "Error at moreOptionsDailogueBox", e);
            Utils.showToastShort(e.getMessage());
        }
    }

    private static void copyText(String text) {
        if (text != null && text.length() > 0) {
            Utils.setClipboard(text);
            Utils.showToastShort(str("piko_copied"));
        }
    }

    public static void addProfileMoreOptionsButton(ViewGroup viewGroup, ProfileInfo profileInfo) {
        try {
            UserData userData = profileInfo.getUserData();

            Context context = viewGroup.getContext();
            InstagramButton button = new InstagramButton(context);
            button.setText(str("piko_more_profile_options"));
            button.setStyle(InstagramButtonStyleEnum.PRIMARY);
            button.setOnClickListener(() ->
                    moreOptionsDailogueBox(context, userData)
            );

            int marginPx = Dim.dp12;
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

