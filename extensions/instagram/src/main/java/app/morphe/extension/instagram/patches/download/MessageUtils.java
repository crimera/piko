/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */


package app.morphe.extension.instagram.patches.download;


import app.morphe.extension.instagram.entity.MessageInfo;
import app.morphe.extension.shared.Utils;

public class MessageUtils {

    public static boolean messageDownloadCheck(Object messageInfoObject){
        try{
            MessageInfo messageInfo = new MessageInfo(messageInfoObject);
            String messageType = messageInfo.getMessageType();
            if(messageType == "media" || messageType == "raven_media"){
                return true;
            }else {
                Utils.showToastShort(messageType+" doesnt support downloading");
            }

        } catch (Exception e) {
            app.morphe.extension.crimera.Utils.logger(e);
        }
        return false;
    }

}
