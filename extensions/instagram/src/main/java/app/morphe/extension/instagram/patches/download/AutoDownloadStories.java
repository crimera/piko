/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.download;

import android.content.Context;
import android.content.SharedPreferences;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.shared.Logger;

/**
 * Selectively auto-downloads stories, but only for accounts explicitly
 * marked via the profile "More options" toggle (see ProfileMoreOption).
 * Everyone else's stories are left untouched, whether followed or not.
 */
public class AutoDownloadStories {

    private static final String PREF_NAME = "piko_auto_download_stories_seen";

    /**
     * Called from AutoDownloadStoriesPatch's hook on ReelItem -- fires
     * automatically whenever a story is rendered on screen (tray or viewer),
     * no button press or explicit "open" step required beyond that.
     *
     * @param userObject  raw Instagram User object (the story owner)
     * @param mediaObject raw Instagram Media object (the story content)
     */
    public static void checkAndDownloadFromReelItem(Context context, Object userObject, Object mediaObject) {
        try {
            if (userObject == null || mediaObject == null) return;

            UserData userData = new UserData(userObject);
            String ownerUserId = userData.getUserId();
            if (ownerUserId == null) return;

            // Selective: only story owners explicitly whitelisted via the
            // profile "More options" toggle get auto-downloaded.
            if (!Pref.isAutoDownloadTarget(ownerUserId)) return;

            MediaData mediaData = new MediaData(mediaObject);
            String mediaId = mediaData.getMediaPkId();
            String mediaUrl = mediaData.getMediaLink();
            String username = userData.getUsername();

            if (mediaId == null || mediaUrl == null) return;

            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            if (prefs.contains(mediaId)) return; // already downloaded, skip duplicate

            String subFolder = DownloadUtils.getSubfolderName(username);
            String fileName = username + "_" + mediaId;

            DownloadUtils.downloadMediaUrl(context, mediaUrl, subFolder, fileName);

            prefs.edit().putBoolean(mediaId, true).apply();
        } catch (Exception e) {
            Logger.printException(() -> "Error at AutoDownloadStories.checkAndDownloadFromReelItem", e);
        }
    }
}
