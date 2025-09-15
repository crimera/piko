
package app.revanced.extension.twitter.patches;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.LinearLayout;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import app.revanced.extension.twitter.Utils;
import app.revanced.extension.twitter.Pref;

public class DownloadPatch {

    public static void mediaHandle(Object obj1, Object para1){
        try{
            int ch = Pref.vidMediaHandle();
            switch(ch){
                case 1:{
                    downloadVideoMedia(obj1, para1);
                    break;
                }
                case 2:{
                    copyVideoMediaLink(para1);
                    break;
                }
                case 3:{
                    alertbox(obj1, para1);
                    break;
                }
            }

        }catch (Exception e){
            Utils.toast(e.toString());
        }
    }

    private static void downloadVideoMedia(Object obj1, Object para1){
        try {
            Class<?> clazz = obj1.getClass();
            clazz.cast(obj1);
            Method downloadClass = clazz.getDeclaredMethod("b", para1.getClass());
            downloadClass.invoke(obj1, para1);
        }
        catch (Exception e){
            Utils.toast(e.toString());
        }
    }

    private static String getMediaLink(Object para1) {
        try{
            Class<?> clazz = para1.getClass();
            clazz.cast(para1);
            Field urlField = clazz.getDeclaredField("a");
            String mediaLink = (String) urlField.get(para1);
            return mediaLink;
        }
        catch (Exception e){
            Utils.toast(e.toString());
        }
        return "";
    }

    private static void copyVideoMediaLink(Object para1) {
        try{

            String mediaLink = getMediaLink(para1);
            app.revanced.extension.shared.Utils.setClipboard(mediaLink);
            Utils.toast(strRes("link_copied_to_clipboard"));
        }
        catch (Exception e){
            Utils.toast(e.toString());
        }
    }

    private static void shareMediaLink(Object para1) {
        try{
            String mediaLink = getMediaLink(para1);
            app.revanced.extension.shared.Utils.shareText(mediaLink);
        }
        catch (Exception e){
            Utils.toast(e.toString());
        }
    }

    private static void alertbox(Object obj1, Object para1) throws NoSuchFieldException, IllegalAccessException {
        Class<?> clazz = obj1.getClass();
        clazz.cast(obj1);
        Field ctxField = clazz.getDeclaredField("a");
        Activity context = (Activity) ctxField.get(obj1);

        LinearLayout ln = new LinearLayout(context);
        ln.setOrientation(LinearLayout.VERTICAL);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(strRes("piko_pref_download_media_link_handle"));

        String[] choices = {strRes("download_video_option"), strRes("piko_pref_download_media_link_handle_copy_media_link"),strRes("piko_pref_download_media_link_handle_share_media_link"), strRes("cancel")};
        builder.setItems(choices, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                switch (which) {
                    case 0: {
                        downloadVideoMedia(obj1, para1);
                        break;
                    }
                    case 1: {
                        copyVideoMediaLink(para1);
                        break;
                    }
                    case 2: {
                        shareMediaLink(para1);
                        break;
                    }
                }
                dialogInterface.dismiss();
            }
        });
        builder.show();

        //endfunc
    }

    private static String strRes(String tag) {
        return Utils.strRes(tag);
    }
    //end
}
