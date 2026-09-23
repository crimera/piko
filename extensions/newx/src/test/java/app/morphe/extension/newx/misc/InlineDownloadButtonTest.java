package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.shared.settings.BooleanSetting;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
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
    public void thumbnailCacheUrlKeepsOriginalMediaUrl() {
        String sourceUrl = "https://pbs.twimg.com/media/example.jpg?format=jpg&name=orig";
        String media = "MediaContentImage(imageUrl=" + sourceUrl + ")";

        assertEquals(sourceUrl, InlineDownloadButton.thumbnailCacheUrlForMedia(media));
    }

    @Test
    public void thumbnailUrlRewritesTwimgSizesAndLeavesOtherHostsAlone() {
        assertEquals(
                "https://pbs.twimg.com/media/x.jpg?format=jpg&name=small",
                InlineDownloadButton.thumbnailUrlForMedia(
                        "MediaContentImage(imageUrl=https://pbs.twimg.com/media/x.jpg?format=png&name=orig)"
                )
        );
        assertEquals(
                "https://example.com/x.jpg",
                InlineDownloadButton.thumbnailUrlForMedia(
                        "MediaContentImage(imageUrl=https://example.com/x.jpg)"
                )
        );
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
    public void imageResolutionUsesOriginalDimensionsAndNamedTwimgSizes() {
        String media = "MediaContentImage(mediaId=1, " +
                "imageUrl=https://pbs.twimg.com/media/abc.jpg, " +
                "originalImgHeight=1080, originalImgWidth=1920, sourceInfo=null)";

        List<InlineDownloadButton.DownloadItem> items =
                InlineDownloadButton.downloadItems(Collections.singletonList(media));

        assertEquals(1, items.size());
        InlineDownloadButton.DownloadItem item = items.get(0);
        assertEquals("1920x1080", item.resolution);
        // The 4096x4096 and large caps are above the original, so twimg returns it unchanged and
        // those sizes are dropped as duplicates.
        assertEquals(3, item.resolutionOptions.size());
        assertEquals(
                "https://pbs.twimg.com/media/abc.jpg?format=jpg&name=orig",
                item.resolutionOptions.get(0).url
        );
        assertEquals("Original", item.resolutionOptions.get(0).label);
        assertEquals("1920x1080", item.resolutionOptions.get(0).detail);
        assertEquals("1200x675", item.resolutionOptions.get(1).detail);
        assertEquals("https://pbs.twimg.com/media/abc.jpg?format=jpg&name=medium",
                item.resolutionOptions.get(1).url);
        assertEquals("680x383", item.resolutionOptions.get(2).detail);
        assertEquals("https://pbs.twimg.com/media/abc.jpg?format=jpg&name=small",
                item.resolutionOptions.get(2).url);
    }

    @Test
    public void imageSizesMatchingTheOriginalAreNotOffered() {
        String media = "MediaContentImage(imageUrl=https://pbs.twimg.com/media/x.jpg, " +
                "originalImgHeight=1188, originalImgWidth=832)";

        List<InlineDownloadButton.DownloadItem> items =
                InlineDownloadButton.downloadItems(Collections.singletonList(media));

        InlineDownloadButton.DownloadItem item = items.get(0);
        // Only Small actually shrinks a 832x1188 photo; the rest resolve to the original.
        assertEquals(2, item.resolutionOptions.size());
        assertEquals("Original", item.resolutionOptions.get(0).label);
        assertEquals("832x1188", item.resolutionOptions.get(0).resolution);
        assertEquals("Small", item.resolutionOptions.get(1).label);
        assertEquals("476x680", item.resolutionOptions.get(1).resolution);
    }

    @Test
    public void nonTwimgImagesOfferOnlyTheOriginalSize() {
        String media = "MediaContentImage(imageUrl=https://example.com/a.jpg, " +
                "originalImgHeight=100, originalImgWidth=200)";

        List<InlineDownloadButton.DownloadItem> items =
                InlineDownloadButton.downloadItems(Collections.singletonList(media));

        assertEquals(1, items.get(0).resolutionOptions.size());
        assertEquals("https://example.com/a.jpg", items.get(0).url);
        assertEquals("200x100", items.get(0).resolution);
    }

    @Test
    public void videoResolutionIsParsedFromVariantUrlAndSortedByBitrate() {
        String media = "MediaContentVideo(variants=[" +
                "MediaVariant(url=https://video.twimg.com/vid/640x360/low.mp4, " +
                "bitRate=458000, contentType=video/mp4), " +
                "MediaVariant(url=https://video.twimg.com/vid/1280x720/high.mp4, " +
                "bitRate=2176000, contentType=video/mp4)])";

        List<InlineDownloadButton.DownloadItem> items =
                InlineDownloadButton.downloadItems(Collections.singletonList(media));

        assertEquals(1, items.size());
        InlineDownloadButton.DownloadItem item = items.get(0);
        assertEquals("1280x720", item.resolution);
        assertEquals("https://video.twimg.com/vid/1280x720/high.mp4", item.url);
        assertEquals(2, item.resolutionOptions.size());
        assertEquals("1280x720", item.resolutionOptions.get(0).label);
        assertEquals("2.2 Mbps", item.resolutionOptions.get(0).detail);
        assertEquals("640x360", item.resolutionOptions.get(1).label);
        assertEquals("458 kbps", item.resolutionOptions.get(1).detail);
    }

    @Test
    public void bitrateIsFormattedFromBitsPerSecond() {
        assertEquals("10.4 Mbps", InlineDownloadButton.formatBitRate(10368000));
        assertEquals("2.2 Mbps", InlineDownloadButton.formatBitRate(2176000));
        assertEquals("950 kbps", InlineDownloadButton.formatBitRate(950000));
        assertEquals("256 kbps", InlineDownloadButton.formatBitRate(256000));
        assertEquals("512 bps", InlineDownloadButton.formatBitRate(512));
        assertNull(InlineDownloadButton.formatBitRate(0));
    }

    @Test
    public void resolutionOptionsSuffixTheFileNameBeforeTheExtension() {
        assertEquals(
                "jack_1_1920x1080.jpg",
                InlineDownloadButton.withResolutionSuffix("jack_1.jpg", "1920x1080")
        );
        assertEquals(
                "jack_1_1280x720",
                InlineDownloadButton.withResolutionSuffix("jack_1", "1280x720")
        );
        assertEquals(
                "jack_1.jpg",
                InlineDownloadButton.withResolutionSuffix("jack_1.jpg", null)
        );
    }

    @Test
    public void imageQualityPreferenceSelectsTheMatchingTier() {
        InlineDownloadButton.DownloadItem original = imageOption("original", "1920x1080");
        InlineDownloadButton.DownloadItem large = imageOption("large", "1920x1080");
        InlineDownloadButton.DownloadItem medium = imageOption("medium", "1200x675");
        InlineDownloadButton.DownloadItem small = imageOption("small", "680x383");
        InlineDownloadButton.DownloadItem item = mediaItem(
                "image/jpeg",
                Arrays.asList(original, large, medium, small)
        );

        assertSame(original, InlineDownloadButton.selectImageOption(item, "original"));
        assertSame(medium, InlineDownloadButton.selectImageOption(item, "medium"));
        // An unrecognised tier must fall back to the best available instead of failing.
        assertSame(original, InlineDownloadButton.selectImageOption(item, "huge"));
    }

    @Test
    public void videoQualityPreferencePicksTheClosestVariant() {
        InlineDownloadButton.DownloadItem v1080 = videoOption("1920x1080", 4000);
        InlineDownloadButton.DownloadItem v720 = videoOption("1280x720", 2000);
        InlineDownloadButton.DownloadItem v360 = videoOption("640x360", 500);
        InlineDownloadButton.DownloadItem item = mediaItem(
                "video/mp4",
                Arrays.asList(v1080, v720, v360)
        );

        assertSame(v1080, InlineDownloadButton.selectVideoOption(item, "highest"));
        assertSame(v360, InlineDownloadButton.selectVideoOption(item, "lowest"));
        assertSame(v720, InlineDownloadButton.selectVideoOption(item, "720p"));
        assertSame(v1080, InlineDownloadButton.selectVideoOption(item, "1080p"));
        // Nothing at or below 480p, so the smallest variant above it wins.
        assertSame(v360, InlineDownloadButton.selectVideoOption(item, "480p"));

        InlineDownloadButton.DownloadItem onlyHigh = mediaItem(
                "video/mp4",
                Arrays.asList(v1080, v720)
        );
        assertSame(v720, InlineDownloadButton.selectVideoOption(onlyHigh, "480p"));
    }

    @Test
    public void videoQualityUsesTheShortSideForPortraitVariants() {
        InlineDownloadButton.DownloadItem v1080 = videoOption("1080x1920", 10368000);
        InlineDownloadButton.DownloadItem v720 = videoOption("720x1280", 2176000);
        InlineDownloadButton.DownloadItem v480 = videoOption("480x852", 950000);
        InlineDownloadButton.DownloadItem v320 = videoOption("320x568", 632000);
        InlineDownloadButton.DownloadItem item = mediaItem(
                "video/mp4",
                Arrays.asList(v1080, v720, v480, v320)
        );

        // Tiers name the short side, so a tall video must not be ranked by its 1920px height.
        assertSame(v1080, InlineDownloadButton.selectVideoOption(item, "1080p"));
        assertSame(v720, InlineDownloadButton.selectVideoOption(item, "720p"));
        assertSame(v480, InlineDownloadButton.selectVideoOption(item, "480p"));
        assertSame(v320, InlineDownloadButton.selectVideoOption(item, "360p"));
    }

    @Test
    public void videoQualityFallsBackWhenVariantUrlsCarryNoResolution() {
        InlineDownloadButton.DownloadItem unknown = videoOption(null, 1000);
        InlineDownloadButton.DownloadItem item = mediaItem(
                "video/mp4",
                Collections.singletonList(unknown)
        );

        assertSame(unknown, InlineDownloadButton.selectVideoOption(item, "720p"));
    }

    private static InlineDownloadButton.DownloadItem imageOption(String qualityKey, String resolution) {
        return new InlineDownloadButton.DownloadItem(
                "https://pbs.twimg.com/media/x.jpg?format=jpg&name=" + qualityKey,
                "jpg",
                "image/jpeg",
                qualityKey,
                null,
                null,
                resolution,
                resolution,
                qualityKey,
                Collections.emptyList()
        );
    }

    private static InlineDownloadButton.DownloadItem videoOption(String resolution, int bitRate) {
        return new InlineDownloadButton.DownloadItem(
                "https://video.twimg.com/vid/" + (resolution == null ? "x" : resolution) + "/v.mp4",
                "mp4",
                "video/mp4",
                resolution == null ? "Video" : resolution,
                null,
                null,
                resolution,
                bitRate + " kbps",
                null,
                Collections.emptyList()
        );
    }

    private static InlineDownloadButton.DownloadItem mediaItem(
            String mimeType,
            List<InlineDownloadButton.DownloadItem> options
    ) {
        return new InlineDownloadButton.DownloadItem(
                "https://example.com/media",
                mimeType.startsWith("image/") ? "jpg" : "mp4",
                mimeType,
                mimeType.startsWith("image/") ? "Image" : "Video",
                null,
                null,
                options.isEmpty() ? null : options.get(0).resolution,
                null,
                null,
                options
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
    public void mediaInspectionDecisionRequiresHidingAndNoExistingDownloadAction() {
        assertFalse(InlineDownloadButton.shouldInspectMedia(false, false));
        assertFalse(InlineDownloadButton.shouldInspectMedia(false, true));
        assertTrue(InlineDownloadButton.shouldInspectMedia(true, false));
        assertFalse(InlineDownloadButton.shouldInspectMedia(true, true));
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
    public void repostWithAttachedMediaResolvesOriginalPosterAndSourceId() {
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

        // A download of a repost must be filed under the original poster, not the retweeter.
        DownloadFileName.PostContext context = DownloadFileName.PostContext.from(post);
        assertEquals("Phot0_detective", context.screenName);
        assertEquals("2088553803364843766", context.id);
        assertEquals("写真集探偵", context.sourceUserDisplayName);
        assertEquals("Phot0_detective_2088553803364843766.jpg",
                DownloadFileName.render(null, context, 0, 1, "jpg"));
    }

    @Test
    public void repostWithAttachedMediaFallsBackToAuthorWhenNoExpandedUrlOrMentions() {
        String post = "ContextualPost(canonicalPost=CanonicalPost(id=2091833522717663582, " +
                "media=[MediaContentImage(mediaId=2088553798574944256, " +
                "sourceInfo=SourceInfo(sourcePostIdentifier=2088553803364843766))], " +
                "entityList=PostEntityList(mentions=[], urls=[], media=[]), " +
                "author=MinimalUser(id=1252509176015790080, screenName=Chetanc54455628, name=一日一グラビア), " +
                "rePostedPost=null)";

        DownloadFileName.PostContext context = DownloadFileName.PostContext.from(post);
        assertEquals("Chetanc54455628", context.screenName);
        assertEquals("2088553803364843766", context.id);
    }

    @Test
    public void foldedRetweetWithMentionsResolvesMentionScreenName() {
        String post = "ContextualPost(canonicalPost=CanonicalPost(id=2088336364039184458, " +
                "media=[MediaContentImage(mediaId=1, " +
                "sourceInfo=SourceInfo(sourcePostIdentifier=2088279482146898407))], " +
                "entityList=PostEntityList(mentions=[MentionEntity(screenName=chachironi3)]), " +
                "author=MinimalUser(id=1, screenName=pokorakun, name=pokorakun), " +
                "rePostedPost=null)";

        DownloadFileName.PostContext context = DownloadFileName.PostContext.from(post);
        assertEquals("chachironi3", context.screenName);
        assertEquals("2088279482146898407", context.id);
    }

    @Test
    public void structuredRepostResolvesOriginalAuthorAndId() {
        String post = "ContextualPost(canonicalPost=CanonicalPost(id=2088334976651792559, " +
                "author=MinimalUser(id=9, screenName=retweeter, name=Retweeter), media=[]), " +
                "rePostedPost=RePostedPost(canonicalPost=CanonicalPost(id=2088221458740969716, " +
                "author=MinimalUser(id=1423483994084048906, screenName=hige_hurai, name=Hige Hurai), " +
                "media=[MediaContentImage(mediaId=1)])))";

        DownloadFileName.PostContext context = DownloadFileName.PostContext.from(post);
        assertEquals("hige_hurai", context.screenName);
        assertEquals("2088221458740969716", context.id);
        assertEquals("Hige Hurai", context.name);
    }

    @Test
    public void completelyUnresolvablePostFallsBackToDefaultName() {
        DownloadFileName.PostContext context = DownloadFileName.PostContext.from("CorruptedPost()");

        assertNull(context.id);
        assertNull(context.screenName);
        // Tokens with no value must not leave braces behind, and a fully unresolved name must not
        // sanitize to an empty filename the provider would reject.
        assertEquals("twitter_post.jpg",
                DownloadFileName.render("{screenName}_{id}", context, 0, 1, "jpg"));
    }

    @Test
    public void templateRendersEveryEditorToken() {
        DownloadFileName.PostContext post = DownloadFileName.PostContext.sample();

        assertEquals("1234567890123456789-jack-Jack-2026-01-31-123456-1-jpg",
                DownloadFileName.render(
                        "{id}-{userName}-{name}-{timestamp}-{mediaIndex}-{ext}", post, 0, 1, "jpg"));
        // {displayName} and the pre-rename {screenName} are documented aliases; dropping them
        // would silently break every template users already wrote against the alias.
        assertEquals(DownloadFileName.render("{name}", post, 0, 1, "jpg"),
                DownloadFileName.render("{displayName}", post, 0, 1, "jpg"));
        assertEquals(DownloadFileName.render("{userName}", post, 0, 1, "jpg"),
                DownloadFileName.render("{screenName}", post, 0, 1, "jpg"));
    }

    @Test
    public void resolutionTokenRendersTheChosenMediaSize() {
        DownloadFileName.PostContext post = DownloadFileName.PostContext.sample();

        assertTrue(DownloadFileName.editorTokens().contains("resolution"));
        assertEquals("jack_1234567890123456789_1920x1080.jpg",
                DownloadFileName.render(
                        "{userName}_{id}_{resolution}", post, 0, 1, "jpg", "1920x1080"));
        // A known token with no value must not leave braces or an empty segment behind.
        assertEquals("jack_1234567890123456789.jpg",
                DownloadFileName.render("{userName}_{id}_{resolution}", post, 0, 1, "jpg", null));
        assertEquals(DownloadFileName.Outcome.OK,
                DownloadFileName.validate("{userName}_{id}_{resolution}").outcome);
        // Resolution is per media, not per post, so it cannot anchor a template on its own.
        assertEquals(DownloadFileName.Outcome.STATIC,
                DownloadFileName.validate("{userName}_{resolution}").outcome);
    }

    @Test
    public void unknownTokenStaysLiteralAndIsRejectedByValidation() {
        DownloadFileName.PostContext post = DownloadFileName.PostContext.sample();

        // A typo must be visible in the filename and blocked in the editor, never silently
        // rendered as an empty segment that collides with every other download.
        assertEquals("idd_jack.jpg",
                DownloadFileName.render("{idd}_{screenName}", post, 0, 1, "jpg"));
        DownloadFileName.Validation validation = DownloadFileName.validate("{screenName}_{idd}");
        assertEquals(DownloadFileName.Outcome.UNKNOWN_TOKEN, validation.outcome);
        assertEquals("idd", validation.token);
    }

    @Test
    public void validationRejectsEmptyUnclosedAndPostIndependentTemplates() {
        assertEquals(DownloadFileName.Outcome.OK,
                DownloadFileName.validate("{userName}_{id}").outcome);
        assertEquals(DownloadFileName.Outcome.OK,
                DownloadFileName.validate("{screenName}_{id}").outcome);
        assertEquals(DownloadFileName.Outcome.EMPTY, DownloadFileName.validate("   ").outcome);
        assertEquals(DownloadFileName.Outcome.UNCLOSED, DownloadFileName.validate("{id").outcome);
        // Without a post-dependent token every download would resolve to one name.
        assertEquals(DownloadFileName.Outcome.STATIC,
                DownloadFileName.validate("{userName}_{mediaIndex}_{ext}").outcome);
        assertEquals(DownloadFileName.Outcome.OK,
                DownloadFileName.validate("{userName}_{timestamp}").outcome);
    }

    @Test
    public void mediaIndexSuffixIsAddedOnlyWhenTemplateOmitsIt() {
        DownloadFileName.PostContext post = DownloadFileName.PostContext.sample();

        assertEquals("jack_1.jpg", DownloadFileName.render("{userName}", post, 0, 2, "jpg"));
        assertEquals("jack_2.jpg", DownloadFileName.render("{userName}", post, 1, 2, "jpg"));
        // A single item never gets a suffix, even though the template has no index token.
        assertEquals("jack.jpg", DownloadFileName.render("{userName}", post, 0, 1, "jpg"));
        // An explicit index token must not be suffixed a second time.
        assertEquals("jack_2.jpg", DownloadFileName.render("{userName}_{mediaIndex}", post, 1, 2, "jpg"));
    }

    @Test
    public void explicitExtensionTokenReplacesTheAutomaticSuffix() {
        DownloadFileName.PostContext post = DownloadFileName.PostContext.sample();

        assertEquals("jack_1234567890123456789.jpg",
                DownloadFileName.render("{userName}_{id}.{ext}", post, 0, 1, "jpg"));
        assertEquals("jack_1234567890123456789.jpg",
                DownloadFileName.render("{userName}_{id}", post, 0, 1, "jpg"));
    }

    @Test
    public void sanitizerStripsSegmentsThatCouldEscapeTheChosenFolder() {
        DownloadFileName.PostContext post = new DownloadFileName.PostContext(
                "../../etc/passwd",
                "..\\..\\evil",
                "a/b",
                "2026-01-31-123456",
                null,
                null,
                null);

        String rendered = DownloadFileName.render("{screenName}_{id}_{name}", post, 0, 1, "jpg");
        assertEquals("evil_etc_passwd_a_b.jpg", rendered);
        assertFalse(rendered.contains("/"));
        assertFalse(rendered.contains("\\"));
        assertFalse(rendered.contains(".."));
        assertFalse(rendered.startsWith("."));
    }

    @Test
    public void mediaKindRoutingRejectsNonMediaMimeTypes() {
        assertEquals(DownloadDestination.MediaKind.IMAGES,
                DownloadDestination.mediaKindFor("image/jpeg"));
        assertEquals(DownloadDestination.MediaKind.VIDEOS,
                DownloadDestination.mediaKindFor("video/mp4"));

        // An unroutable mime must fail closed rather than land in the wrong folder.
        try {
            DownloadDestination.mediaKindFor("application/pdf");
            fail("Expected an unroutable MIME type to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("application/pdf"));
        }
        try {
            DownloadDestination.mediaKindFor(null);
            fail("Expected a null MIME type to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
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
