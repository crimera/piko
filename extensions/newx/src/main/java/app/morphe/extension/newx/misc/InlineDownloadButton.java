package app.morphe.extension.newx.misc;

import android.annotation.SuppressLint;
import android.app.Activity;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.newx.utils.NewXUtils;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.newx.settings.NewXSettingsUi;
import app.morphe.extension.newx.ui.ButtonView;
import app.morphe.extension.newx.ui.DialogView;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.newx.utils.ToStringParser;

@SuppressWarnings("unused")
public final class InlineDownloadButton {
    private static final String SETTING_ID = "newx.content.inline_download_button";
    private static final String HIDE_NO_MEDIA_SETTING = "newx.content.inline_download_hide_no_media";
    private static final String IMAGE_QUALITY_SETTING = "newx.content.inline_download.image_resolution";
    private static final String VIDEO_QUALITY_SETTING = "newx.content.inline_download.video_quality";
    /** Sentinel preference that keeps the resolution chooser instead of auto-selecting. */
    private static final String QUALITY_ASK = "ask";
    private static final String QUALITY_ORIGINAL = "original";
    private static final String QUALITY_HIGHEST = "highest";
    /** Concurrent transfers. Small enough to stay gentle on the connection pool and providers. */
    private static final int TRANSFER_THREADS = 4;
    /**
     * NewX video variants carry only url/bitrate/contentType. Twitter encodes each mp4 variant's
     * resolution in the URL path (`.../vid/1280x720/....mp4`), so the chooser reads it from there.
     */
    private static final Pattern VIDEO_RESOLUTION_PATTERN = Pattern.compile("/(\\d{2,5}x\\d{2,5})/");
    /** Named twimg image sizes, in the order offered by the resolution chooser. */
    private static final String[] NAMED_IMAGE_SIZES = {"4096x4096", "large", "medium", "small"};
    private static final int[] NAMED_IMAGE_CAPS = {4096, 2048, 1200, 680};
    /** Shown when the stored folder was refused and cleared; retapping opens the picker. */
    static final String FOLDER_LOST_MESSAGE =
            "Download folder is no longer available \u2014 tap download again to choose a new one";
    private static final ExecutorService DOWNLOAD_EXECUTOR =
            Executors.newFixedThreadPool(TRANSFER_THREADS);
    // Click-time parsing and document creation run here so the tap handler returns
    // immediately; DOWNLOAD_EXECUTOR stays reserved for transfers.
    private static final ExecutorService CLICK_EXECUTOR = Executors.newSingleThreadExecutor();
    // Timeline/profile scrolling creates a new action object per composition. Keep weak identity
    // keys without a FIFO cap: a cap can evict an action that is still visible and make its icon
    // fall back to Twitter's share glyph. Cleared weak keys are drained during set operations.
    private static final IdentityWeakSet DOWNLOAD_ACTIONS = new IdentityWeakSet();
    private static volatile boolean patchApplied;
    private static boolean initialized;

    private InlineDownloadButton() {
    }

    public static synchronized void initialize(Context context) {
        if (context == null) return;

        Context applicationContext = context.getApplicationContext();
        if (!(applicationContext instanceof Application application)) return;

        patchApplied = true;
        if (initialized) return;

        NewXUtils.initialize(application);
        initialized = true;
    }

    public static List<?> addAction(List<?> actions, Object presenter) {
        if (!patchApplied || !isEnabled() || actions == null) return actions;

        try {
            boolean hasDownloadAction = containsDownloadAction(actions);
            if (hasDownloadAction) {
                NewXLogger.printInfo(() -> "skip duplicate actions=" + actions.size());
                return actions;
            }

            boolean inspectMedia = shouldInspectMedia(hideWhenNoMedia(), hasDownloadAction);
            boolean hasMedia = true;
            if (inspectMedia) {
                Object post = postFor(presenter);
                hasMedia = hasMedia(post);
            }
            if (inspectMedia && !hasMedia) {
                NewXLogger.printInfo(() -> "skip no-media actions=" + actions.size());
                return actions;
            }

            Object downloadAction = createDownloadAction();
            registerDownloadAction(downloadAction);
            final boolean mediaAvailable = hasMedia;
            NewXLogger.printInfo(() -> "add actions=" + actions.size() + " hasMedia=" + mediaAvailable
                    + " download=" + System.identityHashCode(downloadAction));

            List<Object> result = new ArrayList<>(actions.size() + 1);
            result.addAll(actions);
            result.add(downloadAction);
            return result;
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to add the NewX inline download action", exception);
            return actions;
        }
    }

    private static final ThreadLocal<Boolean> CURRENT_ICON_IS_DOWNLOAD =
            new ThreadLocal<>();

    /**
     * Encodes the download classification in the value captured by Twitter's icon lambda. The
     * patched share branch restores the positive size before layout, then uses the retained sign
     * to select the download icon. This makes native/download slot flips visible to Compose without
     * relying on thread-local render ordering or remembered lambda identity.
     */
    public static float markIconSize(Object action, float iconSize) {
        if (!isEnabled()) return iconSize;

        boolean downloadAction = isDownloadAction(action);
        NewXLogger.printInfo(() -> "mark action=" + System.identityHashCode(action)
                + " download=" + downloadAction);
        return downloadAction ? -Math.abs(iconSize) : iconSize;
    }

    /** Returns the actual layout size for the sign-tagged icon-lambda value. */
    public static float displayIconSize(float markedIconSize) {
        CURRENT_ICON_IS_DOWNLOAD.set(Float.floatToRawIntBits(markedIconSize) < 0);
        return Math.abs(markedIconSize);
    }

    /** Selects from the classification captured directly by the icon lambda. */
    public static Object selectIcon(
            Object nativeIcon,
            Object downloadIcon
    ) {
        if (!isEnabled()) return nativeIcon;

        Boolean isDownload = CURRENT_ICON_IS_DOWNLOAD.get();
        CURRENT_ICON_IS_DOWNLOAD.remove();
        boolean useDownloadIcon = Boolean.TRUE.equals(isDownload);
        NewXLogger.printInfo(() -> "select download=" + useDownloadIcon);
        return useDownloadIcon ? downloadIcon : nativeIcon;
    }

