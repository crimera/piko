/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.feed;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.graphics.Color;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.Logger;

import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.instagram.entity.InstagramBottomSheet;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.constants.PostType;
import app.morphe.extension.crimera.downloader.MediaType;

import com.instagram.common.session.UserSession;

public class MoreOptionsOnPostPatch {

    /**
     * @param surfaceHint When known at the call site (reel player, story viewer), pass the
     *                    exact {@link PostType} so the label doesn't depend on reading Instagram's
     *                    obfuscated post-type field, which is unreliable for reels embedded in feed
     *                    (see {@link MediaData#getPostType()}). Pass {@code null} to auto-detect.
     */
    public static void postMoreOptions(Context context, UserSession userSession, Object mediaObject, int currentMediaIndex, PostType surfaceHint) {
        try {
            MediaData mediaInfo = new MediaData(mediaObject, userSession);
            int position = currentMediaIndex < 0 ? 0 : currentMediaIndex;
            MediaData currentMediaData = mediaInfo.getMediaAt(position);

            boolean hasAudio = currentMediaData.hasAudio();
            boolean isVideo = currentMediaData.isVideo();
            int carouselSize = mediaInfo.getCarouselSize();
            boolean isCarousel = carouselSize > 1;

            PostType postType = surfaceHint != null ? surfaceHint : mediaInfo.getPostType();

            InstagramBottomSheet sheet = new InstagramBottomSheet(context);
            sheet.setTitle(str("piko_more_options"));

            sheet.addItem(str("piko_copy_post_description"),
                    InstagramBottomSheet.IconSpec.document(Color.parseColor("#5B4EE0")),
                    () -> {
                        String description = mediaInfo.getDescriptionText();
                        if (description != null && description.length() > 0) {
                            Utils.setClipboard(description);
                            Utils.showToastShort(str("piko_copied"));
                        }
                    });

            boolean isStoryOrReel = postType.equals(PostType.REEL) || postType.equals(PostType.STORY);

            // Carousels are downloaded per-slide (current photo/video) or fully (all slides)
            // below, so the generic post/reel/story item only applies to single-media posts.
            if (!isCarousel) {
                String downloadLabel;
                if (postType.equals(PostType.REEL)) {
                    downloadLabel = str("piko_download_reel");
                } else if (postType.equals(PostType.STORY)) {
                    downloadLabel = str("piko_download_story");
                } else {
                    downloadLabel = str("piko_download_post");
                }

                sheet.addItem(downloadLabel,
                        InstagramBottomSheet.IconSpec.download(Color.parseColor("#2E8B3D")),
                        () -> DownloadUtils.downloadMedia(context, mediaInfo, position, MediaType.ANY));
            }

            if (isStoryOrReel) {
                // Unchanged from before: reel/story keep the plain "current image" item.
                sheet.addItem(str("piko_download_current_image"),
                        InstagramBottomSheet.IconSpec.photo(Color.parseColor("#2F6FE0")),
                        () -> DownloadUtils.downloadMedia(context, mediaInfo, position, MediaType.IMAGE));
            } else {
                // Post/carousel: dynamic photo-vs-video label + matching download type.
                String currentMediaLabel = isVideo ? str("piko_download_current_video") : str("piko_download_current_photo");
                MediaType currentMediaType = isVideo ? MediaType.VIDEO : MediaType.IMAGE;
                sheet.addItem(currentMediaLabel,
                        InstagramBottomSheet.IconSpec.photo(Color.parseColor("#2F6FE0")),
                        () -> DownloadUtils.downloadMedia(context, mediaInfo, position, currentMediaType));
            }

            if (hasAudio) {
                sheet.addItem(str("piko_download_audio"),
                        InstagramBottomSheet.IconSpec.music(Color.parseColor("#C2185B")),
                        () -> DownloadUtils.downloadMedia(context, mediaInfo, position, MediaType.AUDIO));
            }

            if (isCarousel) {
                sheet.addItem(str("piko_download_all_slides"),
                        InstagramBottomSheet.IconSpec.layers(Color.parseColor("#C77B1E")),
                        () -> DownloadUtils.downloadMedia(context, mediaInfo, -1, MediaType.ANY));
            }

            sheet.show();

        } catch (Exception e) {
            Logger.printException(() -> "postMoreOptions failure", e);
        }
    }

    public static void postMoreOptions(Context context, UserSession userSession, Object mediaObject, int currentMediaIndex) {
        postMoreOptions(context, userSession, mediaObject, currentMediaIndex, null);
    }

}