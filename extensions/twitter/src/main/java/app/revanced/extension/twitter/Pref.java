package app.revanced.extension.twitter;

import android.util.Log;
import app.revanced.extension.twitter.settings.Settings;
import com.google.android.material.tabs.TabLayout$g;
import java.util.*;

@SuppressWarnings("unused")
public class Pref {
    public static boolean ROUND_OFF_NUMBERS,ENABLE_FORCE_HD, HIDE_COMM_BADGE,SHOW_SRC_LBL;
    public static float POST_FONT_SIZE;
    static{
        ROUND_OFF_NUMBERS = isRoundOffNumbersEnabled();
        ENABLE_FORCE_HD = enableForceHD();
        POST_FONT_SIZE = setPostFontSize();
        HIDE_COMM_BADGE = hideCommBadge();
        SHOW_SRC_LBL = showSourceLabel();
    }
    public static float setPostFontSize() {
        Float fontSize = 0.0f;
        try{
            fontSize = Float.valueOf(Utils.getStringPref(Settings.CUSTOM_POST_FONT_SIZE));
        }catch (Exception ex){
            fontSize = app.revanced.extension.shared.Utils.getResourceDimension("font_size_normal");
        }
        return fontSize;
    }
    public static boolean serverResponseLogging() {
        return Utils.getBooleanPerf(Settings.LOG_RES);
    }
    public static boolean serverResponseLoggingOverwriteFile() {
        return Utils.getBooleanPerf(Settings.LOG_RES_OVRD);
    }

    public static boolean showSourceLabel() {
        return Utils.getBooleanPerf(Settings.TIMELINE_SHOW_SOURCE_LABEL);
    }
    public static boolean hideCommBadge() {
        return Utils.getBooleanPerf(Settings.TIMELINE_HIDE_COMM_BADGE);
    }

    public static boolean showSensitiveMedia() {
        return Utils.getBooleanPerf(Settings.TIMELINE_SHOW_SENSITIVE_MEDIA);
    }

    public static boolean hideTodaysNews() {
        return Utils.getBooleanPerf(Settings.ADS_REMOVE_TODAYS_NEW);
    }

    public static boolean enableNativeDownloader() {
        return Utils.getBooleanPerf(Settings.VID_NATIVE_DOWNLOADER);
    }

    public static int natveTranslatorProvider(){
        return Integer.parseInt(Utils.getStringPref(Settings.NATIVE_TRANSLATOR_PROVIDERS));
    }
    public static boolean enableNativeTranslator() {
        return Utils.getBooleanPerf(Settings.NATIVE_TRANSLATOR);
    }

    public static boolean enableNativeReaderMode() {
        return Utils.getBooleanPerf(Settings.NATIVE_READER_MODE);
    }
    public static boolean hideNativeReaderPostTextOnlyMode() {
        return Utils.getBooleanPerf(Settings.NATIVE_READER_MODE_TEXT_ONLY_MODE);
    }
    public static boolean hideNativeReaderHideQuotedPosts() {
        return Utils.getBooleanPerf(Settings.NATIVE_READER_MODE_HIDE_QUOTED_POST);
    }

    public static boolean hideNativeReaderNoGrok() {
        return Utils.getBooleanPerf(Settings.NATIVE_READER_MODE_NO_GROK);
    }

    public static String translatorLanguage() {
        return Utils.getStringPref(Settings.NATIVE_TRANSLATOR_LANG);
    }
    public static boolean redirect(TabLayout$g g) {return Utils.redirect(g);}

    public static boolean isRoundOffNumbersEnabled() {
        return Utils.getBooleanPerf(Settings.MISC_ROUND_OFF_NUMBERS);
    }

    public static boolean isChirpFontEnabled() {
        return Utils.getBooleanPerf(Settings.MISC_FONT);
    }

    public static boolean unShortUrl() {
        return Utils.getBooleanPerf(Settings.TIMELINE_UNSHORT_URL);
    }

    public static String getPublicFolder() {
        return Utils.getStringPref(Settings.VID_PUBLIC_FOLDER);
    }

    public static String getVideoFolder(String filename) {
        return Utils.getStringPref(Settings.VID_SUBFOLDER) + "/" + filename;
    }

