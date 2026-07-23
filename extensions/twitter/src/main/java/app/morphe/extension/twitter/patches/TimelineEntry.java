/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches;

import com.twitter.model.json.timeline.urt.JsonTimelineEntry;
import com.twitter.model.json.core.JsonSensitiveMediaWarning;
import com.twitter.model.json.timeline.urt.JsonTimelineModuleItem;
import app.morphe.extension.twitter.Pref;
import app.morphe.extension.twitter.settings.SettingsStatus;
import app.morphe.extension.twitter.entity.Video;
import com.x.models.timelines.items.UrtTimelineItem;
import com.x.models.timelines.items.UrtTimelineModuleItem;
import com.x.models.timelines.items.UrtTimelinePost;
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
            if ((entryId.contains("promoted") || entryId.startsWith("ad-") || entryId.contains("-ad-") || (entryId2.equals("conversationthread") && split.length == 3)) && hideAds) {
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
            PikoUtils.logger(ex);
        }

        return videoEnities;
    }

    private static Class<?>[] getAllInterfaces(Object obj) {
        java.util.Set<Class<?>> set = new java.util.HashSet<>();
        Class<?> current = obj.getClass();
        while (current != null) {
            for (Class<?> iface : current.getInterfaces()) {
                set.add(iface);
            }
            current = current.getSuperclass();
        }
        return set.toArray(new Class<?>[0]);
    }

    @SuppressWarnings("unchecked")
    private static Object wrapAsImmutableList(final List<Object> filteredList, final Object originalList) {
        Class<?>[] interfaces = getAllInterfaces(originalList);
        if (interfaces.length == 0) {
            return java.util.Collections.unmodifiableList(filteredList);
        }

        return java.lang.reflect.Proxy.newProxyInstance(
            originalList.getClass().getClassLoader(),
            interfaces,
            new java.lang.reflect.InvocationHandler() {
                @Override
                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
                    String name = method.getName();
                    if ("equals".equals(name) && args != null && args.length == 1) {
                        return proxy == args[0] || filteredList.equals(args[0]);
                    }
                    if ("hashCode".equals(name) && (args == null || args.length == 0)) {
                        return filteredList.hashCode();
                    }
                    if ("toString".equals(name) && (args == null || args.length == 0)) {
                        return filteredList.toString();
                    }
                    Object res = method.invoke(filteredList, args);
                    if ("subList".equals(name) && res instanceof List) {
                        return wrapAsImmutableList((List<Object>) res, originalList);
                    }
                    return res;
                }
            }
        );
    }

    public static Object filterUrtTimelineItems(Object itemsList) {
        if (!hideAds || itemsList == null) return itemsList;

        try {
            List<Object> filteredList = new ArrayList<>();
            Iterable<?> iterable = (Iterable<?>) itemsList;
            int totalCount = 0;

            for (Object itemObj : iterable) {
                totalCount++;
                if (itemObj == null) continue;

                boolean isAd = false;
                UrtTimelineItem item = null;
                if (itemObj instanceof UrtTimelineItem) {
                    item = (UrtTimelineItem) itemObj;
                } else if (itemObj instanceof UrtTimelineModuleItem) {
                    item = ((UrtTimelineModuleItem) itemObj).getItem();
                }

                if (item != null) {
                    // 1. Check entryId using existing filter helper
                    String entryId = item.getEntryId();
                    if (entryId != null && isEntryIdRemove(entryId)) {
                        isAd = true;
                    }

                    // 2. Check promotedMetadata on UrtTimelinePost
                    if (!isAd && item instanceof UrtTimelinePost) {
                        UrtTimelinePost post = (UrtTimelinePost) item;
                        if (post.getPromotedMetadata() != null) {
                            isAd = true;
                        }
                    }

                    // 3. Check clientEventInfo for promoted content
                    if (!isAd) {
                        Object clientEventInfo = item.getClientEventInfo();
                        if (clientEventInfo != null && clientEventInfo.toString().toLowerCase(java.util.Locale.ROOT).contains("promoted")) {
                            isAd = true;
                        }
                    }
                }

                if (!isAd) {
                    filteredList.add(itemObj);
                }
            }

            if (filteredList.size() == totalCount) {
                return itemsList;
            }

            return wrapAsImmutableList(filteredList, itemsList);

        } catch (Exception e) {
            PikoUtils.logger(e);
            return itemsList;
        }
    }

//end
}
