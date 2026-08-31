/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches;

import static app.morphe.extension.shared.StringRef.str;

import android.text.TextUtils;
import android.view.View;

import com.x.models.interstitial.BlurImageInterstitial;
import com.twitter.model.json.mediavisibility.JsonBlurredImageInterstitial;
import com.twitter.model.json.timeline.urt.JsonTimelineEntry;
import com.twitter.model.json.core.JsonSensitiveMediaWarning;
import com.twitter.model.json.timeline.urt.JsonTimelineModuleItem;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.twitter.Pref;
import app.morphe.extension.twitter.settings.SettingsStatus;
import app.morphe.extension.twitter.entity.Video;

import java.util.List;
import java.util.ArrayList;
import app.morphe.extension.crimera.PikoUtils;

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
            if (entryId2.startsWith("tweetdetail") && hideDetailedPosts) {
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
            if (isEntryIdRemove(entryId)) {
                return null;
            }
        } catch (Exception ignored) {
        }
        return jsonTimelineEntry;
    }
    public static JsonTimelineModuleItem checkEntry(JsonTimelineModuleItem jsonTimelineModuleItem) {
        try {
            String entryId = jsonTimelineModuleItem.a;
            if (isEntryIdRemove(entryId)) {
                return null;
            }
        } catch (Exception ignored) {
        }
        return jsonTimelineModuleItem;
    }
    // Interface to reset obfuscated fields
    // This is one of the methods to avoid using Java Reflection, which has high overhead
    public interface JsonBlurredImageInterstitialPatchInterface {
        // Method is added during patching
        void patch_showSensitiveMedia();
    }
    public interface JsonSensitiveMediaWarningPatchInterface {
        // Method is added during patching
        void patch_showSensitiveMedia();
    }
    public static JsonBlurredImageInterstitial showSensitiveMedia(JsonBlurredImageInterstitialPatchInterface patchInterface) {
        if (showSensitiveMedia && patchInterface != null) {
            patchInterface.patch_showSensitiveMedia();
        }
        return (JsonBlurredImageInterstitial) patchInterface;
    }
    public static JsonSensitiveMediaWarning showSensitiveMedia(JsonSensitiveMediaWarningPatchInterface patchInterface) {
        if (showSensitiveMedia && patchInterface != null) {
            patchInterface.patch_showSensitiveMedia();
        }
        return (JsonSensitiveMediaWarning) patchInterface;
    }
    public static BlurImageInterstitial showSensitiveMedia(BlurImageInterstitial interstitial) {
        return showSensitiveMedia ? null : interstitial;
    }
    public static void showSensitiveImage(View view) {
        if (showSensitiveMedia && view != null) {
            // Click the 'Show' button on the timeline to make the blurred image visible
            Utils.runOnMainThread(view::callOnClick);
        }
    }
    // Caution: This profile may include potentially sensitive content
    private static final String sensitiveProfileHeader = str("profile_interstitial_sensitive_media_header");
    private static boolean isSensitiveProfile = false;
    public static int setSensitiveProfileWarningDialogTitle(String title, int visibility) {
        if (showSensitiveMedia) {
            // Check the title of the alert dialog to prevent other profile warnings (such as racism or terrorism) from closing
            isSensitiveProfile = TextUtils.equals(sensitiveProfileHeader, title);

            if (isSensitiveProfile) {
                // If it is a general sensitive media warning, hide the alert dialog
                return View.GONE;
            }
        }

        return visibility;
    }
    public static void showSensitiveProfile(View view) {
        if (isSensitiveProfile && view != null) {
            // If it is a general sensitive media warning, also click the dismiss button on the alert dialog
            // This is to prevent the UI from breaking due to incorrect WindowInsets calculations, even though the alert dialog is hidden
            Utils.runOnMainThread(view::callOnClick);
        }
    }
    public static boolean hidePromotedTrend(Object data) {
        return data != null && hideAds;
    }

    public static List<Object> timelineVideos(List<Object> videoEntities){
        int maxBitrate = 0;
        Object maxVideoObject = null;
        try{
            if(Pref.ENABLE_FORCE_HD) {
                for (Object vidObj : videoEntities) {
                    Video vid = new Video(vidObj);
                    String mediaExt = vid.getExtension();
                    if (!(mediaExt.equals("mp4"))) continue;

                    int bitrate = vid.getBitrate();
                    if(bitrate<maxBitrate) continue;
                    maxBitrate = bitrate;
                    maxVideoObject = vidObj;
                }
                if (maxVideoObject != null) {
                    ArrayList<Object> result = new ArrayList<>();
                    result.add(maxVideoObject);
                    return result;
                }
            }

        } catch (Exception ex) {
            PikoUtils.logger(ex);
        }

        return videoEntities;
    }

//end
}