    public static int vidMediaHandle() {
        String val = Utils.getStringPref(Settings.VID_MEDIA_HANDLE);
        if(val.equals("download_media")){
            return 1;
        }
        if (val.equals("copy_media_link")){
            return 2;
        }
        return 3;
    }
    public static String customSharingDomain() {
        return Utils.getStringPref(Settings.CUSTOM_SHARING_DOMAIN);
    }

    public static ArrayList hideRecommendedUsers(ArrayList users) {
        if (Utils.getBooleanPerf(Settings.MISC_HIDE_RECOMMENDED_USERS)) {
            return null;
        }
        return users;
    }

    public static ArrayList liveThread(ArrayList fleets) {
        if (Utils.getBooleanPerf(Settings.TIMELINE_HIDE_LIVETHREADS)) {
            return null;
        }
        return fleets;
    }

    public static Map polls(Map map) {
        if (Utils.getBooleanPerf(Settings.TIMELINE_SHOW_POLL_RESULTS)) {
            try {
                if (map.containsKey("counts_are_final")) {
                    if (map.get("counts_are_final").toString().equals("true")) {
                        return map;
                    }
                }

                HashMap newMap = new HashMap();

                ArrayList<String> labels = new ArrayList(Arrays.asList("choice1_label", "choice2_label", "choice3_label", "choice4_label"));
                String[] counts = {"choice1_count", "choice2_count", "choice3_count", "choice4_count"};

                // get sum
                int totalVotes = 0;
                for (String count : counts) {
                    if (!map.containsKey(count)) {
                        break;
                    }

                    totalVotes += Integer.parseInt(map.get(count).toString());
                }

                for (Object key : map.keySet()) {
                    Object idk = map.get(key);

                    if (labels.contains(key.toString())) {
                        String countLabel = counts[labels.indexOf(key.toString())];

                        int count = 0;
                        if (map.get(countLabel) != null) {
                            count = Integer.parseInt(map.get(countLabel).toString());
                        }

                        int percentage = Math.round(count * 100.0f / totalVotes);

                        newMap.put(
                                key,
                                idk.getClass().getConstructor(Object.class, String.class).newInstance(
                                        idk + " - " + percentage + "%",
                                        null
                                )
                        );

                        continue;
                    }

                    newMap.put(key, idk);
                }

                return newMap;
            } catch (Exception e) {
                Log.d("POLL_ERROR", map.toString());
            }
        }
        return map;
    }

    public static boolean hideBanner() {
        return !Utils.getBooleanPerf(Settings.TIMELINE_HIDE_BANNER);
    }

    public static int timelineTab() {
        String val = Utils.getStringPref(Settings.CUSTOM_TIMELINE_TABS);
        if(val.equals("hide_forYou")){
            return 1;
        }
        if (val.equals("hide_following")){
            return 2;
        }
        return 0;
    }

    public static boolean enableForceTranslate() {
        return Utils.getBooleanPerf(Settings.TIMELINE_HIDE_FORCE_TRANSLATE);
    }
    public static boolean hidePromoteBtn() {
        return Utils.getBooleanPerf(Settings.TIMELINE_HIDE_PROMOTE_BUTTON);
    }
    public static boolean hideFAB() {
        return Utils.getBooleanPerf(Settings.MISC_HIDE_FAB);
    }

    public static boolean hideFABBtn() {
        return !Utils.getBooleanPerf(Settings.MISC_HIDE_FAB_BTN);
    }

    public static boolean hideCommNotes() {
        return Utils.getBooleanPerf(Settings.MISC_HIDE_COMM_NOTES);
    }

    public static boolean hideViewCount() {
        return !Utils.getBooleanPerf(Settings.MISC_HIDE_VIEW_COUNT);
    }

    public static boolean hideInlineBookmark() {
        return !Utils.getBooleanPerf(Settings.TIMELINE_HIDE_BMK_ICON);
    }

    public static boolean hideImmersivePlayer() {
        return !Utils.getBooleanPerf(Settings.TIMELINE_HIDE_IMMERSIVE_PLAYER);
    }

    public static int enableVidAutoAdvance() {
        if(Utils.getBooleanPerf(Settings.TIMELINE_ENABLE_VID_AUTO_ADVANCE)){
            return 1;
        }
        return -1;
    }

    public static boolean hideHiddenReplies(boolean bool){
        if(Utils.getBooleanPerf(Settings.TIMELINE_HIDE_HIDDEN_REPLIES)){
            return false;
        }
        return bool;
    }
    public static boolean enableForceHD(){
        return Utils.getBooleanPerf(Settings.TIMELINE_ENABLE_VID_FORCE_HD);
    }

