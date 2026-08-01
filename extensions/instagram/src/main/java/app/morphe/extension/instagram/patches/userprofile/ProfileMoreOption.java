/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.userprofile;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.app.Dialog;
import android.content.DialogInterface;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.ProfileInfo;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.crimera.ObjectBrowser;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
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
            InstagramDialogBox dialog = new InstagramDialogBox(context);

            ArrayList<String> options = new ArrayList<>();
            options.add(str("piko_view_profile_picture"));
            options.add(str("piko_download_profile_picture"));
            options.add(str("piko_copy_username"));
            options.add(str("piko_copy_full_name"));
            options.add(str("piko_copy_user_id"));
            options.add(str("piko_copy_profile_link"));
            options.add(str("piko_share_this_profile"));
            options.add(str("piko_copy_bio"));
            options.add(str("piko_copy_links_from_bio"));
            if (DEBUG) options.add(str("piko_debug"));

            CharSequence[] items = options.toArray(new CharSequence[0]);

            dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    try {
                        // Doing like this because options are dynamic.
                        String selectedOption = options.get(which);
                        boolean toCopy = false;
                        String text = null;

                        if (selectedOption.equals(str("piko_copy_username"))) {
                            text = userData.getUsername();
                            toCopy = true;

                        } else if (selectedOption.equals(str("piko_copy_full_name"))) {
                            text = userData.getFullName();
                            toCopy = true;

                        } else if (selectedOption.equals(str("piko_copy_user_id"))) {
                            text = userData.getUserId();
                            toCopy = true;

                        } else if (selectedOption.equals(str("piko_copy_bio"))) {
                            text = userData.getBio();
                            toCopy = true;

                        } else if (selectedOption.equals(str("piko_copy_profile_link"))) {
                            text = profileLink(userData.getUsername());
                            toCopy = true;

                        } else if (selectedOption.equals(str("piko_share_this_profile"))) {
                            PikoUtils.shareText(profileLink(userData.getUsername()));

                        } else if (selectedOption.equals(str("piko_copy_links_from_bio"))) {
                            // Links live in the bio as plain text; the profile object
                            // carries no separate list of them.
                            String links = linksFrom(userData.getBio());
                            if (links == null) {
                                Utils.showToastShort(str("piko_no_links_in_bio"));
                            } else {
                                text = links;
                                toCopy = true;
                            }

                        } else if (selectedOption.equals(str("piko_view_profile_picture"))) {
                            ActivityHook.handleUrlIntent(false, userData.getProfilePictureUrl());

                        } else if (selectedOption.equals(str("piko_download_profile_picture"))) {
                            String url = userData.getProfilePictureUrl();
                            String username = userData.getUsername();
                            String downloadFilename = username+"_dp.jpg";
                            String subFolder = DownloadUtils.getSubfolderName(username);
                            DownloadUtils.downloadMediaUrl(context, url, subFolder, downloadFilename);
                            toCopy = false;

                        } else if (selectedOption.equals(str("piko_debug"))) {
                            ObjectBrowser.browseObject(context, userData.getObject());

                        }

                        if (toCopy && text != null && text.length() > 0) {
                            Utils.setClipboard(text);
                            Utils.showToastShort(str("piko_copied"));
                        }
                    } catch (Exception e) {
                        Logger.printException(() -> "Error at moreOptionsDailogueBox onclick", e);
                        Utils.showToastShort(e.getMessage());
                    }
                }
            });
            dialog.setTitle(str("piko_more_profile_options"));
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);

            Dialog dlg = dialog.getDialog();
            dlg.show();
        } catch (Exception e) {
            Logger.printException(() -> "Error at moreOptionsDailogueBox", e);
            Utils.showToastShort(e.getMessage());
        }
    }

    /** The public permalink of a profile: what "share" and "copy link" both hand out. */
    private static String profileLink(String username) {
        return "https://www.instagram.com/" + username + "/";
    }

    /**
     * Every link written in a bio, one per line. Instagram stores the bio as plain
     * text, so a URL there is only ever a URL by how it reads — matched loosely on
     * purpose, since most bios write "example.com" without a scheme.
     */
    private static String linksFrom(String bio) {
        if (bio == null || bio.isEmpty()) return null;
        Matcher matcher = BIO_LINK.matcher(bio);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String link = matcher.group();
            if (out.indexOf(link) >= 0) continue; // the same link twice reads as noise
            if (out.length() > 0) out.append('\n');
            out.append(link);
        }
        return out.length() == 0 ? null : out.toString();
    }

    private static final Pattern BIO_LINK = Pattern.compile(
            "(?:https?://)?(?:[\\w-]+\\.)+[a-z]{2,}(?:/[^\\s]*)?",
            Pattern.CASE_INSENSITIVE);

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