    /** Selects from the classification captured directly by the icon lambda. */
    public static Object selectIcon(
            Object nativeIcon,
            float markedIconSize,
            Object downloadIcon
    ) {
        if (!isEnabled()) return nativeIcon;

        CURRENT_ICON_IS_DOWNLOAD.remove();
        boolean useDownloadIcon = Float.floatToRawIntBits(markedIconSize) < 0;
        NewXLogger.printInfo(() -> "select size=" + markedIconSize
                + " download=" + useDownloadIcon);
        return useDownloadIcon ? downloadIcon : nativeIcon;
    }

    /**
     * A weak set with identity, rather than equals(), membership semantics. The thread-local probe
     * avoids allocating a temporary weak reference for each membership lookup without retaining
     * the looked-up object after the operation.
     */
    private static final class IdentityWeakSet {
        private static final Boolean PRESENT = Boolean.TRUE;

        private final ConcurrentHashMap<IdentityWeakReference, Boolean> entries =
                new ConcurrentHashMap<>();
        private final ReferenceQueue<Object> clearedReferences = new ReferenceQueue<>();
        private final ThreadLocal<LookupKey> lookupKeys = new ThreadLocal<LookupKey>() {
            @Override
            protected LookupKey initialValue() {
                return new LookupKey();
            }
        };
        void add(Object referent) {
            if (referent == null) return;

            drainClearedReferences();
            IdentityWeakReference entry = new IdentityWeakReference(referent, clearedReferences);
            entries.putIfAbsent(entry, PRESENT);
        }

        boolean contains(Object referent) {
            if (referent == null) return false;
            if (entries.isEmpty()) return false;

            drainClearedReferences();
            LookupKey lookupKey = lookupKeys.get();
            lookupKey.set(referent);
            try {
                return entries.containsKey(lookupKey);
            } finally {
                lookupKey.clear();
            }
        }

        private void drainClearedReferences() {
            IdentityWeakReference reference;
            while ((reference = (IdentityWeakReference) clearedReferences.poll()) != null) {
                entries.remove(reference);
            }
        }

        private static Object referentOf(Object key) {
            if (key instanceof IdentityWeakReference reference) return reference.get();
            if (key instanceof LookupKey lookupKey) return lookupKey.referent;
            return null;
        }

        private static final class IdentityWeakReference extends WeakReference<Object> {
            private final int identityHashCode;

            IdentityWeakReference(Object referent, ReferenceQueue<Object> queue) {
                super(referent, queue);
                identityHashCode = System.identityHashCode(referent);
            }

            @Override
            public int hashCode() {
                return identityHashCode;
            }

            @Override
            public boolean equals(Object other) {
                Object referent = get();
                return referent != null && referent == referentOf(other);
            }
        }

        private static final class LookupKey {
            private Object referent;
            private int identityHashCode;

            void set(Object referent) {
                this.referent = referent;
                identityHashCode = System.identityHashCode(referent);
            }

            void clear() {
                referent = null;
                identityHashCode = 0;
            }

            @Override
            public int hashCode() {
                return identityHashCode;
            }

            @Override
            public boolean equals(Object other) {
                return referent != null && referent == referentOf(other);
            }
        }
    }

    public static boolean handleEvent(Object presenter, Object event, boolean longPress) {
        if (!patchApplied) return false;

        Object action = findActionEntry(event);
        if (!isDownloadAction(action)) return false;

        try {
            Context context = NewXUtils.findUsableActivity(null);
            Object post = getPresenterPost(presenter);
            if (context == null || post == null) {
                NewXInAppNotification.show("Could not find the selected post");
                return true;
            }

            // Media parsing and storage IPC run off the UI thread; the picker and result
            // toasts are posted back. The event is still consumed synchronously so the
            // native share handler does not run for the download action.
            Context applicationContext = context.getApplicationContext();
            Context safeContext = applicationContext != null ? applicationContext : context;
            CLICK_EXECUTOR.execute(() -> resolveAndPresent(safeContext, post, longPress));
            return true;
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to process inline download action", exception);
            NewXInAppNotification.show("Could not download post media");
            return true;
        }
    }

    private static void resolveAndPresent(Context context, Object post, boolean longPress) {
        final List<DownloadItem> downloads;
        final DownloadFileName.PostContext postContext;
        final String username;
        try {
            String postText = post.toString();
            downloads = downloadItems(mediaFor(post));
            postContext = DownloadFileName.PostContext.fromText(postText);
            username = NewXUtils.sourceUsername(postText);
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to process inline download action", exception);
            NewXUtils.runOnUiThread(() -> NewXInAppNotification.show("Could not download post media"));
            return;
        }
        if (downloads.isEmpty()) {
            NewXUtils.runOnUiThread(() ->
                    NewXInAppNotification.showForUser("No downloadable media found", username));
            return;
        }

        // Resolve destinations before queueing; never fall back to an app-private folder.
        DownloadDestination.DestinationState[] destinations = destinations(context, downloads);
        boolean imagesMissing = needsFolderPrompt(destinations[0]);
        boolean videosMissing = needsFolderPrompt(destinations[1]);
        if (imagesMissing || videosMissing) {
            if (imagesMissing) {
                DownloadDestination.captureDestination(
                        context,
                        DownloadDestination.MediaKind.IMAGES,
                        "first-run"
                );
            }
            if (videosMissing) {
                DownloadDestination.captureDestination(
                        context,
                        DownloadDestination.MediaKind.VIDEOS,
                        "first-run"
                );
            }
            NewXUtils.runOnUiThread(() -> promptForDestination(context, imagesMissing, videosMissing));
            return;
        }

        if (downloads.size() == 1) {
            DownloadItem single = downloads.get(0);
            // A saved quality preference makes a tap download straight away, which would leave no
            // way back to the chooser, so long press opens it. With "ask", tap already does.
            if (longPress) {
                if (single.resolutionOptions.size() > 1) {
                    showResolutionChooser(context, single, postContext, username, 0, 1);
                } else {
                    enqueueSingleDownload(context, single, postContext, username, 0, 1);
                }
                return;
            }
            DownloadItem preferred = autoSelectedOption(single);
            if (preferred != null) {
                enqueueSingleDownload(context, preferred, postContext, username, 0, 1);
            } else if (single.resolutionOptions.size() > 1) {
                showResolutionChooser(context, single, postContext, username, 0, 1);
            } else {
                enqueueSingleDownload(context, single, postContext, username, 0, 1);
            }
            return;
        }

        // Long press downloads everything without the picker.
        if (longPress) {
            enqueueAllDownloads(context, downloads, postContext, username);
            return;
        }
        NewXUtils.runOnUiThread(() -> showMediaPicker(context, downloads, username, postContext));
    }

