package app.revanced.extension.twitter.settings;

import android.app.Activity;
import android.os.Bundle;
import java.util.List;
import android.net.Uri;
import  app.revanced.extension.twitter.Utils;

@SuppressWarnings("deprecation")
public class DeepLink {
    private static final String bundleFlagNameKey = "flagName";
    private static final String bundleFlagValueKey = "flagValue";

    public static boolean deeplink(Activity act) {
        try {
            Uri deeplink = act.getIntent().getData();
            List<String> deeplinkSegments = deeplink.getPathSegments();
            int segmentSize = deeplinkSegments.size();

            if(deeplinkSegments.size() < 2) return false;

            if(!(deeplinkSegments.get(0).equals("i") || deeplinkSegments.get(0).equals("r"))) return false;

            String mainPath = deeplinkSegments.get(1).toLowerCase();
            String lastSegment = deeplink.getLastPathSegment();

            boolean isPiko = mainPath.equals("piko") || mainPath.equals("pikosettings");

            String key = null;
            if(segmentSize == 2 && lastSegment.equals("settings")){
                Utils.startXSettings();
                return true;
            }else if(segmentSize == 2 && isPiko){
                ActivityHook.startSettingsActivity();
                return true;
            }else if(mainPath.equals("addflags")){
                key = Settings.FEATURE_FLAGS;
                String bundleFlagName = deeplink.getQueryParameter("f");
                if(bundleFlagName!=null){
                    boolean bundleFlagValue = deeplink.getBooleanQueryParameter("v",true);
                    Bundle bundle = new Bundle();
                    bundle.putString(bundleFlagNameKey, bundleFlagName);
                    bundle.putBoolean(bundleFlagValueKey, bundleFlagValue);
                    ActivityHook.startActivity(key,bundle);
                    return true;

                }
            }else if(mainPath.equals("status") && deeplinkSegments.get(0).equals("r")){
                String tweetId = deeplinkSegments.get(2);
                ActivityHook.startReaderMode(tweetId);
                return true;
            }
            else if (isPiko) {
                if(lastSegment.equals("premium")){
                    key = Settings.PREMIUM_SECTION;
                } else if (lastSegment.equals("download")) {
                    key = Settings.DOWNLOAD_SECTION;
                } else if (lastSegment.equals("flags")) {
                    key = Settings.FLAGS_SECTION;
                } else if (lastSegment.equals("ads")) {
                    key = Settings.ADS_SECTION;
                } else if (lastSegment.equals("native")) {
                    key = Settings.NATIVE_SECTION;
                } else if (lastSegment.equals("misc")) {
                    key = Settings.MISC_SECTION;
                } else if (lastSegment.equals("customise") || lastSegment.equals("customize")) {
                    key = Settings.CUSTOMISE_SECTION;
                } else if (lastSegment.equals("font")) {
                    key = Settings.FONT_SECTION;
                } else if (lastSegment.equals("timeline")) {
                    key = Settings.TIMELINE_SECTION;
                } else if (lastSegment.equals("pref")) {
                    key = Settings.BACKUP_SECTION;
                } else if (lastSegment.equals("info")) {
                    key = Settings.PATCH_INFO;
                }
            }
            if(key!=null){
                ActivityHook.startActivity(key);
                return true;
            }
        }catch(Exception e){
            Utils.logger(e);
        }

        return false;
    }


}