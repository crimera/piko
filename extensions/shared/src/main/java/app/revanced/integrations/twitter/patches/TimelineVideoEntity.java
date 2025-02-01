package app.revanced.integrations.twitter.patches;

import java.util.List;
import java.util.ArrayList;
import  app.revanced.integrations.twitter.Utils;
import app.revanced.integrations.twitter.Pref;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TimelineVideoEntity {

    public static List videoEnity(List videoEnities){
        try {
            ArrayList out = new ArrayList();
            int max = 0;
            Object max_V = null;

            if(Pref.ENABLE_FORCE_HD) {
                for (Object obj : videoEnities) {
                    Class<?> videoDataClass = obj.getClass();
                    String mediaType = (String) videoDataClass.getDeclaredField("c").get(obj);
                    if (!(mediaType.equals("video/mp4"))) continue;

                    if (Pref.ENABLE_FORCE_HD) {
                        int a = (int) videoDataClass.getDeclaredField("a").get(obj);
                        if (max < a) {
                            max = a;
                            max_V = obj;
                        }
                    }
                }
            }
            if (max_V != null) {
                out.add(max_V);
                return out;
            }

        }catch (Exception e){
            Utils.logger(e);
        }

        return videoEnities;
    }
}