    private static boolean isEnabled() {
        return SettingsRegistry.getBooleanOrDefault(SETTING_ID, false);
    }

    private static boolean hideWhenNoMedia() {
        return SettingsRegistry.getBooleanOrDefault(HIDE_NO_MEDIA_SETTING, true);
    }

    static boolean shouldInspectMedia(boolean hideNoMedia, boolean hasDownloadAction) {
        return hideNoMedia && !hasDownloadAction;
    }

    static boolean hasMedia(Object post) {
        if (post == null) return false;

        try {
            return !mediaFor(post).isEmpty();
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to check NewX post media", exception);
            return false;
        }
    }

    // Release-neutral placeholder: alpha and beta action constructors are injected at patch time.
    private static Object createDownloadAction() {
        throw unpatchedBridge("createDownloadAction");
    }

    private static boolean containsDownloadAction(List<?> actions) {
        for (Object action : actions) {
            if (isDownloadAction(action)) return true;
        }
        return false;
    }

    static void registerDownloadAction(Object action) {
        if (action == null) throw unpatchedBridge("createDownloadAction returned null");
        DOWNLOAD_ACTIONS.add(action);
    }

    /**
     * Identity classification of the injected download action. The inline action bar's shared
     * layout lambda calls this from injected smali to force IconOnly layout for the download slot,
     * so it must stay public and allocation-free.
     */
    public static boolean isDownloadAction(Object candidate) {
        return DOWNLOAD_ACTIONS.contains(candidate);
    }

    private static Object findActionEntry(Object event) {
        if (event == null) return null;

        try {
            for (Field field : event.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(event);
                if (isDownloadAction(value)) return value;
            }
        } catch (IllegalAccessException | RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to read the NewX inline action event", exception);
        }
        return null;
    }

    private static Object canonicalPost(Object post) {
        return getCanonicalPost(post);
    }

    // Alpha and beta model descriptors/getters differ; these bridges are resolved by the patch,
    // keeping release-specific types out of the extension's compile-time API.
    private static Object getCanonicalPost(Object post) {
        throw unpatchedBridge("getCanonicalPost");
    }

    private static Object getPostMedia(Object canonicalPost) {
        throw unpatchedBridge("getPostMedia");
    }

    private static Object getRepostedPost(Object post) {
        throw unpatchedBridge("getRepostedPost");
    }

    private static Object getRepostedCanonicalPost(Object repostedPost) {
        throw unpatchedBridge("getRepostedCanonicalPost");
    }

    private static Object getPresenterPost(Object presenter) {
        throw unpatchedBridge("getPresenterPost");
    }

    private static IllegalStateException unpatchedBridge(String bridgeName) {
        return new IllegalStateException("NewX inline download bridge was not patched: " + bridgeName);
    }

    private static Object postFor(Object presenter) {
        return getPresenterPost(presenter);
    }

    private static List<?> mediaFor(Object post) {
        if (post == null) return java.util.Collections.emptyList();

        List<?> canonicalMedia = mediaForCanonical(canonicalPost(post));
        Object repostedPost = getRepostedPost(post);
        List<?> repostedMedia = repostedPost == null
                ? java.util.Collections.emptyList()
                : mediaForCanonical(getRepostedCanonicalPost(repostedPost));
        return selectMedia(canonicalMedia, repostedMedia);
    }

    private static List<?> mediaForCanonical(Object canonicalPost) {
        if (canonicalPost == null) return java.util.Collections.emptyList();
        Object media = getPostMedia(canonicalPost);
        return media instanceof List<?> list ? list : java.util.Collections.emptyList();
    }

    static List<?> selectMedia(List<?> canonicalMedia, List<?> repostedMedia) {
        if (hasDownloadableMedia(canonicalMedia)) return canonicalMedia;
        if (hasDownloadableMedia(repostedMedia)) return repostedMedia;
        return java.util.Collections.emptyList();
    }

    private static boolean hasDownloadableMedia(List<?> media) {
        if (media == null) return false;

        for (Object item : media) {
            if (isDownloadableMedia(item)) return true;
        }
        return false;
    }

    /**
     * Checks only the fields needed to decide whether the media can be downloaded. This runs
     * while the inline action list is being composed, so full download metadata and thumbnail
     * URLs must stay on the click path in {@link #downloadItem}.
     */
    static boolean isDownloadableMedia(Object media) {
        if (media == null) return false;

        try {
            String value = media.toString();
            if (value == null) return false;
            if (value.startsWith("MediaContentImage(")) {
                return NewXUtils.isHttpUrl(ToStringParser.fieldValue(value, "imageUrl"));
            }
            return hasMp4Variant(value);
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to read NewX media", exception);
            return false;
        }
    }

    /** Boolean counterpart of {@link #mp4Variants}; it intentionally does not parse bitrates
     * or allocate a {@link Variant}. */
    private static boolean hasMp4Variant(String value) {
        String prefix = "MediaVariant(url=";
        int offset = 0;
        while (true) {
            int start = value.indexOf(prefix, offset);
            if (start < 0) return false;
            start += prefix.length();
            int bitRateStart = value.indexOf(", bitRate=", start);
            int contentTypeStart = value.indexOf(", contentType=", bitRateStart);
            int end = value.indexOf(')', contentTypeStart);
            if (bitRateStart < 0 || contentTypeStart < 0 || end < 0) return false;

            String url = value.substring(start, bitRateStart);
            String contentType = value.substring(contentTypeStart + 14, end);
            if (NewXUtils.isHttpUrl(url) &&
                    (contentType.equalsIgnoreCase("video/mp4") || NewXUtils.containsIgnoreCaseAscii(url, ".mp4"))) {
                return true;
            }
            offset = end + 1;
        }
    }

