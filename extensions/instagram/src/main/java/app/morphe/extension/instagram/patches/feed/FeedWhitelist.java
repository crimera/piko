/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.feed;

import static app.morphe.extension.instagram.utils.IgStr.str;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.entity.ReelResponseItem;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.SettingsRestart;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.shared.Logger;

/**
 * Followed accounts whose home feed posts and stories are kept (whitelist) or hidden (blacklist).
 * Feed and stories are selected separately, and one with nothing selected is not filtered.
 */
@SuppressWarnings("unused")
public final class FeedWhitelist {

    public static final String MODE_WHITELIST = "whitelist";
    public static final String MODE_BLACKLIST = "blacklist";

    public static final class Account {
        public final String id;
        public final String username;
        public final String fullName;
        /** Signed CDN links expire, so the picture is only kept for the current list. */
        public final String pictureUrl;
        public final boolean feed;
        public final boolean story;

        public Account(String id, String username, String fullName, String pictureUrl,
                       boolean feed, boolean story) {
            this.id = id;
            this.username = username == null ? "" : username;
            this.fullName = fullName == null || fullName.equals(username) ? "" : fullName;
            this.pictureUrl = pictureUrl;
            this.feed = feed;
            this.story = story;
        }

        public Account withTargets(boolean feed, boolean story) {
            return new Account(id, username, fullName, pictureUrl, feed, story);
        }
    }

    private static String parsedValue;
    private static Map<String, Account> selected = Collections.emptyMap();
    private static Set<String> feedIds = Collections.emptySet();
    private static Set<String> storyIds = Collections.emptySet();

    private FeedWhitelist() {
    }

    /** Accounts selected for the feed or stories by id, in the order they were picked. */
    public static synchronized Map<String, Account> selected() {
        String value = SharedPref.getStringPref(Settings.FEED_WHITELIST);
        if (!value.equals(parsedValue)) {
            Map<String, Account> accounts = parse(value);
            Set<String> feed = new HashSet<>();
            Set<String> story = new HashSet<>();
            for (Account account : accounts.values()) {
                if (account.feed) feed.add(account.id);
                if (account.story) story.add(account.id);
            }
            selected = Collections.unmodifiableMap(accounts);
            feedIds = feed;
            storyIds = story;
            parsedValue = value;
        }
        return selected;
    }

    private static synchronized Set<String> feedIds() {
        selected();
        return feedIds;
    }

    private static synchronized Set<String> storyIds() {
        selected();
        return storyIds;
    }

    public static Account get(String id) {
        return selected().get(id);
    }

    /** Saves which of the feed and stories the account is selected for. */
    public static synchronized void set(Account account, boolean feed, boolean story) {
        Map<String, Account> accounts = new LinkedHashMap<>(selected());
        if (feed || story) {
            accounts.put(account.id, account.withTargets(feed, story));
        } else {
            accounts.remove(account.id);
        }
        save(accounts);
    }

    public static synchronized void clear() {
        save(Collections.emptyMap());
    }

    public static boolean isBlacklist() {
        return MODE_BLACKLIST.equals(SharedPref.getStringPref(Settings.FEED_WHITELIST_MODE));
    }

    public static void setBlacklist(boolean blacklist) {
        String key = Settings.FEED_WHITELIST_MODE.key;
        String previous = SharedPref.getStringPref(Settings.FEED_WHITELIST_MODE);
        String value = blacklist ? MODE_BLACKLIST : MODE_WHITELIST;
        if (SharedPref.setStringPref(key, value)) {
            SettingsRestart.markChanged(key, previous, value);
        }
    }

    public static String summary() {
        int feed = feedIds().size();
        int story = storyIds().size();
        if (feed == 0 && story == 0) return str("piko_feed_whitelist_summary_none");
        return str(isBlacklist() ? "piko_feed_whitelist_summary_blacklist" : "piko_feed_whitelist_summary_whitelist",
                feed, story);
    }

