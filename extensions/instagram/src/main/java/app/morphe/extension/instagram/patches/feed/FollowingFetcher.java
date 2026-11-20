/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.feed;

import android.app.Activity;

import com.instagram.common.session.UserSession;
import com.instagram.common.typedurl.ImageUrl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/** Loads the accounts the current user follows, using Instagram's own authenticated requests. */
@SuppressWarnings("unused")
public final class FollowingFetcher {

    public interface Listener {
        void onProgress(int loaded);

        void onLoaded(List<FeedWhitelist.Account> following);

        void onError();
    }

    private static final String FOLLOWING_PATH = "friendships/%s/following/";
    private static final int MAX_PAGES = 500;

    private static List<FeedWhitelist.Account> cache;
    private static String cacheOwnerId;

    private FollowingFetcher() {
    }

    public static void load(Activity activity, boolean refresh, Listener listener) {
        Object session = getActivitySession(activity);
        if (!(session instanceof UserSession)) {
            Logger.printDebug(() -> "No user session for the following list");
            listener.onError();
            return;
        }
        UserSession userSession = (UserSession) session;
        String viewerId = userSession.getUserId();
        synchronized (FollowingFetcher.class) {
            if (!refresh && cache != null && viewerId.equals(cacheOwnerId)) {
                listener.onLoaded(cache);
                return;
            }
        }

        Utils.runOnBackgroundThread(() -> {
            try {
                List<FeedWhitelist.Account> following = Collections.unmodifiableList(
                        fetchAll(userSession, viewerId, listener));
                synchronized (FollowingFetcher.class) {
                    cache = following;
                    cacheOwnerId = viewerId;
                }
                Utils.runOnMainThread(() -> listener.onLoaded(following));
            } catch (Throwable e) {
                // This runs off the main thread, where anything uncaught would close the app.
                Logger.printException(() -> "Failed to load followed accounts", e);
                Utils.runOnMainThread(listener::onError);
            }
        });
    }

    private static List<FeedWhitelist.Account> fetchAll(
            UserSession session, String viewerId, Listener listener) throws Exception {
        String path = String.format(FOLLOWING_PATH, viewerId);
        Map<String, FeedWhitelist.Account> accounts = new LinkedHashMap<>();
        String maxId = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            FollowingRequestCallback callback = new FollowingRequestCallback();
            // Runs inline on this thread, so the callback has its response when this returns.
            executeFollowingRequest(session, path, maxId, callback);
            Object response = callback.response;
            if (response == null) throw new IllegalStateException("Following request failed");

            List<?> users = getResponseUsers(response);
            if (users != null) {
                for (Object user : users) {
                    FeedWhitelist.Account account = toAccount(user);
                    if (account != null) accounts.putIfAbsent(account.id, account);
                }
            }
            int loaded = accounts.size();
            Utils.runOnMainThread(() -> listener.onProgress(loaded));

            String nextMaxId = getResponseNextMaxId(response);
            if (nextMaxId == null || nextMaxId.isEmpty() || nextMaxId.equals(maxId)) break;
            maxId = nextMaxId;
        }
        return new ArrayList<>(accounts.values());
    }

    private static FeedWhitelist.Account toAccount(Object user) {
        try {
            UserData data = new UserData(user);
            String id = data.getUserId();
            if (id == null || id.isEmpty()) return null;
            String pictureUrl = null;
            try {
                ImageUrl picture = data.getLowResProfilePicture();
                if (picture != null) pictureUrl = picture.getUrl();
            } catch (Throwable ignored) {
                // The list stays usable without pictures.
            }
            return new FeedWhitelist.Account(id, data.getUsername(), data.getFullName(), pictureUrl,
                    false, false);
        } catch (Exception e) {
            Logger.printException(() -> "Failed to read a followed account", e);
            return null;
        }
    }

    /** Replaced at patch time with Instagram's activity session getter. */
    private static Object getActivitySession(Activity activity) {
        return null;
    }

    /** Replaced at patch time: builds Instagram's user list request and runs it inline. */
    private static void executeFollowingRequest(
            UserSession session, String path, String maxId, FollowingRequestCallback callback) {
    }

    /** Replaced at patch time with the user list response's users getter. */
    private static List<?> getResponseUsers(Object response) {
        return null;
    }

    /** Replaced at patch time with the user list response's next page cursor getter. */
    private static String getResponseNextMaxId(Object response) {
        return null;
    }
}