    static List<DownloadItem> downloadItems(List<?> media) {
        List<DownloadItem> downloads = new ArrayList<>(media.size());
        for (Object item : media) {
            if (item == null) continue;

            try {
                DownloadItem download = downloadItem(item);
                if (download != null) downloads.add(download);
            } catch (RuntimeException exception) {
                NewXLogger.printException(() -> "Failed to read NewX media", exception);
            }
        }
        return downloads;
    }

    private static DownloadItem downloadItem(Object media) {
        String value = media.toString();
        String thumbnailCacheUrl = thumbnailCacheUrlForMedia(value);
        String thumbnailUrl = thumbnailUrlForImage(thumbnailCacheUrl);
        if (value.startsWith("MediaContentImage(")) {
            String url = ToStringParser.fieldValue(value, "imageUrl");
            if (!NewXUtils.isHttpUrl(url)) return null;
            return imageDownloadItem(value, url, thumbnailUrl, thumbnailCacheUrl);
        }

        List<Variant> variants = mp4Variants(value);
        if (variants.isEmpty()) return null;

        String label = value.startsWith("MediaContentGif(") ? "GIF" : "Video";
        List<DownloadItem> options = new ArrayList<>(variants.size());
        for (Variant variant : variants) {
            String optionLabel = variant.resolution != null ? variant.resolution : label;
            String detail = formatBitRate(variant.bitRate);
            options.add(new DownloadItem(
                    variant.url,
                    "mp4",
                    "video/mp4",
                    optionLabel,
                    thumbnailUrl,
                    thumbnailCacheUrl,
                    variant.resolution,
                    detail,
                    null,
                    java.util.Collections.emptyList()
            ));
        }
        // mp4Variants sorts by descending bitrate, so the first entry is the current default.
        DownloadItem best = options.get(0);
        return new DownloadItem(
                best.url,
                "mp4",
                "video/mp4",
                label,
                thumbnailUrl,
                thumbnailCacheUrl,
                best.resolution,
                null,
                null,
                options
        );
    }

    /**
     * Builds the image download item plus its twimg size variants. The original resolution comes
     * from the model's {@code originalImgWidth}/{@code originalImgHeight} fields; the named sizes
     * are the same ones the app's own image loader requests, scaled to fit their cap.
     */
    private static DownloadItem imageDownloadItem(
            String value,
            String url,
            String thumbnailUrl,
            String thumbnailCacheUrl
    ) {
        int[] size = imageOriginalSize(value);
        String resolution = resolutionLabel(size);
        List<DownloadItem> options = new ArrayList<>();
        options.add(new DownloadItem(
                originalImageUrl(url),
                "jpg",
                "image/jpeg",
                "Original",
                thumbnailUrl,
                thumbnailCacheUrl,
                resolution,
                resolution,
                "original",
                java.util.Collections.emptyList()
        ));

        if (size != null && isTwimgHost(url)) {
            for (int index = 0; index < NAMED_IMAGE_SIZES.length; index++) {
                int cap = NAMED_IMAGE_CAPS[index];
                String optionResolution = resolutionLabel(scaleToFit(size[0], size[1], cap));
                options.add(new DownloadItem(
                        imageUrlWithName(url, NAMED_IMAGE_SIZES[index]),
                        "jpg",
                        "image/jpeg",
                        friendlySizeLabel(NAMED_IMAGE_SIZES[index]),
                        thumbnailUrl,
                        thumbnailCacheUrl,
                        optionResolution,
                        optionResolution,
                        NAMED_IMAGE_SIZES[index],
                        java.util.Collections.emptyList()
                ));
            }
        }

        // Named sizes that the CDN serves at the original dimensions (because the original is
        // smaller than the cap) are not real choices, so the chooser only shows distinct sizes.
        options = dedupeByResolution(options);

        DownloadItem original = options.get(0);
        return new DownloadItem(
                original.url,
                "jpg",
                "image/jpeg",
                "Image",
                thumbnailUrl,
                thumbnailCacheUrl,
                resolution,
                null,
                null,
                options
        );
    }

    /** Keeps the first (best) option for each distinct pixel size. */
    private static List<DownloadItem> dedupeByResolution(List<DownloadItem> options) {
        List<DownloadItem> unique = new ArrayList<>(options.size());
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (DownloadItem option : options) {
            String key = option.resolution != null ? option.resolution : "url:" + option.url;
            if (seen.add(key)) unique.add(option);
        }
        return unique;
    }

    private static String friendlySizeLabel(String name) {
        switch (name) {
            case "large":
                return "Large";
            case "medium":
                return "Medium";
            case "small":
                return "Small";
            case "4096x4096":
                return "4096px";
            default:
                return name;
        }
    }

    private static int[] imageOriginalSize(String value) {
        int width = parsePositiveInt(ToStringParser.fieldValue(value, "originalImgWidth"));
        int height = parsePositiveInt(ToStringParser.fieldValue(value, "originalImgHeight"));
        if (width <= 0 || height <= 0) return null;
        return new int[] {width, height};
    }

