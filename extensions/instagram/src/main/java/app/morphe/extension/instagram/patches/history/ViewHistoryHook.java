/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.history;

import android.content.Context;
import android.content.Intent;

import com.instagram.common.session.UserSession;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.PostType;
import app.morphe.extension.instagram.db.PikoHistoryDb;
import app.morphe.extension.instagram.entity.ImageData;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.patches.Links;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;

@SuppressWarnings("unused")
public class ViewHistoryHook {

    private static final int THUMBNAIL_MIN_WIDTH = 360;
    private static final ExecutorService DB_WRITER = Executors.newSingleThreadExecutor();

    public static void openHistory(Context ctx) {
        try {
            if (ctx == null) ctx = PikoUtils.getContext();
            if (ctx == null) return;
            Intent intent = new Intent(ctx, HistoryActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Logger.printException(() -> "openHistory failure", e);
        }
    }

    /** Records a viewed item. Injected into the feed, Reels and Stories viewers. */
    public static void logMediaView(Object mediaObject, UserSession userSession, int carouselIndex) {
        try {
            if (mediaObject == null || !Pref.saveViewHistory()) return;

            MediaData mediaData = new MediaData(mediaObject, userSession);
            String mediaPk = mediaData.getPostID();
            if (mediaPk == null || "0".equals(mediaPk)) return;
            String postType = mediaData.getPostType().name();

            UserData user = orNull(mediaData::getUserData);
            String ownerUsername = user != null ? orNull(user::getUsername) : null;
            String thumbUrl = orNull(() -> thumbnailUrl(mediaData));
            String caption = orNull(mediaData::getDescriptionText);
            String permalink = mediaData.getPostType() == PostType.STORY
                    ? storyLink(mediaData, mediaPk, carouselIndex)
                    : Links.generatePostLink(mediaData, carouselIndex);

            DB_WRITER.execute(() -> {
                try {
                    PikoHistoryDb.getInstance(PikoUtils.getContext())
                            .logView(mediaPk, postType, ownerUsername, thumbUrl, caption, permalink);
                } catch (Exception e) {
                    Logger.printException(() -> "View history write failure", e);
                }
            });
        } catch (Exception e) {
            Logger.printException(() -> "logMediaView failure", e);
        }
    }

    /**
     * The web link for a story only opens the owner's profile and leaves the story ring
     * loading forever. The internal link goes straight to the story viewer at this item.
     */
    private static String storyLink(MediaData mediaData, String mediaPk, int carouselIndex) throws Exception {
        String ownerId = mediaData.getOwnerID();
        if (ownerId == null) return Links.generatePostLink(mediaData, carouselIndex);
        return "instagram://stories?user_id=" + ownerId + "&media_id=" + mediaPk;
    }

    private static <T> T orNull(Callable<T> getter) {
        try {
            return getter.call();
        } catch (Exception e) {
            return null;
        }
    }

    /** The smallest image variant that is still sharp in a half-width grid card. */
    @SuppressWarnings("unchecked")
    private static String thumbnailUrl(MediaData mediaData) throws Exception {
        List<ImageData> variants = mediaData.getImageVariants();
        if (variants == null || variants.isEmpty()) return null;
        ImageData best = null;
        int bestWidth = Integer.MAX_VALUE;
        for (ImageData variant : variants) {
            Integer width = variant.getWidth();
            if (width != null && width >= THUMBNAIL_MIN_WIDTH && width < bestWidth) {
                best = variant;
                bestWidth = width;
            }
        }
        return (best != null ? best : variants.get(0)).getUrl();
    }
}