    /**
     * Injection point: an item about to be added to the home feed, from the network or the
     * on-device feed cache.
     */
    public static boolean isAllowedInFeed(Object item) {
        if (!SettingsStatus.feedWhitelist) return true;
        Set<String> ids = feedIds();
        if (ids.isEmpty()) return true;
        try {
            Object media = getFeedItemMedia(item);
            // Feed units without a post, such as the end of feed marker, are not filtered.
            if (media == null) return true;
            UserData owner = new MediaData(media).getUserDataWithoutUserSession();
            String ownerId = owner.getUserId();
            if (ownerId == null) return true;
            if (ids.contains(ownerId)) return !isBlacklist();
            // Only followed accounts are filtered, so suggested posts are left to Instagram.
            return isBlacklist() || !isFollowed(owner);
        } catch (Exception e) {
            Logger.printException(() -> "Failed to read the owner of a feed item", e);
            return true;
        }
    }

    /**
     * Injection point: the reels about to be added to the home story tray.
     * The parameters follow the hooked method's registers: the signed-in user, unused, the reels.
     */
    public static List<Object> filterStoryTray(Object viewer, String unused, List<Object> reels) {
        if (!SettingsStatus.feedWhitelist || reels == null || reels.isEmpty()) return reels;
        Set<String> ids = storyIds();
        if (ids.isEmpty()) return reels;

        String viewerId = null;
        try {
            if (viewer != null) viewerId = new UserData(viewer).getUserId();
        } catch (Exception e) {
            Logger.printException(() -> "Failed to read the signed-in user", e);
        }

        List<Object> filtered = new ArrayList<>(reels.size());
        for (Object reel : reels) {
            String ownerId = null;
            try {
                UserData owner = new ReelResponseItem(reel).getUserData();
                if (owner != null) ownerId = owner.getUserId();
            } catch (Exception e) {
                Logger.printException(() -> "Failed to read the owner of a story", e);
            }
            // Your own story and reels without a single owner, such as live rooms, stay.
            if (ownerId == null || ownerId.equals(viewerId) || isKept(ids, ownerId)) {
                filtered.add(reel);
            }
        }
        return filtered;
    }

    /** An unknown friendship counts as followed, so whitelisted feeds don't leak followed posts. */
    private static boolean isFollowed(UserData user) throws Exception {
        Boolean following = user.getUserFriendshipStatus().getMappings().get("following");
        return !Boolean.FALSE.equals(following);
    }

    private static boolean isKept(Set<String> ids, String ownerId) {
        return ids.contains(ownerId) != isBlacklist();
    }

    /** Replaced at patch time with the media getter of a home feed item. */
    private static Object getFeedItemMedia(Object item) {
        return null;
    }

    private static Map<String, Account> parse(String value) {
        Map<String, Account> accounts = new LinkedHashMap<>();
        if (value == null || value.isEmpty()) return accounts;
        try {
            JSONArray array = new JSONArray(value);
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.getJSONObject(i);
                String id = entry.optString("id");
                if (id.isEmpty()) continue;
                accounts.put(id, new Account(id, entry.optString("username"), entry.optString("full_name"),
                        null, entry.optBoolean("feed", true), entry.optBoolean("story", false)));
            }
        } catch (Exception e) {
            Logger.printException(() -> "Failed to read the feed whitelist", e);
        }
        return accounts;
    }

    private static void save(Map<String, Account> accounts) {
        String previous = SharedPref.getStringPref(Settings.FEED_WHITELIST);
        String value = "";
        try {
            if (!accounts.isEmpty()) {
                JSONArray array = new JSONArray();
                for (Account account : accounts.values()) {
                    array.put(new JSONObject()
                            .put("id", account.id)
                            .put("username", account.username)
                            .put("full_name", account.fullName)
                            .put("feed", account.feed)
                            .put("story", account.story));
                }
                value = array.toString();
            }
        } catch (Exception e) {
            Logger.printException(() -> "Failed to save the feed whitelist", e);
            return;
        }
        if (SharedPref.setStringPref(Settings.FEED_WHITELIST.key, value)) {
            SettingsRestart.markChanged(Settings.FEED_WHITELIST.key, previous, value);
        }
    }
}
