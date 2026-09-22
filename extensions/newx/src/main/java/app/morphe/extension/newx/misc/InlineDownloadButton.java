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
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.shared.StringRef;
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

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.newx.utils.ToStringParser;

@SuppressWarnings("unused")
public final class InlineDownloadButton {
    private static final String SETTING_ID = "newx.content.inline_download_button";
    private static final String HIDE_NO_MEDIA_SETTING = "newx.content.inline_download_hide_no_media";
    /** Concurrent transfers. Small enough to stay gentle on the connection pool and providers. */
    private static final int TRANSFER_THREADS = 4;
    private static final ExecutorService DOWNLOAD_EXECUTOR =
            Executors.newFixedThreadPool(TRANSFER_THREADS);
    // Click-time media resolution and SAF document creation run here so the inline-action event
    // handler returns immediately instead of blocking the UI thread on post toString parsing and
    // provider IPC. DOWNLOAD_EXECUTOR stays reserved for the transfers themselves, which can be
    // busy copying large videos.
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
            // Materialize the (large) post toString once. Rebuilding it for every field lookup was
            // a dominant click-path cost before any download work started.
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

        // A destination that was never chosen, or whose persisted grant is gone after a restore,
        // has to be resolved before any item is queued. Never fall back to an app-private folder.
        boolean[] missing = missingDestinations(context, downloads);
        if (missing[0] || missing[1]) {
            boolean imagesMissing = missing[0];
            boolean videosMissing = missing[1];
            NewXUtils.runOnUiThread(() -> promptForDestination(context, imagesMissing, videosMissing));
            return;
        }