    private static int parsePositiveInt(String value) {
        if (value == null) return 0;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String resolutionLabel(int[] size) {
        return size == null || size[0] <= 0 || size[1] <= 0 ? null : size[0] + "x" + size[1];
    }

    /** NewX variant bitrates are bits per second, not kbps. */
    static String formatBitRate(int bitRate) {
        if (bitRate <= 0) return null;
        if (bitRate >= 1_000_000) {
            long tenths = Math.round(bitRate / 100_000.0);
            return (tenths / 10) + "." + (tenths % 10) + " Mbps";
        }
        if (bitRate >= 1_000) {
            return Math.round(bitRate / 1_000.0) + " kbps";
        }
        return bitRate + " bps";
    }

    /** Scales to fit inside a square cap without upscaling, matching twimg's named sizes. */
    private static int[] scaleToFit(int width, int height, int cap) {
        int longest = Math.max(width, height);
        if (longest <= cap) return new int[] {width, height};
        double scale = (double) cap / longest;
        return new int[] {(int) Math.round(width * scale), (int) Math.round(height * scale)};
    }

    private static boolean isTwimgHost(String url) {
        String host = hostOf(url);
        return host != null && (host.equals("twimg.com") || host.endsWith(".twimg.com"));
    }

    /** Host without the port/user-info, parsed without the Android Uri dependency. */
    private static String hostOf(String url) {
        if (url == null) return null;
        int schemeIndex = url.indexOf("://");
        if (schemeIndex < 0) return null;

        int start = schemeIndex + 3;
        int end = url.length();
        for (int index = start; index < url.length(); index++) {
            char character = url.charAt(index);
            if (character == '/' || character == '?' || character == '#'
                    || character == ':') {
                end = index;
                break;
            }
        }
        if (start >= end) return null;

        String authority = url.substring(start, end);
        int userInfoEnd = authority.lastIndexOf('@');
        if (userInfoEnd >= 0) authority = authority.substring(userInfoEnd + 1);
        return authority.isEmpty() ? null : authority.toLowerCase();
    }

    private static String imageUrlWithName(String url, String name) {
        if (!isTwimgHost(url)) return url;
        int cut = url.length();
        int queryIndex = url.indexOf('?');
        if (queryIndex >= 0) cut = Math.min(cut, queryIndex);
        int fragmentIndex = url.indexOf('#');
        if (fragmentIndex >= 0) cut = Math.min(cut, fragmentIndex);
        return url.substring(0, cut) + "?format=jpg&name=" + name;
    }

    private static String originalImageUrl(String url) {
        return imageUrlWithName(url, "orig");
    }

    static String thumbnailUrlForMedia(String mediaText) {
        return thumbnailUrlForImage(thumbnailCacheUrlForMedia(mediaText));
    }

    static String thumbnailCacheUrlForMedia(String mediaText) {
        if (mediaText == null) return null;

        if (mediaText.startsWith("MediaContentImage(")) {
            return ToStringParser.fieldValue(mediaText, "imageUrl");
        }

        if (mediaText.startsWith("MediaContentVideo(")) {
            String previewImage = ToStringParser.fieldValue(mediaText, "previewImage");
            return ToStringParser.fieldValue(previewImage, "imageUrl");
        }

        if (mediaText.startsWith("MediaContentGif(")) {
            return ToStringParser.fieldValue(mediaText, "previewUrl");
        }

        return null;
    }

    private static String thumbnailUrlForImage(String url) {
        if (!NewXUtils.isHttpUrl(url)) return null;
        if (!isTwimgHost(url)) return url;
        return imageUrlWithName(url, "small");
    }

    /** All downloadable mp4 variants, highest bitrate first. */
    private static List<Variant> mp4Variants(String value) {
        List<Variant> variants = new ArrayList<>();
        String prefix = "MediaVariant(url=";
        int offset = 0;
        while (true) {
            int start = value.indexOf(prefix, offset);
            if (start < 0) break;
            start += prefix.length();
            int bitRateStart = value.indexOf(", bitRate=", start);
            int contentTypeStart = value.indexOf(", contentType=", bitRateStart);
            int end = value.indexOf(')', contentTypeStart);
            if (bitRateStart < 0 || contentTypeStart < 0 || end < 0) break;

            String url = value.substring(start, bitRateStart);
            String contentType = value.substring(contentTypeStart + 14, end);
            if (NewXUtils.isHttpUrl(url) &&
                    (contentType.equalsIgnoreCase("video/mp4") || NewXUtils.containsIgnoreCaseAscii(url, ".mp4"))) {
                variants.add(new Variant(
                        url,
                        parseBitRate(value.substring(bitRateStart + 10, contentTypeStart)),
                        resolutionFromUrl(url)
                ));
            }
            offset = end + 1;
        }
        variants.sort((left, right) -> Integer.compare(right.bitRate, left.bitRate));
        return variants;
    }

    private static String resolutionFromUrl(String url) {
        Matcher matcher = VIDEO_RESOLUTION_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int parseBitRate(String value) {
        try {
            return value.equals("null") ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void showMediaPicker(
            Context context,
            List<DownloadItem> downloads,
            String username,
            DownloadFileName.PostContext postContext
    ) {
        MediaPickerDialog.show(
                context,
                downloads,
                username,
                new MediaPickerDialog.OnMediaSelectedListener() {
                    @Override
                    public void onDownloadItem(int index) {
                        if (index >= 0 && index < downloads.size()) {
                            DownloadItem item = downloads.get(index);
                            DownloadItem preferred = autoSelectedOption(item);
                            enqueueSingleDownload(
                                    context,
                                    preferred != null ? preferred : item,
                                    postContext,
                                    username,
                                    index,
                                    downloads.size()
                            );
                        }
                    }

                    @Override
                    public void onDownloadAll() {
                        enqueueAllDownloads(context, downloads, postContext, username);
                    }

                    @Override
                    public void onDownloadAndMerge(List<DownloadItem> items) {
                        MediaMerger.downloadAndMerge(context, items, username, postContext);
                    }

                    @Override
                    public void onShowResolutions(int index) {
                        if (index >= 0 && index < downloads.size()) {
                            showResolutionChooser(
                                    context,
                                    downloads.get(index),
                                    postContext,
                                    username,
                                    index,
                                    downloads.size()
                            );
                        }
                    }
                }
        );
    }

    private static void showResolutionChooser(
            Context context,
            DownloadItem item,
            DownloadFileName.PostContext postContext,
            String username,
            int index,
            int mediaCount
    ) {
        NewXUtils.runOnUiThread(() -> ResolutionChooserDialog.show(
                context,
                item,
                username,
                option -> enqueueSingleDownload(
                        context,
                        option,
                        postContext,
                        username,
                        index,
                        mediaCount,
                        true
                )
        ));
    }

    /**
     * Returns the option matching the saved quality preference, or null when the chooser should be
     * shown. Photos use the exact twimg size tiers; videos and GIFs use a best-effort variant
     * policy since the model has no per-variant resolution field.
     */
    private static DownloadItem autoSelectedOption(DownloadItem item) {
        if (item == null || item.resolutionOptions.size() <= 1) return null;

        if (isImageItem(item)) {
            String preference = SettingsRegistry.getStringOrDefault(IMAGE_QUALITY_SETTING, QUALITY_ORIGINAL);
            return QUALITY_ASK.equals(preference) ? null : selectImageOption(item, preference);
        }

        String preference = SettingsRegistry.getStringOrDefault(VIDEO_QUALITY_SETTING, QUALITY_HIGHEST);
        return QUALITY_ASK.equals(preference) ? null : selectVideoOption(item, preference);
    }

    private static boolean isImageItem(DownloadItem item) {
        return item.mimeType != null && item.mimeType.startsWith("image/");
    }

    static DownloadItem selectImageOption(DownloadItem item, String preference) {
        for (DownloadItem option : item.resolutionOptions) {
            if (preference.equals(option.qualityKey)) return option;
        }
        // Non-twimg photos only expose the original, so fall back to the best available size.
        return item.resolutionOptions.get(0);
    }

    static DownloadItem selectVideoOption(DownloadItem item, String preference) {
        List<DownloadItem> options = item.resolutionOptions;
        if ("highest".equals(preference)) return options.get(0);
        if ("lowest".equals(preference)) return options.get(options.size() - 1);

        int target = targetHeight(preference);
        if (target <= 0) return options.get(0);

        // Prefer the largest variant at or below the requested quality. Quality tiers name the
        // short side (1080p is 1920x1080 or 1080x1920), which is the width on portrait videos.
        DownloadItem atOrBelow = null;
        DownloadItem nextAbove = null;
        int atOrBelowQuality = -1;
        int nextAboveQuality = Integer.MAX_VALUE;
        for (DownloadItem option : options) {
            int quality = resolutionShortSide(option.resolution);
            if (quality <= 0) continue;
            if (quality <= target) {
                if (quality > atOrBelowQuality) {
                    atOrBelowQuality = quality;
                    atOrBelow = option;
                }
            } else if (quality < nextAboveQuality) {
                nextAboveQuality = quality;
                nextAbove = option;
            }
        }
        if (atOrBelow != null) return atOrBelow;
        if (nextAbove != null) return nextAbove;
        return options.get(0);
    }

    private static int targetHeight(String preference) {
        switch (preference) {
            case "1080p":
                return 1080;
            case "720p":
                return 720;
            case "480p":
                return 480;
            case "360p":
                return 360;
            default:
                return 0;
        }
    }

    /**
     * Quality tier of a variant: the shorter edge, so portrait videos rank by width rather than by
     * their (much larger) height.
     */
    private static int resolutionShortSide(String resolution) {
        if (resolution == null) return 0;
        int separator = resolution.indexOf('x');
        if (separator < 0) return 0;
        int width = parsePositiveInt(resolution.substring(0, separator));
        int height = parsePositiveInt(resolution.substring(separator + 1));
        if (width <= 0 || height <= 0) return 0;
        return Math.min(width, height);
    }

    private static void enqueueAllDownloads(
            Context context,
            List<DownloadItem> downloads,
            DownloadFileName.PostContext postContext,
            String username
    ) {
        Context applicationContext = context.getApplicationContext();
        Context safeContext = applicationContext != null ? applicationContext : context;
        List<DownloadItem> items = new ArrayList<>(downloads);
        // Name resolution and document creation hit the provider per item; keep off the tap path.
        CLICK_EXECUTOR.execute(() -> {
            final DownloadDestination.ConflictPolicy policy;
            try {
                policy = DownloadDestination.conflictPolicy();
            } catch (RuntimeException exception) {
                NewXLogger.printException(() -> "Unsupported NewX download conflict policy", exception);
                NewXUtils.runOnUiThread(() ->
                        NewXInAppNotification.showForUser("Could not start download", username));
                return;
            }

            int queued = 0;
            int skipped = 0;
            int failed = 0;
            int lost = 0;
            for (int index = 0; index < items.size(); index++) {
                DownloadItem chosen = autoSelectedOption(items.get(index));
                if (chosen == null) chosen = items.get(index);
                switch (enqueueDownload(
                        safeContext,
                        chosen,
                        postContext,
                        username,
                        index,
                        items.size(),
                        policy,
                        false
                )) {
                    case QUEUED -> queued++;
                    case SKIPPED -> skipped++;
                    case FAILED -> failed++;
                    case DESTINATION_LOST -> lost++;
                }
            }
            int queuedResult = queued;
            int skippedResult = skipped;
            int failedResult = failed;
            int lostResult = lost;
            NewXUtils.runOnUiThread(() ->
                    showQueueResult(queuedResult, skippedResult, failedResult, lostResult, username));
        });
    }

    private static void enqueueSingleDownload(
            Context context,
            DownloadItem download,
            DownloadFileName.PostContext postContext,
            String username,
            int index,
            int mediaCount
    ) {
        enqueueSingleDownload(context, download, postContext, username, index, mediaCount, false);
    }

    private static void enqueueSingleDownload(
            Context context,
            DownloadItem download,
            DownloadFileName.PostContext postContext,
            String username,
            int index,
            int mediaCount,
            boolean explicitResolution
    ) {
        Context applicationContext = context.getApplicationContext();
        Context safeContext = applicationContext != null ? applicationContext : context;
        // Document creation does provider IPC; keep off the tap handler.
        CLICK_EXECUTOR.execute(() -> {
            final EnqueueState state;
            try {
                state = enqueueDownload(
                        safeContext,
                        download,
                        postContext,
                        username,
                        index,
                        mediaCount,
                        DownloadDestination.conflictPolicy(),
                        explicitResolution
                );
            } catch (RuntimeException exception) {
                NewXLogger.printException(() -> "Failed to start NewX media download", exception);
                NewXUtils.runOnUiThread(() ->
                        reportDownloadStatus("Could not start download", username));
                return;
            }
            NewXUtils.runOnUiThread(() -> reportEnqueueResult(state, username));
        });
    }

    private static synchronized EnqueueState enqueueDownload(
            Context context,
            DownloadItem download,
            DownloadFileName.PostContext postContext,
            String username,
            int index,
            int mediaCount,
            DownloadDestination.ConflictPolicy policy,
            boolean explicitResolution
    ) {
        if (!NewXUtils.isHttpUrl(download.url)) return EnqueueState.FAILED;

        final DownloadDestination.MediaKind kind;
        try {
            kind = DownloadDestination.mediaKindFor(download.mimeType);
        } catch (IllegalArgumentException exception) {
            NewXLogger.printException(
                    () -> "Unsupported NewX download media type: " + download.mimeType,
                    exception
            );
            return EnqueueState.FAILED;
        }

        String template = DownloadSettings.filenameTemplate();
        String fileName = DownloadFileName.render(
                template,
                postContext,
                index,
                mediaCount,
                download.extension,
                download.resolution
        );
        // Only a chooser pick gets the automatic suffix; auto-selected preferences keep the plain
        // filename. An explicit {resolution} token already places it, so do not append twice.
        boolean templateUsesResolution = template != null
                && template.contains("{" + DownloadFileName.TOKEN_RESOLUTION + "}");
        if (explicitResolution && download.resolution != null && !templateUsesResolution) {
            fileName = withResolutionSuffix(fileName, download.resolution);
        }

        final DownloadDestination.Target target;
        try {
            target = DownloadDestination.reserve(context, kind, fileName, download.mimeType, policy);
        } catch (IOException | RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to create the NewX download file", exception);
            // reserve() clears refused folders, so the next tap re-prompts.
            return DownloadDestination.isDestinationLoss(exception)
                    ? EnqueueState.DESTINATION_LOST
                    : EnqueueState.FAILED;
        }
        if (target == null) return EnqueueState.SKIPPED;

        // Post progress at enqueue time, not behind earlier transfers.
        int notificationId =
                DownloadDestination.beginDownloadNotification(context, target.fileName());
        // Keep transfers off CLICK_EXECUTOR so taps stay responsive.
        downloadAsync(context, download.url, target, username, notificationId);
        return EnqueueState.QUEUED;
    }

    static String withResolutionSuffix(String fileName, String resolution) {
        if (fileName == null || resolution == null || resolution.isEmpty()) return fileName;
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) return fileName + "_" + resolution;
        return fileName.substring(0, dot) + "_" + resolution + fileName.substring(dot);
    }

    private static void downloadAsync(
            Context context,
            String url,
            DownloadDestination.Target target,
            String username,
            int notificationId
    ) {
        DOWNLOAD_EXECUTOR.execute(() -> {
            DownloadDestination.SaveState state;
            try {
                state = DownloadDestination.save(context, target, url, notificationId);
            } catch (RuntimeException exception) {
                // save() handles its own failures; this covers throws before it could clean up.
                NewXLogger.printException(() -> "Failed to download " + target.fileName(), exception);
                DownloadDestination.cancelNotification(context, notificationId);
                DownloadDestination.discard(context, target);
                state = DownloadDestination.SaveState.FAILED;
            }

            switch (state) {
                case SAVED -> {
                    // Success shows via the OS notification; without it, say so in-app.
                    if (!DownloadDestination.notificationsEnabled(context)) {
                        NewXUtils.runOnUiThread(() ->
                                reportDownloadStatus("Saved " + target.fileName(), username));
                    }
                }
                case DESTINATION_LOST -> NewXUtils.runOnUiThread(() ->
                        reportDownloadStatus(FOLDER_LOST_MESSAGE, username));
                case FAILED -> NewXUtils.runOnUiThread(() ->
                        reportDownloadStatus("Could not save " + target.fileName(), username));
            }
        });
    }

    /**
     * First-run gate. Downloads never fall back to an app-private folder: a destination that was
     * never chosen, or whose persisted grant is gone after a restore, aborts the action and offers
     * the picker instead.
     */
    private static void promptForDestination(
            Context context,
            boolean imagesMissing,
            boolean videosMissing
    ) {
        Activity activity = currentActivity();
        if (activity == null) {
            NewXInAppNotification.show("Set a download folder in Download options");
            return;
        }

        String requirement = imagesMissing && videosMissing
                ? "piko_newx_download_first_run_both"
                : imagesMissing
                        ? "piko_newx_download_first_run_images"
                        : "piko_newx_download_first_run_videos";

        LinearLayout body = dialogForm(activity);
        TextView retry = NewXSettingsUi.summaryText(activity);
        retry.setText(StringRef.str("piko_newx_download_first_run_retry"));
        body.addView(retry, new LinearLayout.LayoutParams(-1, -2));

        DialogView dialog = new DialogView(activity)
                .setTitle(StringRef.str("piko_newx_download_first_run_title"))
                .setSubtitle(StringRef.str(requirement))
                .setBodyView(body);
        dialog.getDialog().setCanceledOnTouchOutside(true);

        if (imagesMissing) {
            dialog.addButton(pickFolderButton(
                    activity,
                    dialog,
                    DownloadDestination.MediaKind.IMAGES,
                    "piko_newx_download_first_run_images_action"
            ));
        }
        if (videosMissing) {
            dialog.addButton(pickFolderButton(
                    activity,
                    dialog,
                    DownloadDestination.MediaKind.VIDEOS,
                    "piko_newx_download_first_run_videos_action"
            ));
        }

        ButtonView cancel = NewXSettingsUi.dialogButton(
                activity,
                StringRef.str("piko_newx_settings_cancel")
        );
        cancel.setOnClickListener(ignored -> dialog.dismiss());
        dialog.addButton(cancel);
        dialog.show();
    }

    private static ButtonView pickFolderButton(
            Activity activity,
            DialogView dialog,
            DownloadDestination.MediaKind kind,
            String labelResource
    ) {
        ButtonView button = NewXSettingsUi.dialogButton(activity, StringRef.str(labelResource));
        button.setOnClickListener(ignored -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, DownloadFolderPickerActivity.class)
                    .putExtra(DownloadFolderPickerActivity.KIND_EXTRA, kind.name()));
        });
        return button;
    }