    public static boolean hideNudgeButton() {
        return Utils.getBooleanPerf(Settings.TIMELINE_HIDE_NUDGE_BUTTON);
    }

    public static boolean hideAds() {
        return Utils.getBooleanPerf(Settings.ADS_HIDE_PROMOTED_POSTS);
    }

    public static boolean hideWTF() {
        return Utils.getBooleanPerf(Settings.ADS_HIDE_WHO_TO_FOLLOW);
    }

    public static boolean hideCTS() {
        return Utils.getBooleanPerf(Settings.ADS_HIDE_CREATORS_TO_SUB);
    }

    public static boolean hideCTJ() {
        return Utils.getBooleanPerf(Settings.ADS_HIDE_COMM_TO_JOIN);
    }

    public static boolean hideRBMK() {
        return Utils.getBooleanPerf(Settings.ADS_HIDE_REVISIT_BMK);
    }

    public static boolean hideTopPeopleSearch() {
        return Utils.getBooleanPerf(Settings.ADS_HIDE_TOP_PEOPLE_SEARCH);
    }

    public static boolean removePremiumUpsell() {return !Utils.getBooleanPerf(Settings.ADS_REMOVE_PREMIUM_UPSELL);
    }

    public static boolean hideRPinnedPosts() {
        return Utils.getBooleanPerf(Settings.ADS_HIDE_REVISIT_PINNED_POSTS);
    }

    public static boolean hideDetailedPosts() {
        return Utils.getBooleanPerf(Settings.ADS_HIDE_DETAILED_POSTS);
    }

    public static boolean hidePremiumPrompt() {
        return Utils.getBooleanPerf(Settings.ADS_HIDE_PREMIUM_PROMPT);
    }

    public static boolean enableUndoPosts() {
        return Utils.getBooleanPerf(Settings.PREMIUM_UNDO_POSTS);
    }

    public static boolean enableForcePip() {
        return Utils.getBooleanPerf(Settings.PREMIUM_ENABLE_FORCE_PIP);
    }

    public static boolean enableDebugMenu() {
        return Utils.getBooleanPerf(Settings.MISC_DEBUG_MENU);
    }
    public static boolean hideSocialProof() {
        return Utils.getBooleanPerf(Settings.MISC_HIDE_SOCIAL_PROOF);
    }

    private static ArrayList getList(String key){
        ArrayList<String> arrayList = new ArrayList<String>();
        try{
            Set<String> ch = Utils.getSetPerf(key,null);
            if(!ch.isEmpty()) {
                arrayList = new ArrayList<String>(ch);
            }
        }catch (Exception e){}
        return arrayList;
    }
    public static ArrayList customProfileTabs() {
        return getList(Settings.CUSTOM_PROFILE_TABS.key);
    }

    public static ArrayList customSidebar() {
        return getList(Settings.CUSTOM_SIDEBAR_TABS.key);
    }
    public static ArrayList customExploreTabs() {
        return getList(Settings.CUSTOM_EXPLORE_TABS.key);
    }

    public static ArrayList customNavbar() {
        return getList(Settings.CUSTOM_NAVBAR_TABS.key);
    }

    public static ArrayList inlineBar() {
        return getList(Settings.CUSTOM_INLINE_TABS.key);
    }

    public static ArrayList searchTabs() {
        return getList(Settings.CUSTOM_SEARCH_TABS.key);
    }

    public static String defaultReplySortFilter() {
        String sortfilter = Utils.getStringPref(Settings.CUSTOM_DEF_REPLY_SORTING);
        if(sortfilter.equals("LastPostion")){
            sortfilter = Utils.getStringPref(Settings.REPLY_SORTING_LAST_FILTER);
        }
        return sortfilter;
    }

    public static void setReplySortFilter(String sortfilter) {
        sortfilter = sortfilter.length()>0?sortfilter:"Relevance";
        Utils.setStringPref(Settings.REPLY_SORTING_LAST_FILTER.key,sortfilter);
    }

    public static ArrayList customSearchTypeAhead() {
        return getList(Settings.CUSTOM_SEARCH_TYPE_AHEAD.key);
    }

    public static int nativeDownloaderFileNameType() {
        return Integer.parseInt(Utils.getStringPref(Settings.VID_NATIVE_DOWNLOADER_FILENAME));
    }


    //end
}