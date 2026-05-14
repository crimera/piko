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

import android.view.View;
import android.content.Context;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

import com.instagram.feed.media.mediaoption.MediaOption$Option;

public class ReelButton implements View.OnClickListener  {
    public final Context context;
    public final Object mediaObject;
    private static boolean ENABLE_DOWNLOAD;
    private static String DOWNLOAD_BUTTON_TEXT;
    static{
        ENABLE_DOWNLOAD = Pref.enableDownload() && SettingsStatus.downloadMedia;
        if(Pref.enableDirectDownload() && SettingsStatus.downloadMedia){
            DOWNLOAD_BUTTON_TEXT = Strings.CATEGORY_DOWNLOAD_MEDIA;
        }else{
            DOWNLOAD_BUTTON_TEXT = Strings.DOWNLOAD_OPTIONS;
        }
    }

    public ReelButton(Context context, Object mediaObject) {
        this.context = context;
        this.mediaObject = mediaObject;
    }

    public static void addReelButton(Context context, Object helperObject, Object mediaObject){
        try {
            if(ENABLE_DOWNLOAD) {
                int icon = MediaOption$Option.DOWNLOAD.iconDrawable;
                ReelButton listener = new ReelButton(context, mediaObject);

                Class<?> clazz = helperObject.getClass();
                Method method = clazz.getDeclaredMethod(
                        "A01",
                        Context.class,
                        View.OnClickListener.class,
                        String.class,
                        int.class
                );

                method.setAccessible(true);

                method.invoke(helperObject, context, listener, DOWNLOAD_BUTTON_TEXT, icon);
            }
        } catch (Exception e) {
            Logger.printException(() -> "Error at addReelButton",e);
        }
    }

    @Override
    public final void onClick(View view) {
        DownloadUtils.downloadPost(this.context,this.mediaObject,0);
    }

}