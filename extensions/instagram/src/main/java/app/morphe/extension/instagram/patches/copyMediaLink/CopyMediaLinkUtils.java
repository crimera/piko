/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.copyMediaLink;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;

import java.util.ArrayList;

import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

import com.instagram.common.session.UserSession;

public class CopyMediaLinkUtils {

    public static void copyMediaLinkDialog(Context context, UserSession userSession, Object mediaObject, int currentMediaIndex) {
        try {
            MediaData mediaData = new MediaData(mediaObject, userSession);
            int carouselSize = mediaData.getCarouselSize();
            boolean hasAudio = mediaData.getMediaAt(currentMediaIndex).hasAudio();

            InstagramDialogBox dialog = new InstagramDialogBox(context);
            ArrayList<String> options = new ArrayList<>();

            options.add(str("piko_copy_current_media_link"));
            if (hasAudio) {
                options.add(str("piko_copy_audio_link"));
            }
            if (carouselSize > 1) {
                options.add(str("piko_copy_all_media_links"));
            }

            CharSequence[] items = options.toArray(new CharSequence[0]);

            dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    try {
                        // Doing like this because options are dynamic.
                        String selectedOption = options.get(which);
                        String stringToCopy = null;

                        if (selectedOption.equals(str("piko_copy_current_media_link"))) {
                            stringToCopy = mediaData.getMediaAt(currentMediaIndex).getMediaLink();

                        } else if (selectedOption.equals(str("piko_copy_audio_link"))) {
                            stringToCopy = mediaData.getMediaAt(currentMediaIndex).getAudioMedia().getAudioUrl();

                        } else if (selectedOption.equals(str("piko_copy_all_media_links"))) {
                            StringBuilder builder = new StringBuilder();
                            int size = mediaData.getCarouselSize();
                            for (int index = 0; index < size; index++) {
                                if (index > 0) {
                                    builder.append('\n');
                                }
                                builder.append(mediaData.getMediaAt(index).getMediaLink());
                            }
                            stringToCopy = builder.toString();
                        }

                        if (stringToCopy != null && !stringToCopy.isEmpty()) {
                            Utils.setClipboard(stringToCopy);
                            Utils.showToastShort(str("piko_copied_media_link"));
                        } else {
                            Utils.showToastShort(str("piko_fail_no_file"));
                        }
                    } catch (Exception e) {
                        Logger.printException(() -> "Error at copyMediaLinkDialog onClick", e);
                        Utils.showToastShort(e.getMessage());
                    }
                }
            });

            dialog.setTitle(str("piko_copy_media_link"));
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);

            Dialog dlg = dialog.getDialog();
            dlg.show();

        } catch (Exception e) {
            Utils.showToastShort(e.getMessage());
            Logger.printException(() -> "copyMediaLinkDialog failure", e);
        }
    }
}
