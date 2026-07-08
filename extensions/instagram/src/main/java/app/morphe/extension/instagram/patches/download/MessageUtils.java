/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.download;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;

import app.morphe.extension.instagram.entity.MessageInfo;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.entity.DirectItem;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.constants.Constants;

import app.morphe.extension.crimera.ObjectBrowser;
import app.morphe.extension.crimera.downloader.MediaType;
import app.morphe.extension.crimera.PikoUtils;

public class MessageUtils {
    private static boolean DEBUG;
    static {
        DEBUG = Pref.pikoDebug();
    }

    public static boolean messageDownloadCheck(Context context, Object messageInfoObject){
        try{
            MessageInfo messageInfo = new MessageInfo(messageInfoObject);
            String messageType = messageInfo.getMessageType();

            Log.d("piko", "messageDownloadCheck type=" + messageType);

            if(DEBUG){
                ObjectBrowser.browseObject(context, messageInfo);
                return false;
            }

            if("media".equals(messageType) || "raven_media".equals(messageType)){
                Log.d("piko", "messageDownloadCheck -> let Instagram handle (media)");
                return true;

            } else if ("voice_media".equals(messageType) && SettingsStatus.downloadVoiceMessage) {
                MediaData audioData = messageInfo.getAudioMedia();
                String audioUrl = audioData.getMessageAudioUrl();
                String fileName = audioData.getDownloadFilename(MediaType.AUDIO);
                DownloadUtils.downloadMediaUrl(context,audioUrl,Constants.DEFAULT_DM_FOLDER,fileName);

                showVoiceDownloadedDialog(context, audioUrl);
                return false;

            } else if (isXmaType(messageType)) {
                showXmaDialog(context, messageInfoObject, messageType);
                return false;

            } else {
                String label = messageType != null ? messageType : "null";
                Log.d("piko", "messageDownloadCheck -> unsupported type: " + label);
                Utils.showToastShort(str("piko_media_unknown") + ": " + label);
            }

        } catch (Exception e) {
            Log.e("piko", "messageDownloadCheck threw", e);
            app.morphe.extension.crimera.PikoUtils.logger(e);
        }
        return false;
    }

    private static boolean isXmaType(String type) {
        return "reel_share".equals(type) || "story_share".equals(type) || "media_share".equals(type)
            || "clip".equals(type) || "xma_clip".equals(type)
            || "animated_media".equals(type) || "link".equals(type);
    }

    private static void showVoiceDownloadedDialog(Context context, String audioUrl) {
        try {
            InstagramDialogBox dialog = new InstagramDialogBox(context);
            ArrayList<String> options = new ArrayList<>();
            options.add(str("piko_open_voice_with_player"));

            dialog.addDialogMenuItems(options.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which == 0) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.parse(audioUrl), "audio/*");
                        PikoUtils.launchIntent(Intent.createChooser(intent, str("piko_open_voice_with_player")));
                    }
                }
            });
            dialog.setTitle(str("piko_download_complete"));
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);
            Dialog dlg = dialog.getDialog();
            dlg.show();
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }

    /**
     * Best-effort URL extraction for shared content (XMA / reel / post / story shares).
     * Tries DirectItem.getText() (which contains the share URL for XMA types in most versions)
     * then DirectItem.getMediaUrl() as fallback.
     */
    private static String extractContentUrl(Object messageInfoObject) {
        try {
            DirectItem directItem = new DirectItem(messageInfoObject);
            String text = directItem.getText();
            if (text != null && !text.isEmpty()) return text;
            return directItem.getMediaUrl();
        } catch (Exception e) {
            PikoUtils.logger(e);
            return null;
        }
    }

    private static void showXmaDialog(Context context, Object messageInfoObject, String messageType) {
        try {
            String url = extractContentUrl(messageInfoObject);

            InstagramDialogBox dialog = new InstagramDialogBox(context);
            ArrayList<String> options = new ArrayList<>();
            if (url != null) {
                options.add(str("piko_open_share_in_instagram"));
                options.add(str("piko_copy_media_link"));
            } else {
                options.add(str("piko_copy_media_link"));
            }

            final String finalUrl = url;
            dialog.addDialogMenuItems(options.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    try {
                        if (which == 0 && finalUrl != null) {
                            PikoUtils.openUrl(finalUrl);
                        } else if ((which == 0 && finalUrl == null) || (which == 1)) {
                            if (finalUrl != null) {
                                Utils.setClipboard(finalUrl);
                                Utils.showToastShort(str("piko_copied_media_link"));
                            } else {
                                // Try to get at least the message text for clipboard
                                try {
                                    DirectItem di = new DirectItem(messageInfoObject);
                                    String text = di.getText();
                                    if (text != null && !text.isEmpty()) {
                                        Utils.setClipboard(text);
                                    }
                                } catch (Exception ignored) {}
                                Utils.showToastShort(str("piko_media_not_available"));
                            }
                        }
                    } catch (Exception e) {
                        PikoUtils.logger(e);
                    }
                }
            });
            dialog.setTitle(str("piko_shared_content"));
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);
            Dialog dlg = dialog.getDialog();
            dlg.show();
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }

}