    private static LinearLayout dialogForm(Context context) {
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Theme.dpToPx(context, 24f), 0, Theme.dpToPx(context, 24f), 0);
        return form;
    }

    /** Destination state per needed kind, or null when the action needs no media of that kind. */
    private static DownloadDestination.DestinationState[] destinations(
            Context context,
            List<DownloadItem> downloads
    ) {
        boolean needsImages = false;
        boolean needsVideos = false;
        for (DownloadItem item : downloads) {
            DownloadDestination.MediaKind kind;
            try {
                kind = DownloadDestination.mediaKindFor(item.mimeType);
            } catch (IllegalArgumentException exception) {
                // Unsupported media is reported per item; it must not block the whole action.
                continue;
            }
            if (kind == DownloadDestination.MediaKind.VIDEOS) {
                needsVideos = true;
            } else {
                needsImages = true;
            }
        }
        return new DownloadDestination.DestinationState[] {
                needsImages
                        ? DownloadDestination.destinationState(context, DownloadDestination.MediaKind.IMAGES)
                        : null,
                needsVideos
                        ? DownloadDestination.destinationState(context, DownloadDestination.MediaKind.VIDEOS)
                        : null,
        };
    }

    /** True when the action needs this kind but its folder is unusable. */
    private static boolean needsFolderPrompt(DownloadDestination.DestinationState state) {
        return state != null && !DownloadDestination.isUsable(state);
    }

    /** In-app message plus toast fallback, so outcomes never depend on the host being ready. */
    static void reportDownloadStatus(String message, String username) {
        String formatted = NewXInAppNotification.formatForUser(message, username);
        NewXInAppNotification.tryShow(formatted);
        Utils.showToastShort(formatted);
    }

    private static void reportEnqueueResult(EnqueueState state, String username) {
        switch (state) {
            case QUEUED -> NewXInAppNotification.showForUser("Download started", username);
            case SKIPPED -> NewXInAppNotification.showForUser("Already downloaded", username);
            case FAILED -> reportDownloadStatus("Could not start download", username);
            case DESTINATION_LOST -> reportDownloadStatus(FOLDER_LOST_MESSAGE, username);
        }
    }

    private static void showQueueResult(int queued, int skipped, int failed, int lost, String username) {
        if (failed == 0 && skipped == 0 && lost == 0) {
            String message = queued == 1 ? "Download started" : queued + " downloads started";
            NewXInAppNotification.showForUser(message, username);
            return;
        }
        if (queued == 0) {
            if (failed == 0 && lost == 0 && skipped > 0) {
                NewXInAppNotification.showForUser(skipped == 1
                        ? "Already downloaded"
                        : skipped + " media already downloaded", username);
                return;
            }
            if (lost > 0) {
                reportDownloadStatus(FOLDER_LOST_MESSAGE, username);
                return;
            }
            reportDownloadStatus("Could not start download", username);
            return;
        }
        List<String> parts = new ArrayList<>();
        parts.add(queued == 1 ? "1 download started" : queued + " downloads started");
        if (skipped > 0) parts.add(skipped == 1
                ? "1 already downloaded"
                : skipped + " already downloaded");
        if (failed > 0) parts.add(failed == 1 ? "1 failed" : failed + " failed");
        if (lost > 0) parts.add(lost == 1 ? "1 needs a new folder" : lost + " need a new folder");
        reportDownloadStatus(String.join(", ", parts), username);
    }


    static Activity currentActivity() {
        return NewXUtils.findUsableActivity(null);
    }


    private enum EnqueueState {
        QUEUED,
        SKIPPED,
        FAILED,
        /** Stored folder was refused and cleared. */
        DESTINATION_LOST,
    }

    enum ConflictBehavior {
        OVERWRITE,
        RENAME,
        SKIP,
    }

    static final class DownloadItem {
        final String url;
        final String extension;
        final String mimeType;
        final String label;
        final String thumbnailUrl;
        final String thumbnailCacheUrl;
        /** Original/known resolution as "WxH", or null when the model does not expose one. */
        final String resolution;
        /** Secondary chooser text (bitrate, size name), or null. */
        final String detail;
        /** Stable key into the saved quality preference (image sizes), or null. */
        final String qualityKey;
        /** Selectable sizes/variants for this media; empty when there is nothing to choose. */
        final List<DownloadItem> resolutionOptions;

        DownloadItem(String url, String extension, String mimeType, String label) {
            this(url, extension, mimeType, label, null, null);
        }

        DownloadItem(
                String url,
                String extension,
                String mimeType,
                String label,
                String thumbnailUrl
        ) {
            this(url, extension, mimeType, label, thumbnailUrl, thumbnailUrl);
        }

        DownloadItem(
                String url,
                String extension,
                String mimeType,
                String label,
                String thumbnailUrl,
                String thumbnailCacheUrl
        ) {
            this(
                    url,
                    extension,
                    mimeType,
                    label,
                    thumbnailUrl,
                    thumbnailCacheUrl,
                    null,
                    null,
                    null,
                    java.util.Collections.emptyList()
            );
        }

        DownloadItem(
                String url,
                String extension,
                String mimeType,
                String label,
                String thumbnailUrl,
                String thumbnailCacheUrl,
                String resolution,
                String detail,
                String qualityKey,
                List<DownloadItem> resolutionOptions
        ) {
            this.url = url;
            this.extension = extension;
            this.mimeType = mimeType;
            this.label = label;
            this.thumbnailUrl = thumbnailUrl;
            this.thumbnailCacheUrl = thumbnailCacheUrl;
            this.resolution = resolution;
            this.detail = detail;
            this.qualityKey = qualityKey;
            this.resolutionOptions = resolutionOptions == null
                    ? java.util.Collections.emptyList()
                    : resolutionOptions;
        }
    }

    private static final class Variant {
        final String url;
        final int bitRate;
        final String resolution;

        Variant(String url, int bitRate, String resolution) {
            this.url = url;
            this.bitRate = bitRate;
            this.resolution = resolution;
        }
    }
}
