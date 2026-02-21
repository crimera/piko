package app.morphe.extension.twitter.patches;

import com.twitter.model.json.timeline.urt.JsonTimelineEntry;
import com.twitter.model.json.core.JsonSensitiveMediaWarning;
import com.twitter.model.json.timeline.urt.JsonTimelineModuleItem;
import app.morphe.extension.twitter.Pref;
import app.morphe.extension.twitter.settings.SettingsStatus;
import app.morphe.extension.twitter.entity.Video;
import java.util.List;
import java.util.ArrayList;
import app.morphe.extension.twitter.Utils;

public class TimelineEntry {
    public static final boolean hideAds;
    private static final boolean hideWTF,hideCTS,hideCTJ,hideDetailedPosts,hideRBMK,hidePinnedPosts,hidePremiumPrompt,showSensitiveMedia,hideTopPeopleSearch,hideTodaysNews;
    static {
        hideAds = (Pref.hideAds() && SettingsStatus.hideAds);
        hideWTF = (Pref.hideWTF() && SettingsStatus.hideWTF);
        hideCTS = (Pref.hideCTS() && SettingsStatus.hideCTS);
        hideCTJ = (Pref.hideCTJ() && SettingsStatus.hideCTJ);
        hideDetailedPosts = (Pref.hideDetailedPosts() && SettingsStatus.hideDetailedPosts);
        hideRBMK = (Pref.hideRBMK() && SettingsStatus.hideRBMK);
        hidePinnedPosts = (Pref.hideRPinnedPosts() && SettingsStatus.hideRPinnedPosts);
        hidePremiumPrompt = (Pref.hidePremiumPrompt() && SettingsStatus.hidePremiumPrompt);
        showSensitiveMedia = Pref.showSensitiveMedia();
        hideTopPeopleSearch = (Pref.hideTopPeopleSearch() && SettingsStatus.hideTopPeopleSearch);
        hideTodaysNews = (Pref.hideTodaysNews() && SettingsStatus.hideTodaysNews);
    }

    private static boolean isEntryIdRemove(String entryId) {
        String[] split = entryId.split("-");
        String entryId2 = split[0];
        if (!entryId2.equals("cursor") && !entryId2.equals("Guide") && !entryId2.startsWith("semantic_core")) {
            if (entryId.contains("promoted") || (entryId2.equals("conversationthread") && split.length == 3) && hideAds) {
                return true;
            }
            if ((entryId2.equals("superhero") || entryId2.equals("eventsummary")) && hideAds) {
                return true;
            }
            if (entryId.contains("rtb") && hideAds) {
                return true;
            }
            if (entryId2.equals("tweetdetailrelatedtweets") && hideDetailedPosts) {
                return true;
            }
            if (entryId2.equals("bookmarked") && hideRBMK) {
                return true;
            }
            if (entryId.startsWith("community-to-join") && hideCTJ) {
                return true;
            }
            if (entryId.startsWith("who-to-follow") && hideWTF) {
                return true;
            }
            if (entryId.startsWith("who-to-subscribe") && hideCTS) {
                return true;
            }
            if (entryId.startsWith("pinned-tweets") && hidePinnedPosts) {
                return true;
            }
            if (entryId.startsWith("messageprompt-") && hidePremiumPrompt) {
                return true;
            }
            if ((entryId.startsWith("main-event-") || entryId2.equals("pivot")) && hideAds) {
                return true;
            }
            if (entryId2.equals("toptabsrpusermodule") && hideTopPeopleSearch) {
                return true;
            }
            if (entryId.startsWith("stories") && hideTodaysNews) {
                return true;
            }
        }
        return false;
    }
    public static JsonTimelineEntry checkEntry(JsonTimelineEntry jsonTimelineEntry) {
        try {
            String entryId = jsonTimelineEntry.a;
            if(isEntryIdRemove(entryId)){
                return null;
            }
        } catch (Exception unused) {

        }
        return jsonTimelineEntry;
    }
    public static JsonTimelineModuleItem checkEntry(JsonTimelineModuleItem jsonTimelineModuleItem) {
        try {
            String entryId = jsonTimelineModuleItem.a;
            if(isEntryIdRemove(entryId)){
                return null;
            }
        } catch (Exception unused) {

        }
        return jsonTimelineModuleItem;
    }
    public static JsonSensitiveMediaWarning sensitiveMedia(JsonSensitiveMediaWarning jsonSensitiveMediaWarning) {
        try {
            if(showSensitiveMedia){
                jsonSensitiveMediaWarning.a = false;
                jsonSensitiveMediaWarning.b = false;
                jsonSensitiveMediaWarning.c = false;
            }
        } catch (Exception unused) {

        }
        return jsonSensitiveMediaWarning;
    }
    public static boolean hidePromotedTrend(Object data) {
        if (data != null && hideAds) {
            return true;
        }
        return false;
    }

    public static List timelineVideos(List videoEnities){
        int maxBitrate = 0;
        Object maxVideoObject = null;
        try{
            if(Pref.ENABLE_FORCE_HD) {
                for (Object vidObj : videoEnities) {
                    Video vid = new Video(vidObj);
                    String mediaExt = vid.getExtension();
                    if (!(mediaExt.equals("mp4"))) continue;

                    int bitrate = vid.getBitrate();
                    if(bitrate<maxBitrate) continue;
                    maxBitrate = bitrate;
                    maxVideoObject = vidObj;
                }
                if (maxVideoObject != null) {
                    ArrayList result = new ArrayList();
                    result.add(maxVideoObject);
                    return result;
                }
            }

        }catch(Exception ex){
            Utils.logger(ex);
        }

        return videoEnities;
    }

//end
}
