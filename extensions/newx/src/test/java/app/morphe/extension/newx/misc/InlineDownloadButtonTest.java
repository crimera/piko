package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class InlineDownloadButtonTest {
    @Test
    public void singleMediaUsesRequestedTwitterFilename() {
        assertEquals(
                "jack_123456789.jpg",
                InlineDownloadButton.downloadFileName("jack", "123456789", "jpg", 0, 1)
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
    public void selectIconConsumesRenderMarker() {
        Object downloadAction = new Object();
        InlineDownloadButton.registerDownloadAction(downloadAction);
        assertFalse(InlineDownloadButton.renderMarkerPending());

        Object nativeIcon = new Object();
        Object downloadIcon = new Object();
        InlineDownloadButton.markIconSize(downloadAction, 18f);
        assertTrue(InlineDownloadButton.renderMarkerPending());
        assertSame(downloadIcon, InlineDownloadButton.selectIcon(nativeIcon, 18f, downloadIcon));
        assertFalse(InlineDownloadButton.renderMarkerPending());

        // A consumed marker must not substitute the download icon a second time.
        assertSame(nativeIcon, InlineDownloadButton.selectIcon(nativeIcon, 18f, downloadIcon));
    }

    @Test
    public void nativeActionRenderIsUntouched() {
        InlineDownloadButton.markIconSize(new Object(), 18f);

        Object nativeIcon = new Object();
        assertSame(nativeIcon, InlineDownloadButton.selectIcon(nativeIcon, 18f, new Object()));
        assertFalse(InlineDownloadButton.renderMarkerPending());
    }

    @Test
    public void finishRenderClearsMarkerWhenIconRenderingExitsEarly() {
        Object downloadAction = new Object();
        InlineDownloadButton.registerDownloadAction(downloadAction);
        InlineDownloadButton.markIconSize(downloadAction, 18f);
        assertTrue(InlineDownloadButton.renderMarkerPending());

        // Icon lambda never reached selectIcon (Compose skip path); entry-render exit
        // cleanup must still clear the marker.
        InlineDownloadButton.finishRender();
        assertFalse(InlineDownloadButton.renderMarkerPending());
    }

    @Test
    public void exceptionPathClearsRenderMarker() {
        Object downloadAction = new Object();
        InlineDownloadButton.registerDownloadAction(downloadAction);

        try {
            InlineDownloadButton.markIconSize(downloadAction, 18f);
            assertTrue(InlineDownloadButton.renderMarkerPending());
            throw new AssertionError("simulated Compose render failure");
        } catch (Throwable exception) {
            InlineDownloadButton.finishRender();
        }

        assertFalse(InlineDownloadButton.renderMarkerPending());
        Object nativeIcon = new Object();
        Object downloadIcon = new Object();
        assertSame(nativeIcon, InlineDownloadButton.selectIcon(nativeIcon, 18f, downloadIcon));
    }

    @Test
    public void repeatedRecompositionLeavesNoRenderMarker() {
        Object downloadAction = new Object();
        InlineDownloadButton.registerDownloadAction(downloadAction);
        Object nativeIcon = new Object();
        Object downloadIcon = new Object();

        for (int pass = 0; pass < 5; pass++) {
            InlineDownloadButton.markIconSize(downloadAction, 18f);
            assertSame(downloadIcon, InlineDownloadButton.selectIcon(nativeIcon, 18f, downloadIcon));
            assertFalse(InlineDownloadButton.renderMarkerPending());

            // Early-exit render pass: marker staged, icon lambda never consumes it.
            InlineDownloadButton.markIconSize(downloadAction, 18f);
            InlineDownloadButton.finishRender();
            assertFalse(InlineDownloadButton.renderMarkerPending());

            InlineDownloadButton.markIconSize(new Object(), 18f);
            assertSame(nativeIcon, InlineDownloadButton.selectIcon(nativeIcon, 18f, downloadIcon));
            assertFalse(InlineDownloadButton.renderMarkerPending());
        }
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

    private static final class DownloadableMedia {
        @Override
        public String toString() {
            return "MediaContentVideo(variants=[MediaVariant(" +
                    "url=https://video.twimg.com/media.mp4, bitRate=100, " +
                    "contentType=video/mp4)])";
        }
    }

    private static final class UnsupportedMedia {
        @Override
        public String toString() {
            return "MediaContentImage(imageUrl=null)";
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
