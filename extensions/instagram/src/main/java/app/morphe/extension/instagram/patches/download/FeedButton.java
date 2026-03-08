package app.morphe.extension.instagram.patches.download;


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
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

import com.instagram.feed.media.mediaoption.MediaOption$Option;

public class FeedButton {
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

    private static Class<?> getEnumButtonClass() throws Exception {
        return Class.forName("X.6zl");
    }
    private static Object enumNormalButton() throws Exception {
        return (Object) new Entity().getMethod(getEnumButtonClass(),"valueOf","NORMAL");
    }

    private static void addButton(Object buttonAdderObject, ArrayList buttonlist) throws Exception {
        Class<?> clazz = buttonAdderObject.getClass();

        Method method = clazz.getDeclaredMethod(
                "A00",
                getEnumButtonClass(),
                MediaOption$Option.class,
                clazz,
                CharSequence.class,
                ArrayList.class,
                boolean.class
        );

        method.setAccessible(true);
        method.invoke(null, enumNormalButton(), MediaOption$Option.DOWNLOAD, buttonAdderObject, DOWNLOAD_BUTTON_TEXT, buttonlist, false);
    }

    public static void addFeedButton(Object buttonAdderObject, ArrayList buttonlist){
        try {
            if(ENABLE_DOWNLOAD) {
                addButton(buttonAdderObject, buttonlist);
            }
        } catch (Exception e) {
            Logger.printException(() -> "Error at addReelButton",e);
        }
    }

    public static boolean isDownloadButton(MediaOption$Option buttonOption){
        try {
            return buttonOption.equals(MediaOption$Option.DOWNLOAD);
        } catch (Exception e) {
            Logger.printException(() -> "Error at isDownloadButton",e);
        }
        return false;
    }

    public static void downloadPost(Context context, Object mediaObject, int position){
        DownloadUtils.downloadPost(context,mediaObject,position);
    }
}