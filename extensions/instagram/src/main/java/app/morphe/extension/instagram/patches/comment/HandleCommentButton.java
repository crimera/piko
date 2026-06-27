/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.comment;

import static app.morphe.extension.instagram.utils.IgStr.str;

import java.util.List;
import android.content.Context;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.crimera.ObjectBrowser;
import app.morphe.extension.crimera.downloader.MediaType;

import app.morphe.extension.instagram.entity.CommentData;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.instagram.patches.comment.copyTextButton.CopyTextButton;
import app.morphe.extension.instagram.patches.comment.debugButton.DebugButton;
import app.morphe.extension.instagram.patches.comment.saveMediaButton.SaveMediaButton;

// Thanks to MyInsta.
@SuppressWarnings("unused")
public class HandleCommentButton {

    public static void addButtons(List list, Object commentObject) {
        try {
            CommentData commentData = new CommentData(commentObject);

            if (Pref.pikoDebug()) {
                list.add(DebugButton.A00);
            }
            if (commentData.hasText() && Pref.commentCopyButton()) {
                list.add(CopyTextButton.A00);
            }
            if(commentData.hasMedia() && Pref.commentSaveMediaButton()){
                list.add(SaveMediaButton.A00);
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }

    public static boolean checkOnCommentButtonClick(Object button, List list) {
        try {
            if (list.isEmpty()) return false;
            Object commentObject = list.get(0);

            if (button.equals(CopyTextButton.A00)) {
                CommentData commentData = new CommentData(commentObject);
                if (commentData.hasText()) {
                    String commentText = (String) commentData.getText();
                    app.morphe.extension.shared.Utils.setClipboard(commentText);
                    PikoUtils.toast(str("piko_comment_copied_success"));
                } else {
                    PikoUtils.toast(str("piko_comment_copied_failed"));
                }
                return true;
            } else if (button.equals(DebugButton.A00)) {
                CommentData commentData = new CommentData(commentObject);

                Context context = (Context) Utils.getActivity();
                ObjectBrowser.browseObject(context,commentData);
                return true;
            } else if (button.equals(SaveMediaButton.A00)) {
                CommentData commentData = new CommentData(commentObject);

                Context context = (Context) Utils.getActivity();
                if(commentData.hasGifMedia()) {
                    String gifUrl = commentData.getGifUrl();
                    String fileName = commentData.getGifDownloadName();
                    DownloadUtils.downloadMediaUrl(context, gifUrl, Constants.DEFAULT_GIF_FOLDER, fileName);
                    return true;
                } else if (commentData.hasImageMedia()) {
                    MediaData imageData = commentData.getImageMedia();
                    UserData userData = commentData.getCommentUserData();

                    String userName = userData.getUsername();
                    String mediaLink = imageData.getMediaLink();
                    String fileName = userName+"_"+imageData.getDownloadFilename(MediaType.IMAGE);

                    String subFolder = DownloadUtils.getSubfolderName(userName);

                    DownloadUtils.downloadMediaUrl(context, mediaLink, subFolder, fileName);
                    return true;
                }
            }

        } catch (Exception e) {
            PikoUtils.logger(e);
        }
        return false;
    }

}