        // Long press is the "download everything" shortcut and skips the picker entirely.
        if (longPress) {
            enqueueAllDownloads(context, downloads, postContext, username);
            return;
        }
        if (downloads.size() == 1) {
            enqueueSingleDownload(context, downloads.get(0), postContext, username, 0, 1);
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

    private static boolean isDownloadAction(Object candidate) {
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

    /** Boolean counterpart of {@link #bestMp4Variant}; it intentionally does not parse bitrates
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

    private static List<DownloadItem> downloadItems(List<?> media) {
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
            return new DownloadItem(
                    originalImageUrl(url),
                    "jpg",
                    "image/jpeg",
                    "Image",
                    thumbnailUrl,
                    thumbnailCacheUrl
            );
        }

        Variant bestVariant = bestMp4Variant(value);
        if (bestVariant == null) return null;

        String label = value.startsWith("MediaContentGif(") ? "GIF" : "Video";
        return new DownloadItem(
                bestVariant.url,
                "mp4",
                "video/mp4",
                label,
                thumbnailUrl,
                thumbnailCacheUrl
        );
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

        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null || !(host.equals("twimg.com") || host.endsWith(".twimg.com"))) {
            return url;
        }

        return uri.buildUpon()
                .clearQuery()
                .appendQueryParameter("format", "jpg")
                .appendQueryParameter("name", "small")
                .build()
                .toString();
    }

    private static Variant bestMp4Variant(String value) {
        Variant best = null;
        String prefix = "MediaVariant(url=";
        int offset = 0;
        while (true) {
            int start = value.indexOf(prefix, offset);
            if (start < 0) return best;
            start += prefix.length();
            int bitRateStart = value.indexOf(", bitRate=", start);
            int contentTypeStart = value.indexOf(", contentType=", bitRateStart);
            int end = value.indexOf(')', contentTypeStart);
            if (bitRateStart < 0 || contentTypeStart < 0 || end < 0) return best;

            String url = value.substring(start, bitRateStart);
            String contentType = value.substring(contentTypeStart + 14, end);
            if (NewXUtils.isHttpUrl(url) &&
                    (contentType.equalsIgnoreCase("video/mp4") || NewXUtils.containsIgnoreCaseAscii(url, ".mp4"))) {
                int bitRate = parseBitRate(value.substring(bitRateStart + 10, contentTypeStart));
                if (best == null || bitRate > best.bitRate) best = new Variant(url, bitRate);
            }
            offset = end + 1;
        }
    }

    private static int parseBitRate(String value) {
        try {
            return value.equals("null") ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String originalImageUrl(String url) {
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null || !host.endsWith("twimg.com")) return url;

        return uri.buildUpon()
                .clearQuery()
                .appendQueryParameter("format", "jpg")
                .appendQueryParameter("name", "orig")
                .build()
                .toString();
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
                            enqueueSingleDownload(context, downloads.get(index), postContext, username, index, downloads.size());
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
                }
        );
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
        // Name resolution and document creation hit the provider per item; keep it off the
        // picker button path.
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
            for (int index = 0; index < items.size(); index++) {
                switch (enqueueDownload(
                        safeContext,
                        items.get(index),
                        postContext,
                        username,
                        index,
                        items.size(),
                        policy
                )) {
                    case QUEUED -> queued++;
                    case SKIPPED -> skipped++;
                    case FAILED -> failed++;
                }
            }
            int queuedResult = queued;
            int skippedResult = skipped;
            int failedResult = failed;
            NewXUtils.runOnUiThread(() ->
                    showQueueResult(queuedResult, skippedResult, failedResult, username));
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
        Context applicationContext = context.getApplicationContext();
        Context safeContext = applicationContext != null ? applicationContext : context;
        // Document creation does provider IPC; the tap handler must not wait for it.
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
                        DownloadDestination.conflictPolicy()
                );
            } catch (RuntimeException exception) {
                NewXLogger.printException(() -> "Failed to start NewX media download", exception);
                NewXUtils.runOnUiThread(() ->
                        NewXInAppNotification.showForUser("Could not start download", username));
                return;
            }
            NewXUtils.runOnUiThread(() -> {
                switch (state) {
                    case QUEUED -> NewXInAppNotification.showForUser("Download started", username);
                    case SKIPPED -> NewXInAppNotification.showForUser("Already downloaded", username);
                    case FAILED -> NewXInAppNotification.showForUser("Could not start download", username);
                }
            });
        });
    }

    private static synchronized EnqueueState enqueueDownload(
            Context context,
            DownloadItem download,
            DownloadFileName.PostContext postContext,
            String username,
            int index,
            int mediaCount,
            DownloadDestination.ConflictPolicy policy
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

        String fileName = DownloadFileName.render(
                DownloadSettings.filenameTemplate(),
                postContext,
                index,
                mediaCount,
                download.extension
        );

        final DownloadDestination.Target target;
        try {
            target = DownloadDestination.reserve(context, kind, fileName, download.mimeType, policy);
        } catch (IOException | RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to create the NewX download file", exception);
            return EnqueueState.FAILED;
        }
        if (target == null) return EnqueueState.SKIPPED;

        // Post the progress notification now, at enqueue time. Creating it on the transfer thread
        // made it wait for every queued download ahead of it to finish streaming first.
        int notificationId =
                DownloadDestination.beginDownloadNotification(context, target.fileName());
        // A large video must not occupy CLICK_EXECUTOR: queue-result reporting and further taps
        // run there.
        downloadAsync(context, download.url, target, username, notificationId);
        return EnqueueState.QUEUED;
    }

    private static void downloadAsync(
            Context context,
            String url,
            DownloadDestination.Target target,
            String username,
            int notificationId
    ) {
        DOWNLOAD_EXECUTOR.execute(() -> {
            boolean saved;
            try {
                saved = DownloadDestination.save(context, target, url, notificationId);
            } catch (RuntimeException exception) {
                NewXLogger.printException(() -> "Failed to download " + target.fileName(), exception);
                DownloadDestination.cancelNotification(context, notificationId);
                DownloadDestination.discard(context, target);
                saved = false;
            }

            // Success is reported by the OS download notification, which is already on screen.
            // Only surface a failure, since that notification is cancelled on error.
            boolean failed = !saved;
            if (failed) {
                NewXUtils.runOnUiThread(() -> NewXInAppNotification.showForUser(
                        "Could not save " + target.fileName(),
                        username
                ));
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
            // The picker writes the setting itself, so the user just taps download again.
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

    /** Reports which media types in this action have no usable destination. */
    private static boolean[] missingDestinations(Context context, List<DownloadItem> downloads) {
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
        return new boolean[] {
                needsImages
                        && !DownloadDestination.isConfigured(context, DownloadDestination.MediaKind.IMAGES),
                needsVideos
                        && !DownloadDestination.isConfigured(context, DownloadDestination.MediaKind.VIDEOS),
        };
    }

    private static void showQueueResult(int queued, int skipped, int failed, String username) {
        if (failed == 0 && skipped == 0) {
            String message = queued == 1 ? "Download started" : queued + " downloads started";
            NewXInAppNotification.showForUser(message, username);
            return;
        }
        if (queued == 0) {
            if (failed == 0 && skipped > 0) {
                NewXInAppNotification.showForUser(skipped == 1
                        ? "Already downloaded"
                        : skipped + " media already downloaded", username);
                return;
            }
            NewXInAppNotification.showForUser("Could not start download", username);
            return;
        }
        List<String> parts = new ArrayList<>();
        parts.add(queued == 1 ? "1 download started" : queued + " downloads started");
        if (skipped > 0) parts.add(skipped == 1
                ? "1 already downloaded"
                : skipped + " already downloaded");
        if (failed > 0) parts.add(failed == 1 ? "1 failed" : failed + " failed");
        NewXInAppNotification.showForUser(String.join(", ", parts), username);
    }


    static Activity currentActivity() {
        return NewXUtils.findUsableActivity(null);
    }


    private enum EnqueueState {
        QUEUED,
        SKIPPED,
        FAILED,
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
            this.url = url;
            this.extension = extension;
            this.mimeType = mimeType;
            this.label = label;
            this.thumbnailUrl = thumbnailUrl;
            this.thumbnailCacheUrl = thumbnailCacheUrl;
        }
    }

    private static final class Variant {
        final String url;
        final int bitRate;

        Variant(String url, int bitRate) {
            this.url = url;
            this.bitRate = bitRate;
        }
    }
}
