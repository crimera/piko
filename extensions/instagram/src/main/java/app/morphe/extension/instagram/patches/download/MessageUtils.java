/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.download;


import android.content.Context;

import app.morphe.extension.instagram.entity.MessageInfo;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.constants.Constants;

import app.morphe.extension.crimera.ObjectBrowser;
import app.morphe.extension.crimera.downloader.MediaType;

public class MessageUtils {
    private static boolean DEBUG;
    static {
        DEBUG = Pref.pikoDebug();
    }

    public static boolean messageDownloadCheck(Context context, Object messageInfoObject){
        try{
            MessageInfo messageInfo = new MessageInfo(messageInfoObject);
            String messageType = messageInfo.getMessageType();

            if(DEBUG){
                ObjectBrowser.browseObject(context, messageInfo);
                return false;
            }

            if(messageType == "media" || messageType == "raven_media"){
                return true;

            } else if (messageType == "voice_media" && SettingsStatus.downloadVoiceMessage) {
                MediaData audioData = messageInfo.getAudioMedia();
                String audioUrl = audioData.getMessageAudioUrl();
                String fileName = audioData.getDownloadFilename(MediaType.AUDIO);
                DownloadUtils.downloadMediaUrl(context,audioUrl,Constants.DEFAULT_DM_FOLDER,fileName);

                // We need to return false since we don't need download action to be taken by Instagram.
                return false;

            } else {
                Utils.showToastShort(messageType+" doesnt support downloading");
            }

        } catch (Exception e) {
            app.morphe.extension.crimera.PikoUtils.logger(e);
        }
        return false;
    }

}
