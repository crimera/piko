package app.revanced.integrations.twitter.patches;

import com.twitter.model.json.timeline.urt.JsonTimelineEntry;
import com.twitter.model.json.timeline.urt.JsonTimelineModuleItem;

import app.revanced.integrations.twitter.Pref;
import app.revanced.integrations.twitter.settings.SettingsStatus;

public class TimelineEntry {
    private static final boolean hideAds,hideGAds,hideWTF,hideCTS,hideCTJ,hideDetailedPosts,hideRBMK,hidePinnedPosts,hidePremiumPrompt,hideMainEvent,hideSuperheroEvent,hideVideosForYou;
    static {
        hideAds = (Pref.hideAds() && SettingsStatus.hideAds);
        hideGAds = (Pref.hideGoogleAds() && SettingsStatus.hideGAds);
        hideWTF = (Pref.hideWTF() && SettingsStatus.hideWTF);
        hideCTS = (Pref.hideCTS() && SettingsStatus.hideCTS);
        hideCTJ = (Pref.hideCTJ() && SettingsStatus.hideCTJ);
        hideDetailedPosts = (Pref.hideDetailedPosts() && SettingsStatus.hideDetailedPosts);
        hideRBMK = (Pref.hideRBMK() && SettingsStatus.hideRBMK);
        hidePinnedPosts = (Pref.hideRPinnedPosts() && SettingsStatus.hideRPinnedPosts);
        hidePremiumPrompt = (Pref.hidePremiumPrompt() && SettingsStatus.hidePremiumPrompt);
        hideMainEvent = (Pref.hideMainEvent() && SettingsStatus.hideMainEvent);
        hideSuperheroEvent = (Pref.hideSuperheroEvent() && SettingsStatus.hideSuperheroEvent);
        hideVideosForYou = (Pref.hideVideosForYou() && SettingsStatus.hideVideosForYou);
    }


    private static boolean isEntryIdRemove(String entryId) {
        String[] split = entryId.split("-");
        String entryId2 = split[0];
        if (!entryId2.equals("cursor") && !entryId2.equals("Guide") && !entryId2.startsWith("semantic_core")) {
            if (entryId2.equals("promoted") || ((entryId2.equals("conversationthread") && split.length == 3)) && hideAds) {
                return true;
            }
            if (entryId2.equals("superhero") && hideSuperheroEvent) {
                return true;
            }
            if (entryId2.equals("rtb") && hideGAds) {
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
            if (entryId.startsWith("main-event-") && hideMainEvent) {
                return true;
            }
            if (entryId2.equals("tweet") && entryId.contains("-tweet-") && hideVideosForYou) {
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

//end
}