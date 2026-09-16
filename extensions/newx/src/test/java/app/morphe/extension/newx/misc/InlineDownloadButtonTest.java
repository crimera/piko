package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.shared.settings.BooleanSetting;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class InlineDownloadButtonTest {
    private static final String INLINE_DOWNLOAD_SETTING_ID =
            "newx.content.inline_download_button";
    private static final BooleanSetting INLINE_DOWNLOAD_SETTING = new BooleanSetting(
            INLINE_DOWNLOAD_SETTING_ID,
            true,
            false
    );

    @Before
    public void enableInlineDownloads() throws ReflectiveOperationException {
        settings().put(INLINE_DOWNLOAD_SETTING_ID, INLINE_DOWNLOAD_SETTING);
        INLINE_DOWNLOAD_SETTING.save(true);
    }

    @After
    public void removeInlineDownloadSetting() throws ReflectiveOperationException {
        settings().remove(INLINE_DOWNLOAD_SETTING_ID);
    }

    @Test
    public void singleMediaUsesRequestedTwitterFilename() {
        assertEquals(
                "jack_123456789.jpg",
                InlineDownloadButton.downloadFileName("jack", "123456789", "jpg", 0, 1)
        );
    }

    @Test
    public void downloadNotificationsIdentifyThePostAuthor() {
        assertEquals(
                "Download started — @jack",
                NewXInAppNotification.formatForUser("Download started", "jack")
        );
        assertEquals(
                "Already downloaded or queued — @jack",
                NewXInAppNotification.formatForUser("Already downloaded or queued", "@jack")
        );
    }

    @Test
    public void downloadNotificationOmitsMissingAuthor() {
        assertEquals(
                "Download started",
                NewXInAppNotification.formatForUser("Download started", " ")
        );
    }

    @Test
    public void multipleMediaUsesOneBasedSuffix() {
        assertEquals(
                "jack_123456789_1.jpg",
                InlineDownloadButton.downloadFileName("jack", "123456789", "jpg", 0, 4)
        );
        assertEquals(
                "jack_123456789_4.mp4",
                InlineDownloadButton.downloadFileName("jack", "123456789", "mp4", 3, 4)
        );
    }

    @Test
    public void filenameSegmentsAreSanitized() {
        assertEquals(
                "jack_user_post_id.jpg",
                InlineDownloadButton.downloadFileName("@jack/user", "post:id", "jpg", 0, 1)
        );
    }

    @Test
    public void thumbnailCacheUrlKeepsOriginalMediaUrl() {
        String sourceUrl = "https://pbs.twimg.com/media/example.jpg?format=jpg&name=orig";
        String media = "MediaContentImage(imageUrl=" + sourceUrl + ")";

        assertEquals(sourceUrl, InlineDownloadButton.thumbnailCacheUrlForMedia(media));
    }

    @Test
    public void temporarySuffixPrecedesExtension() {
        assertEquals(
                "jack_123456789_tmp.jpg",
                InlineDownloadButton.temporaryDownloadFileName("jack_123456789.jpg")
        );
        assertEquals(
                "download_tmp",
                InlineDownloadButton.temporaryDownloadFileName("download")
        );
    }

    @Test
    public void occupiedTargetIsSkipped() {
        String target = "jack_123456789.jpg";

        assertNull(InlineDownloadButton.resolveTargetFileName(
                target,
                InlineDownloadButton.ConflictBehavior.SKIP,
                Set.of(target)::contains
        ));
    }

    @Test
    public void mediaStoreRenamedAllocationIsTreatedAsOccupied() {
        assertTrue(InlineDownloadButton.mediaStoreAllocatedNameDiffers(
                "jack_123456789.jpg",
                "jack_123456789 (1).jpg"
        ));
        assertFalse(InlineDownloadButton.mediaStoreAllocatedNameDiffers(
                "jack_123456789.jpg",
                "jack_123456789.jpg"
        ));
    }

    @Test
    public void renameSkipsOccupiedTargets() {
        String target = "jack_123456789.jpg";

        assertEquals(
                target,
                InlineDownloadButton.resolveTargetFileName(
                        target,
                        InlineDownloadButton.ConflictBehavior.RENAME,
                        Set.of()::contains
                )
        );
        assertEquals(
                "jack_123456789_2.jpg",
                InlineDownloadButton.resolveTargetFileName(
                        target,
                        InlineDownloadButton.ConflictBehavior.RENAME,
                        Set.of(target, "jack_123456789_1.jpg")::contains
                )
        );
    }

    @Test
    public void uniqueTemporaryDownloadNamesDoNotCollide() {
        String first = InlineDownloadButton.uniqueTemporaryDownloadFileName("jack_123456789.jpg");
        String second = InlineDownloadButton.uniqueTemporaryDownloadFileName("jack_123456789.jpg");

        assertNotEquals(first, second);
        assertTrue(first.startsWith("jack_123456789_tmp_"));
        assertTrue(first.endsWith(".jpg"));
    }

    @Test
    public void structuredRepostUsesOriginalMediaWhenWrapperHasNone() {
        List<?> repostedMedia = Collections.singletonList(new DownloadableMedia());

        assertSame(
                repostedMedia,
                InlineDownloadButton.selectMedia(Collections.emptyList(), repostedMedia)
        );
    }

    @Test
    public void structuredRepostUsesOriginalMediaWhenWrapperMediaIsNotDownloadable() {
        List<?> repostedMedia = Collections.singletonList(new DownloadableMedia());

        assertSame(
                repostedMedia,
                InlineDownloadButton.selectMedia(
                        Collections.singletonList(new UnsupportedMedia()),
                        repostedMedia
                )
        );
    }

    @Test
    public void canonicalMediaWinsWhenItHasDownloadableImage() {
        List<?> canonicalMedia = Collections.singletonList(new ValidImageMedia());
        List<?> repostedMedia = Collections.singletonList(new DownloadableMedia());

        assertSame(canonicalMedia, InlineDownloadButton.selectMedia(canonicalMedia, repostedMedia));
    }

    @Test
    public void validImageMediaIsDownloadable() {
        assertTrue(InlineDownloadButton.isDownloadableMedia(new ValidImageMedia()));
    }

    @Test
    public void imageWithoutHttpUrlIsNotDownloadable() {
        assertFalse(InlineDownloadButton.isDownloadableMedia(new InvalidImageMedia()));
        assertEquals(
                Collections.emptyList(),
                InlineDownloadButton.selectMedia(
                        Collections.singletonList(new InvalidImageMedia()),
                        Collections.emptyList()
                )
        );
    }

    @Test
    public void validVideoMediaIsDownloadableWithoutParsingBitrate() {
        assertTrue(InlineDownloadButton.isDownloadableMedia(new ValidVideoMedia("not-a-number")));
    }

    @Test
    public void videoWithoutValidHttpMp4VariantIsNotDownloadable() {
        List<?> invalidMedia = Collections.singletonList(new InvalidVideoMedia());
        List<?> repostedMedia = Collections.singletonList(new DownloadableMedia());

        assertFalse(InlineDownloadButton.isDownloadableMedia(invalidMedia.get(0)));
        assertSame(repostedMedia, InlineDownloadButton.selectMedia(invalidMedia, repostedMedia));
    }

    @Test
    public void validMp4VariantIsAcceptedForGifAndOtherMedia() {
        assertTrue(InlineDownloadButton.isDownloadableMedia(new ValidGifMedia()));
        assertTrue(InlineDownloadButton.isDownloadableMedia(new ValidOtherMedia()));
    }

    @Test
    public void hasMediaDoesNotUseReflectiveGetMediaFallback() {
        assertFalse(InlineDownloadButton.hasMedia(null));
        assertFalse(InlineDownloadButton.hasMedia(new MediaPost(null)));
        assertFalse(InlineDownloadButton.hasMedia(new MediaPost(Collections.emptyList())));
        assertFalse(InlineDownloadButton.hasMedia(new MediaPost(Collections.singletonList(new Object()))));
    }

    @Test
    public void downloadActionIsNotAddedBeforeItsPatchInitializes() {
        List<?> actions = Collections.singletonList(new Object());

        assertSame(actions, InlineDownloadButton.addAction(actions, new Object()));
    }

    @Test
    public void mediaInspectionDecisionRequiresHidingAndNoExistingDownloadAction() {
        assertFalse(InlineDownloadButton.shouldInspectMedia(false, false));
        assertFalse(InlineDownloadButton.shouldInspectMedia(false, true));
        assertTrue(InlineDownloadButton.shouldInspectMedia(true, false));
        assertFalse(InlineDownloadButton.shouldInspectMedia(true, true));
    }

    @Test
    public void videoRelativePathUsesMoviesDirectory() {
        assertEquals(
                "Movies/Twitter/",
                InlineDownloadButton.relativeDownloadPath("video/mp4")
        );
    }

    @Test
    public void gifRelativePathUsesMoviesDirectory() {
        assertEquals(
                "Movies/Twitter/",
                InlineDownloadButton.relativeDownloadPath("video/mp4")
        );
    }

    @Test
    public void imageRelativePathUsesPicturesDirectory() {
        assertEquals(
                "Pictures/Twitter/",
                InlineDownloadButton.relativeDownloadPath("image/jpeg")
        );
    }

    @Test
    public void unknownMimeFallsBackToPicturesDirectory() {
        assertEquals(
                "Pictures/Twitter/",
                InlineDownloadButton.relativeDownloadPath("application/octet-stream")
        );
        assertEquals(
                "Pictures/Twitter/",
                InlineDownloadButton.relativeDownloadPath(null)
        );
    }

    @Test
    public void conflictCleanupExcludesPublishedDestination() {
        assertTrue(InlineDownloadButton.existingMediaSelection().contains("!=?"));
        assertArrayEquals(
                new String[]{"jack_123456789.jpg", "Pictures/Twitter/", "42"},
                InlineDownloadButton.existingMediaSelectionArgs(
                        "jack_123456789.jpg",
                        "Pictures/Twitter/",
                        "42"
                )
        );
    }

    @Test
    public void signTaggedIconSizeSurvivesDeferredRendering() {
        Object downloadAction = new Object();
        InlineDownloadButton.registerDownloadAction(downloadAction);
        Object nativeIcon = new Object();
        Object downloadIcon = new Object();
        float capturedSize = InlineDownloadButton.markIconSize(downloadAction, 18f);

        assertEquals(-18f, capturedSize, 0.0f);
        assertEquals(18f, InlineDownloadButton.displayIconSize(capturedSize), 0.0f);
        assertSame(downloadIcon, InlineDownloadButton.selectIcon(
                nativeIcon,
                capturedSize,
                downloadIcon
        ));
    }

    @Test
    public void actionMembershipUsesIdentityRatherThanEquals() {
        Object equalityToken = new Object();
        EqualObject downloadAction = new EqualObject(equalityToken);
        EqualObject equalButDistinctNativeAction = new EqualObject(equalityToken);
        InlineDownloadButton.registerDownloadAction(downloadAction);

        assertEquals(
                18f,
                InlineDownloadButton.markIconSize(equalButDistinctNativeAction, 18f),
                0.0f
        );
        assertEquals(-18f, InlineDownloadButton.markIconSize(downloadAction, 18f), 0.0f);
    }

    @Test
    public void downloadActionRegistrationDoesNotEvictVisibleEntry() {
        Object oldestAction = new Object();
        InlineDownloadButton.registerDownloadAction(oldestAction);
        List<Object> retainedActions = new ArrayList<>();
        for (int index = 0; index < 2000; index++) {
            Object action = new Object();
            retainedActions.add(action);
            InlineDownloadButton.registerDownloadAction(action);
        }

        assertEquals(-18f, InlineDownloadButton.markIconSize(oldestAction, 18f), 0.0f);
        assertEquals(-18f, InlineDownloadButton.markIconSize(
                retainedActions.get(retainedActions.size() - 1),
                18f
        ), 0.0f);
    }

    @Test
    public void nativeAndDownloadSlotsHaveDistinctCapturedValues() {
        Object downloadAction = new Object();
        InlineDownloadButton.registerDownloadAction(downloadAction);
        Object nativeIcon = new Object();
        Object downloadIcon = new Object();

        float nativeSize = InlineDownloadButton.markIconSize(new Object(), 18f);
        float downloadSize = InlineDownloadButton.markIconSize(downloadAction, 18f);

        assertEquals(18f, nativeSize, 0.0f);
        assertEquals(-18f, downloadSize, 0.0f);
        assertEquals(
                InlineDownloadButton.displayIconSize(nativeSize),
                InlineDownloadButton.displayIconSize(downloadSize),
                0.0f
        );
        assertSame(nativeIcon, InlineDownloadButton.selectIcon(nativeIcon, nativeSize, downloadIcon));
        assertSame(downloadIcon, InlineDownloadButton.selectIcon(
                nativeIcon,
                downloadSize,
                downloadIcon
        ));

        assertEquals(18f, InlineDownloadButton.displayIconSize(nativeSize), 0.0f);
        assertSame(nativeIcon, InlineDownloadButton.selectIcon(nativeIcon, downloadIcon));

        assertEquals(18f, InlineDownloadButton.displayIconSize(downloadSize), 0.0f);
        assertSame(downloadIcon, InlineDownloadButton.selectIcon(nativeIcon, downloadIcon));
    }

    @Test
    public void disabledRenderContractUsesNativeIcon() {
        INLINE_DOWNLOAD_SETTING.save(false);
        Object downloadAction = new Object();
        InlineDownloadButton.registerDownloadAction(downloadAction);
        Object nativeIcon = new Object();

        assertEquals(18f, InlineDownloadButton.markIconSize(downloadAction, 18f), 0.0f);
        assertSame(nativeIcon, InlineDownloadButton.selectIcon(
                nativeIcon,
                -18f,
                new Object()
        ));
    }

    @Test
    public void repostWithAttachedMediaExtractsScreenNameFromExpandedUrl() {
        String post = "ContextualPost(canonicalPost=CanonicalPost(id=2091833522717663582, " +
                "text=菊地姫奈さんのお尻って国宝だよな！\nhttps://t.co/KfA7O5wSze, " +
                "timestamp=2026-08-24T10:22:30Z, " +
                "media=[MediaContentImage(mediaId=2088553798574944256, " +
                "imageUrl=https://pbs.twimg.com/media/HPwInTpaMAA4y6q.jpg, " +
                "sourceInfo=SourceInfo(sourcePostIdentifier=2088553803364843766, " +
                "sourceUserIdentifier=2044418450530181120, " +
                "sourceUserDisplayName=写真集探偵, " +
                "sourceUserAvatarUrl=https://pbs.twimg.com/profile_images/2044419200387780608/ErcK3mbv_normal.jpg, " +
                "sourceUserVerifiedType=NotVerified), isDownloadable=true)], " +
                "entityList=PostEntityList(mentions=[], urls=[], " +
                "media=[MediaEntity(id=2088553798574944256, displayUrl=pic.x.com/KfA7O5wSze, " +
                "expandedUrl=https://x.com/Phot0_detective/status/2088553803364843766/photo/1, " +
                "url=https://t.co/KfA7O5wSze, startIdx=18, endIdx=41, grokPostId=null)]), " +
                "author=MinimalUser(id=1252509176015790080, screenName=Chetanc54455628, name=一日一グラビア), " +
                "legacyCard=null, rePostedPost=null)";

        assertEquals("Phot0_detective", InlineDownloadButton.sourceUsername(post));
        assertEquals("2088553803364843766", InlineDownloadButton.sourcePostId(post));
    }

    @Test
    public void repostWithAttachedMediaFallsBackToAuthorWhenNoExpandedUrlOrMentions() {
        String post = "ContextualPost(canonicalPost=CanonicalPost(id=2091833522717663582, " +
                "media=[MediaContentImage(mediaId=2088553798574944256, " +
                "sourceInfo=SourceInfo(sourcePostIdentifier=2088553803364843766))], " +
                "entityList=PostEntityList(mentions=[], urls=[], media=[]), " +
                "author=MinimalUser(id=1252509176015790080, screenName=Chetanc54455628, name=一日一グラビア), " +
                "rePostedPost=null)";

        assertEquals("Chetanc54455628", InlineDownloadButton.sourceUsername(post));
        assertEquals("2088553803364843766", InlineDownloadButton.sourcePostId(post));
    }

    @Test
    public void foldedRetweetWithMentionsExtractsMentionScreenName() {
        String post = "ContextualPost(canonicalPost=CanonicalPost(id=2088336364039184458, " +
                "media=[MediaContentImage(mediaId=1, " +
                "sourceInfo=SourceInfo(sourcePostIdentifier=2088279482146898407))], " +
                "entityList=PostEntityList(mentions=[MentionEntity(screenName=chachironi3)]), " +
                "author=MinimalUser(id=1, screenName=pokorakun, name=pokorakun), " +
                "rePostedPost=null)";

        assertEquals("chachironi3", InlineDownloadButton.sourceUsername(post));
        assertEquals("2088279482146898407", InlineDownloadButton.sourcePostId(post));
    }

    @Test
    public void structuredRepostExtractsOriginalAuthorAndId() {
        String post = "ContextualPost(canonicalPost=CanonicalPost(id=2088334976651792559, " +
                "author=MinimalUser(id=9, screenName=retweeter, name=Retweeter), media=[]), " +
                "rePostedPost=RePostedPost(canonicalPost=CanonicalPost(id=2088221458740969716, " +
                "author=MinimalUser(id=1423483994084048906, screenName=hige_hurai, name=Hige Hurai), " +
                "media=[MediaContentImage(mediaId=1)])))";

        assertEquals("hige_hurai", InlineDownloadButton.sourceUsername(post));
        assertEquals("2088221458740969716", InlineDownloadButton.sourcePostId(post));
    }

    @Test
    public void completelyUnresolvablePostGracefullyFallsBackToDefaults() {
        String post = "CorruptedPost()";

        assertEquals("twitter", InlineDownloadButton.sourceUsername(post));
        assertEquals("post", InlineDownloadButton.sourcePostId(post));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> settings() throws ReflectiveOperationException {
        Field field = SettingsRegistry.class.getDeclaredField("SETTINGS");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(null);
    }

    private static final class DownloadableMedia {
        @Override
        public String toString() {
            return "MediaContentVideo(variants=[MediaVariant(" +
                    "url=https://video.twimg.com/media.mp4, bitRate=100, " +
                    "contentType=video/mp4)])";
        }
    }

    private static final class ValidImageMedia {
        @Override
        public String toString() {
            return "MediaContentImage(imageUrl=https://pbs.twimg.com/media/example.jpg)";
        }
    }

    private static final class InvalidImageMedia {
        @Override
        public String toString() {
            return "MediaContentImage(imageUrl=file:///media/example.jpg)";
        }
    }

    private static final class ValidVideoMedia {
        private final String bitRate;

        ValidVideoMedia(String bitRate) {
            this.bitRate = bitRate;
        }

        @Override
        public String toString() {
            return "MediaContentVideo(variants=[MediaVariant(" +
                    "url=https://video.twimg.com/media, bitRate=" + bitRate + ", " +
                    "contentType=video/mp4)])";
        }
    }

    private static final class InvalidVideoMedia {
        @Override
        public String toString() {
            return "MediaContentVideo(variants=[MediaVariant(" +
                    "url=https://video.twimg.com/media.webm, bitRate=100, " +
                    "contentType=video/webm)])";
        }
    }

    private static final class ValidGifMedia {
        @Override
        public String toString() {
            return "MediaContentGif(variants=[MediaVariant(" +
                    "url=https://video.twimg.com/media, bitRate=null, " +
                    "contentType=video/mp4)])";
        }
    }

    private static final class ValidOtherMedia {
        @Override
        public String toString() {
            return "MediaContentOther(variants=[MediaVariant(" +
                    "url=https://video.twimg.com/media.mp4, bitRate=100, " +
                    "contentType=application/octet-stream)])";
        }
    }

    private static final class UnsupportedMedia {
        @Override
        public String toString() {
            return "MediaContentImage(imageUrl=null)";
        }
    }

    private static final class EqualObject {
        private final Object equalityToken;

        EqualObject(Object equalityToken) {
            this.equalityToken = equalityToken;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualObject
                    && equalityToken == ((EqualObject) other).equalityToken;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(equalityToken);
        }
    }

    /** Probes that hasMedia does not consult a reflective getMedia() accessor. */
    public static final class MediaPost {
        private final List<?> media;

        MediaPost(List<?> media) {
            this.media = media;
        }

        public List<?> getMedia() {
            return media;
        }
    }
}
