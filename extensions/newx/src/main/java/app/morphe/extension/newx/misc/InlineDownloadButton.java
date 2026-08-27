package app.morphe.extension.newx.misc;

import android.annotation.SuppressLint;
import android.app.Activity;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.newx.utils.NewXUtils;
import android.app.AlertDialog;
import android.app.Application;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.newx.utils.ToStringParser;

@SuppressWarnings("unused")
public final class InlineDownloadButton {
    private static final String SETTING_ID = "newx.content.inline_download_button";
    private static final String DOWNLOAD_DIRECTORY = "Twitter";
    // Primary public directories. These literal values match Environment.DIRECTORY_PICTURES /
    // DIRECTORY_MOVIES and are exactly the strings MediaStore accepts as RELATIVE_PATH primary
    // directories. Kept explicit so the path is deterministic and not dependent on framework
    // constants that resolve to null under some test runtimes.
    private static final String PICTURES_DIRECTORY = "Pictures";
    private static final String MOVIES_DIRECTORY = "Movies";

    /** Primary public directory for a MIME type: videos publish to Movies, all other media
     *  (images) to Pictures. Required because MediaStore restricts each collection to specific
     *  primary directories (Video allows only DCIM/Movies; Images allows only DCIM/Pictures). */
    private static String primaryDirectoryForMime(String mimeType) {
        return mimeType != null && mimeType.startsWith("video/")
                ? MOVIES_DIRECTORY
                : PICTURES_DIRECTORY;
    }

    /** Scoped-storage relative path under which a download is published, e.g. "Movies/Twitter/"
     *  for videos and "Pictures/Twitter/" for images. */
    static String relativeDownloadPath(String mimeType) {
        return primaryDirectoryForMime(mimeType) + "/" + DOWNLOAD_DIRECTORY + "/";
    }
    private static final String PENDING_DOWNLOADS_PREFS = "piko_newx_inline_downloads";
    private static final String CONFLICT_SETTING = "newx.content.inline_download_conflict";
    private static final ConflictBehavior DEFAULT_CONFLICT_BEHAVIOR = ConflictBehavior.SKIP;
    private static final int MAX_TRACKED_OBJECTS = 128;
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final List<WeakReference<Object>> DOWNLOAD_ACTIONS = new ArrayList<>();
    private static volatile boolean patchApplied;
    private static boolean initialized;
    private static boolean downloadReceiverRegistered;
    private static final ThreadLocal<Boolean> RENDERING_DOWNLOAD_ACTION = new ThreadLocal<>();

    private InlineDownloadButton() {
    }

    public static synchronized void initialize(Context context) {
        if (context == null) return;

        Context applicationContext = context.getApplicationContext();
        if (!(applicationContext instanceof Application application)) return;

        patchApplied = true;
        if (initialized) return;

        NewXUtils.initialize(application);
        registerDownloadReceiver(application);
        resumePendingDownloads(application);
        initialized = true;
    }

    public static List<?> addAction(List<?> actions, Object presenter) {
        if (!patchApplied || !isEnabled() || actions == null) return actions;

        try {
            if (!hasMedia(postFor(presenter))) return actions;
            if (containsDownloadAction(actions)) return actions;

            Object downloadAction = createDownloadAction();
            registerDownloadAction(downloadAction);

            List<Object> result = new ArrayList<>(actions.size() + 1);
            result.addAll(actions);
            result.add(downloadAction);
            return result;
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to add the NewX inline download action", exception);
            return actions;
        }
    }

    /** Stages the rendered entry's identity for the icon lambda; consumed by
     *  {@link #selectIcon} and unconditionally cleared by {@link #finishRender}. */
    public static float markIconSize(Object action, float iconSize) {
        RENDERING_DOWNLOAD_ACTION.set(isDownloadAction(action));
        return iconSize;
    }

    public static Object selectIcon(Object nativeIcon, float markedIconSize, Object downloadIcon) {
        boolean useDownloadIcon = Boolean.TRUE.equals(RENDERING_DOWNLOAD_ACTION.get());
        RENDERING_DOWNLOAD_ACTION.remove();
        return useDownloadIcon ? downloadIcon : nativeIcon;
    }

    /** Unconditional marker cleanup; injected at the entry renderer's exit so an icon
     *  lambda that exits before {@link #selectIcon} cannot leave stale state. */
    public static void finishRender() {
        RENDERING_DOWNLOAD_ACTION.remove();
    }

    static boolean renderMarkerPending() {
        return RENDERING_DOWNLOAD_ACTION.get() != null;
    }

    public static boolean handleEvent(Object presenter, Object event) {
        if (!patchApplied) return false;

        Object action = findActionEntry(event);
        if (!isDownloadAction(action)) return false;

        Context context = null;
        try {
            context = NewXUtils.findUsableActivity(null);
            Object post = getPresenterPost(presenter);
            if (context == null || post == null) {
                Utils.showToastShort("Could not find the selected post");
                return true;
            }

            List<DownloadItem> downloads = downloadItems(mediaFor(post));
            if (downloads.isEmpty()) {
                Utils.showToastShort("No downloadable media found");
                return true;
            }

            String username = sourceUsername(post);
            String postId = sourcePostId(post);
            if (downloads.size() == 1) {
                enqueueSingleDownload(context, downloads.get(0), username, postId, 0, 1);
            } else {
                showMediaPicker(context, downloads, username, postId);
            }
            return true;
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to process inline download action", exception);
            Utils.showToastShort("Could not download post media");
            return true;
        }
    }

    private static boolean isEnabled() {
        return SettingsRegistry.getBooleanOrDefault(SETTING_ID, false);
    }

    static boolean hasMedia(Object post) {
        if (post == null) return false;

        try {
            return !mediaFor(post).isEmpty();
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to check NewX post media", exception);
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
        synchronized (DOWNLOAD_ACTIONS) {
            removeClearedDownloadActions();
            if (DOWNLOAD_ACTIONS.size() >= MAX_TRACKED_OBJECTS) DOWNLOAD_ACTIONS.clear();
            DOWNLOAD_ACTIONS.add(new WeakReference<>(action));
        }
    }

    private static boolean isDownloadAction(Object candidate) {
        if (candidate == null) return false;

        synchronized (DOWNLOAD_ACTIONS) {
            Iterator<WeakReference<Object>> iterator = DOWNLOAD_ACTIONS.iterator();
            while (iterator.hasNext()) {
                Object action = iterator.next().get();
                if (action == null) {
                    iterator.remove();
                    continue;
                }
                if (action == candidate) return true;
            }
        }
        return false;
    }

    private static void removeClearedDownloadActions() {
        DOWNLOAD_ACTIONS.removeIf(reference -> reference.get() == null);
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
            Logger.printException(() -> "Failed to read the NewX inline action event", exception);
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
        return !downloadItems(media).isEmpty();
    }

    private static final Pattern STATUS_URL_PATTERN =
            Pattern.compile("https?://(?:[a-zA-Z0-9-]+\\.)*(?:twitter|x|fxtwitter|vxtwitter|fixupx|twx)\\.com/([A-Za-z0-9_]+)/status/\\d+");

    static String sourcePostId(Object post) {
        String postText = postText(post);
        String originalPostText = originalRepostedPostText(postText);
        if (originalPostText != null) {
            String originalPostId = ToStringParser.fieldValue(originalPostText, "id");
            if (originalPostId != null) return safeFileSegment(originalPostId, "post");
        }

        if (hasRepostedMedia(postText)) {
            String sourcePostId = sourceMediaField(postText, "sourcePostIdentifier");
            if (sourcePostId != null) return safeFileSegment(sourcePostId, "post");
        }

        String canonicalText = canonicalPostText(post);
        String postId = ToStringParser.fieldValue(canonicalText, "id");
        if (postId != null) return safeFileSegment(postId, "post");

        String rawPostId = ToStringParser.fieldValue(postText, "id");
        if (rawPostId != null) return safeFileSegment(rawPostId, "post");

        return safeFileSegment(null, "post");
    }

    static String sourceUsername(Object post) {
        String postText = postText(post);
        String originalPostText = originalRepostedPostText(postText);
        if (originalPostText != null) {
            String originalAuthor = ToStringParser.fieldValue(originalPostText, "author");
            String originalScreenName = originalAuthor == null
                    ? null
                    : ToStringParser.fieldValue(originalAuthor, "screenName");
            if (originalScreenName != null) return safeFileSegment(originalScreenName, "twitter");
        }

        // Folded RT posts or posts with credited/reposted media carry sourceInfo
        // on their media, and the original author's screen name in the first RT
        // mention, in expandedUrl, or on the post author.
        if (hasRepostedMedia(postText)) {
            String sourceScreenName = firstMentionScreenName(postText);
            if (sourceScreenName != null) return safeFileSegment(sourceScreenName, "twitter");

            String expandedUrlScreenName = mediaExpandedUrlScreenName(postText);
            if (expandedUrlScreenName != null) return safeFileSegment(expandedUrlScreenName, "twitter");
        }

        String canonicalText = canonicalPostText(post);
        String author = ToStringParser.fieldValue(canonicalText, "author");
        String screenName = author == null ? null : ToStringParser.fieldValue(author, "screenName");
        if (screenName != null) return safeFileSegment(screenName, "twitter");

        String rawAuthor = ToStringParser.fieldValue(postText, "author");
        String rawScreenName = rawAuthor == null ? null : ToStringParser.fieldValue(rawAuthor, "screenName");
        if (rawScreenName != null) return safeFileSegment(rawScreenName, "twitter");

        return safeFileSegment(null, "twitter");
    }

    private static String postText(Object post) {
        return post == null ? null : post.toString();
    }

    private static String canonicalPostText(Object post) {
        try {
            Object canonicalPost = canonicalPost(post);
            if (canonicalPost != null) return canonicalPost.toString();
        } catch (RuntimeException ignored) {
        }
        String postText = postText(post);
        if (postText == null) return null;
        String canonicalField = ToStringParser.fieldValue(postText, "canonicalPost");
        return canonicalField != null ? canonicalField : postText;
    }

    private static String originalRepostedPostText(String postText) {
        if (postText == null) return null;
        String repostedPost = ToStringParser.fieldValue(postText, "rePostedPost");
        if (repostedPost == null) return null;
        String canonical = ToStringParser.fieldValue(repostedPost, "canonicalPost");
        return canonical != null ? canonical : repostedPost;
    }

    private static boolean hasRepostedMedia(String text) {
        return sourceMediaField(text, "sourcePostIdentifier") != null;
    }

    private static String firstMentionScreenName(String text) {
        String entityList = ToStringParser.fieldValue(text, "entityList");
        if (entityList == null) return null;
        String mentions = ToStringParser.fieldValue(entityList, "mentions");
        if (mentions == null) return null;
        // Mentions are ordered by appearance; the first one is the "RT @name:"
        // source of a repost.
        return ToStringParser.fieldValue(mentions, "screenName");
    }

    private static String mediaExpandedUrlScreenName(String text) {
        if (text == null) return null;
        String entityList = ToStringParser.fieldValue(text, "entityList");
        String searchScope = entityList != null ? entityList : text;
        String expandedUrl = ToStringParser.fieldValue(searchScope, "expandedUrl");
        String screenName = screenNameFromUrl(expandedUrl);
        if (screenName != null) return screenName;
        return screenNameFromUrl(searchScope);
    }

    private static String screenNameFromUrl(String url) {
        if (url == null) return null;
        Matcher matcher = STATUS_URL_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String sourceMediaField(String text, String fieldName) {
        if (text == null) return null;
        String sourceInfo = ToStringParser.fieldValue(text, "sourceInfo");
        if (sourceInfo == null) return null;
        return ToStringParser.fieldValue(sourceInfo, fieldName);
    }


    private static List<DownloadItem> downloadItems(List<?> media) {
        List<DownloadItem> downloads = new ArrayList<>(media.size());
        for (Object item : media) {
            if (item == null) continue;

            try {
                DownloadItem download = downloadItem(item);
                if (download != null) downloads.add(download);
            } catch (RuntimeException exception) {
                Logger.printException(() -> "Failed to read NewX media", exception);
            }
        }
        return downloads;
    }

    private static DownloadItem downloadItem(Object media) {
        String value = media.toString();
        if (value.startsWith("MediaContentImage(")) {
            String url = ToStringParser.fieldValue(value, "imageUrl");
            if (!NewXUtils.isHttpUrl(url)) return null;
            return new DownloadItem(originalImageUrl(url), "jpg", "image/jpeg", "Image");
        }

        Variant bestVariant = bestMp4Variant(value);
        if (bestVariant == null) return null;

        String label = value.startsWith("MediaContentGif(") ? "GIF" : "Video";
        return new DownloadItem(bestVariant.url, "mp4", "video/mp4", label);
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
                    (contentType.equalsIgnoreCase("video/mp4") || url.toLowerCase().contains(".mp4"))) {
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
            String postId
    ) {
        MediaPickerDialog.show(
                context,
                downloads,
                username,
                postId,
                new MediaPickerDialog.OnMediaSelectedListener() {
                    @Override
                    public void onDownloadItem(int index) {
                        if (index >= 0 && index < downloads.size()) {
                            enqueueSingleDownload(context, downloads.get(index), username, postId, index, downloads.size());
                        }
                    }

                    @Override
                    public void onDownloadAll() {
                        enqueueAllDownloads(context, downloads, username, postId);
                    }
                }
        );
    }

    private static void enqueueAllDownloads(
            Context context,
            List<DownloadItem> downloads,
            String username,
            String postId
    ) {
        int queued = 0;
        int skipped = 0;
        int failed = 0;
        ConflictBehavior behavior = conflictBehavior();
        for (int index = 0; index < downloads.size(); index++) {
            switch (enqueueDownload(
                    context,
                    downloads.get(index),
                    username,
                    postId,
                    index,
                    downloads.size(),
                    behavior
            )) {
                case QUEUED -> queued++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }
        showQueueResult(context, queued, skipped, failed);
    }

    private static void enqueueSingleDownload(
            Context context,
            DownloadItem download,
            String username,
            String postId,
            int index,
            int mediaCount
    ) {
        EnqueueState state =
                enqueueDownload(
                        context,
                        download,
                        username,
                        postId,
                        index,
                        mediaCount,
                        conflictBehavior()
                );
        switch (state) {
            case QUEUED -> Utils.showToastShort("Download started");
            case SKIPPED -> Utils.showToastShort("Already downloaded or queued");
            case FAILED -> Utils.showToastShort("Could not start download");
        }
    }

    private static synchronized EnqueueState enqueueDownload(
            Context context,
            DownloadItem download,
            String username,
            String postId,
            int index,
            int mediaCount,
            ConflictBehavior behavior
    ) {
        if (!NewXUtils.isHttpUrl(download.url)) return EnqueueState.FAILED;

        DownloadManager manager = downloadManager(context);
        if (manager == null) return EnqueueState.FAILED;

        String baseFileName = downloadFileName(username, postId, download.extension, index, mediaCount);
        String fileName;
        try {
            fileName = resolveTargetFileName(context, baseFileName, behavior, download.mimeType);
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to resolve NewX download target", exception);
            return EnqueueState.FAILED;
        }
        if (fileName == null) return EnqueueState.SKIPPED;

        return queueDownload(
                context,
                manager,
                download.url,
                fileName,
                download.mimeType,
                "Downloading media from @" + username
        );
    }

    private static synchronized void enqueueFallbackDownload(
            Context context,
            DownloadManager manager,
            String fallbackUrl,
            String fileName,
            String mimeType
    ) {
        if (queueDownload(context, manager, fallbackUrl, fileName, mimeType, "Downloading media") ==
                EnqueueState.FAILED) {
            Utils.showToastShort("Download failed: " + fileName);
        }
    }

    private static EnqueueState queueDownload(
            Context context,
            DownloadManager manager,
            String url,
            String fileName,
            String mimeType,
            String description
    ) {
        String temporaryFileName = uniqueTemporaryDownloadFileName(fileName);
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    .setTitle(fileName)
                    .setDescription(description)
                    .setMimeType(mimeType)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalPublicDir(
                            primaryDirectoryForMime(mimeType),
                            DOWNLOAD_DIRECTORY + "/" + temporaryFileName
                    );
            // setDestinationInExternalPublicDir accepts any public-directory name; the literal
            // "Pictures"/"Movies" values equal Environment.DIRECTORY_* and route the staged file
            // to the matching volume root so the later MediaStore publish lands in the same place.

            long downloadId = manager.enqueue(request);
            try {
                savePendingDownload(context, downloadId, temporaryFileName, fileName, mimeType, url);
            } catch (RuntimeException exception) {
                manager.remove(downloadId);
                throw exception;
            }
            finishPendingDownloadAsync(context, manager, downloadId);
            return EnqueueState.QUEUED;
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to enqueue NewX media download", exception);
            return EnqueueState.FAILED;
        }
    }

    private static DownloadManager downloadManager(Context context) {
        Object service = context.getSystemService(Context.DOWNLOAD_SERVICE);
        return service instanceof DownloadManager manager ? manager : null;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private static void registerDownloadReceiver(Context context) {
        if (downloadReceiverRegistered) return;

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId < 0) return;

                DownloadManager manager = downloadManager(receiverContext);
                if (manager == null) return;
                finishPendingDownloadAsync(receiverContext, manager, downloadId);
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
        downloadReceiverRegistered = true;
    }

    private static void resumePendingDownloads(Context context) {
        DownloadManager manager = downloadManager(context);
        if (manager == null) return;

        Map<String, ?> pending = pendingDownloads(context).getAll();
        for (String key : pending.keySet()) {
            try {
                finishPendingDownloadAsync(context, manager, Long.parseLong(key));
            } catch (NumberFormatException exception) {
                pendingDownloads(context).edit().remove(key).apply();
            }
        }
    }

    private static void savePendingDownload(
            Context context,
            long downloadId,
            String temporaryFileName,
            String fileName,
            String mimeType,
            String url
    ) {
        String value = temporaryFileName + "\n" + fileName + "\n" + mimeType + "\n" + (url != null ? url : "");
        boolean saved = pendingDownloads(context)
                .edit()
                .putString(String.valueOf(downloadId), value)
                .commit();
        if (!saved) throw new IllegalStateException("Could not persist pending download");
    }

    private static PendingDownload pendingDownload(Context context, long downloadId) {
        String value = pendingDownloads(context).getString(String.valueOf(downloadId), null);
        if (value == null) return null;

        String[] fields = value.split("\n", -1);
        if (fields.length < 3) {
            clearPendingDownload(context, downloadId);
            return null;
        }
        String url = fields.length >= 4 ? fields[3] : "";
        return new PendingDownload(fields[0], fields[1], fields[2], url);
    }

    private static SharedPreferences pendingDownloads(Context context) {
        return context.getSharedPreferences(PENDING_DOWNLOADS_PREFS, Context.MODE_PRIVATE);
    }

    private static void clearPendingDownload(Context context, long downloadId) {
        pendingDownloads(context).edit().remove(String.valueOf(downloadId)).apply();
    }

    private static void finishPendingDownloadAsync(
            Context context,
            DownloadManager manager,
            long downloadId
    ) {
        Context applicationContext = context.getApplicationContext();
        Context safeContext = applicationContext != null ? applicationContext : context;
        DOWNLOAD_EXECUTOR.execute(() -> finishPendingDownload(safeContext, manager, downloadId));
    }

    private static void finishPendingDownload(
            Context context,
            DownloadManager manager,
            long downloadId
    ) {
        PendingDownload pending = pendingDownload(context, downloadId);
        if (pending == null) return;

        int status = downloadStatus(manager, downloadId);
        if (status == DownloadManager.STATUS_PENDING ||
                status == DownloadManager.STATUS_RUNNING ||
                status == DownloadManager.STATUS_PAUSED) {
            return;
        }
        if (status != DownloadManager.STATUS_SUCCESSFUL) {
            handleFailedDownload(context, manager, downloadId, pending);
            return;
        }

        try {
            boolean moved = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? publishDownload(context, manager, downloadId, pending.fileName, pending.mimeType)
                    : moveLegacyDownload(context, pending.temporaryFileName, pending.fileName, pending.mimeType);
            if (!moved) {
                Utils.showToastShort("Could not finalize download: " + pending.fileName);
                return;
            }

            removePendingDownload(context, manager, downloadId);
            Utils.showToastShort("Downloaded: " + pending.fileName);
        } catch (IOException | RuntimeException exception) {
            Logger.printException(() -> "Failed to finalize NewX media download", exception);
            Utils.showToastShort("Could not finalize download: " + pending.fileName);
        }
    }

    private static synchronized void handleFailedDownload(
            Context context,
            DownloadManager manager,
            long downloadId,
            PendingDownload pending
    ) {
        removePendingDownload(context, manager, downloadId);
        if (pending.url != null && pending.url.contains("name=orig")) {
            String fallbackUrl = pending.url.replace("name=orig", "name=4096x4096");
            enqueueFallbackDownload(context, manager, fallbackUrl, pending.fileName, pending.mimeType);
            return;
        }
        Utils.showToastShort("Download failed: " + pending.fileName);
    }

    private static synchronized void removePendingDownload(
            Context context,
            DownloadManager manager,
            long downloadId
    ) {
        clearPendingDownload(context, downloadId);
        manager.remove(downloadId);
    }

    private static int downloadStatus(DownloadManager manager, long downloadId) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = manager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) return -1;
            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            return statusIndex < 0 ? -1 : cursor.getInt(statusIndex);
        }
    }

    private static boolean publishDownload(
            Context context,
            DownloadManager manager,
            long downloadId,
            String fileName,
            String mimeType
    ) throws IOException {
        Uri source = manager.getUriForDownloadedFile(downloadId);
        if (source == null) return false;

        ContentResolver resolver = context.getContentResolver();
        Uri collection = mimeType.startsWith("video/")
                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String relativePath = relativeDownloadPath(mimeType);

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri destination = resolver.insert(collection, values);
        if (destination == null) return false;

        try (InputStream input = resolver.openInputStream(source);
             OutputStream output = resolver.openOutputStream(destination, "w")) {
            if (input == null || output == null) throw new IOException("Could not open media streams");
            copy(input, output);
        } catch (IOException | RuntimeException exception) {
            resolver.delete(destination, null, null);
            throw exception;
        }

        ContentValues completed = new ContentValues();
        completed.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(destination, completed, null, null);

        // Replace any pre-existing copy only after the new file is fully written, so a
        // failed download never destroys the previously saved media.
        deleteExistingMedia(resolver, collection, fileName, relativePath, destination);
        return true;
    }

    private static void deleteExistingMedia(
            ContentResolver resolver,
            Uri collection,
            String fileName,
            String relativePath,
            Uri destination
    ) {
        String destinationId = destination.getLastPathSegment();
        if (destinationId == null) return;

        try {
            resolver.delete(
                    collection,
                    existingMediaSelection(),
                    existingMediaSelectionArgs(fileName, relativePath, destinationId)
            );
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to replace existing NewX media", exception);
        }
    }

    static String existingMediaSelection() {
        return MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " +
                MediaStore.MediaColumns._ID + "!=?";
    }

    static String[] existingMediaSelectionArgs(
            String fileName,
            String relativePath,
            String destinationId
    ) {
        return new String[]{fileName, relativePath, destinationId};
    }

    private static boolean moveLegacyDownload(
            Context context,
            String temporaryFileName,
            String fileName,
            String mimeType
    ) throws IOException {
        File primary = Environment.getExternalStoragePublicDirectory(primaryDirectoryForMime(mimeType));
        File directory = new File(primary, DOWNLOAD_DIRECTORY);
        if (!directory.isDirectory()) return false;
        File temporaryFile = new File(directory, temporaryFileName);
        if (!temporaryFile.isFile()) return false;

        File finalFile = new File(directory, fileName);
        Files.move(
                temporaryFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );
        context.sendBroadcast(
                new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(finalFile))
        );
        return true;
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    static String temporaryDownloadFileName(String fileName) {
        return temporaryDownloadFileName(fileName, "_tmp");
    }

    static String uniqueTemporaryDownloadFileName(String fileName) {
        return temporaryDownloadFileName(
                fileName,
                "_tmp_" + UUID.randomUUID().toString().replace("-", "")
        );
    }

    private static String temporaryDownloadFileName(String fileName, String suffix) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex <= 0) return fileName + suffix;
        return fileName.substring(0, extensionIndex) + suffix + fileName.substring(extensionIndex);
    }

    static String downloadFileName(
            String username,
            String postId,
            String extension,
            int index,
            int mediaCount
    ) {
        String baseName = safeFileSegment(username, "twitter") + "_" +
                safeFileSegment(postId, "post");
        String suffix = mediaCount > 1 ? "_" + (index + 1) : "";
        return baseName + suffix + "." + safeFileSegment(extension, "bin");
    }

    private static String safeFileSegment(String value, String fallback) {
        if (value == null) return fallback;

        String sanitized = value.trim().replaceFirst("^@", "")
                .replaceAll("[^A-Za-z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._-]+|[._-]+$", "");
        return sanitized.isEmpty() ? fallback : sanitized;
    }

    private static void showQueueResult(Context context, int queued, int skipped, int failed) {
        if (failed == 0 && skipped == 0) {
            String message = queued == 1 ? "Download started" : queued + " downloads started";
            Utils.showToastShort(message);
            return;
        }
        if (queued == 0) {
            if (failed == 0 && skipped > 0) {
                Utils.showToastShort(skipped == 1
                        ? "Already downloaded or queued"
                        : skipped + " media already downloaded or queued");
                return;
            }
            Utils.showToastShort("Could not start download");
            return;
        }
        List<String> parts = new ArrayList<>();
        parts.add(queued == 1 ? "1 download started" : queued + " downloads started");
        if (skipped > 0) parts.add(skipped == 1
                ? "1 already downloaded or queued"
                : skipped + " already downloaded or queued");
        if (failed > 0) parts.add(failed == 1 ? "1 failed" : failed + " failed");
        Utils.showToastShort(String.join(", ", parts));
    }

    private static ConflictBehavior conflictBehavior() {
        String value = SettingsRegistry.getStringOrDefault(
                CONFLICT_SETTING,
                DEFAULT_CONFLICT_BEHAVIOR.name()
        );
        for (ConflictBehavior behavior : ConflictBehavior.values()) {
            if (behavior.name().equalsIgnoreCase(value)) return behavior;
        }
        return DEFAULT_CONFLICT_BEHAVIOR;
    }

    @androidx.annotation.Nullable
    private static String resolveTargetFileName(
            Context context,
            String baseFileName,
            ConflictBehavior behavior,
            String mimeType
    ) {
        return resolveTargetFileName(
                baseFileName,
                behavior,
                fileName -> mediaExists(context, fileName, mimeType) || pendingFileExists(context, fileName)
        );
    }

    static String resolveTargetFileName(
            String baseFileName,
            ConflictBehavior behavior,
            Predicate<String> isOccupied
    ) {
        if (behavior == null || isOccupied == null) {
            throw new IllegalArgumentException("Download target resolver is incomplete");
        }

        return switch (behavior) {
            case OVERWRITE -> baseFileName;
            case SKIP -> isOccupied.test(baseFileName) ? null : baseFileName;
            case RENAME -> isOccupied.test(baseFileName)
                    ? uniqueFileName(baseFileName, isOccupied)
                    : baseFileName;
        };
    }

    private static String uniqueFileName(String baseFileName, Predicate<String> isOccupied) {
        int dot = baseFileName.lastIndexOf('.');
        String stem = dot > 0 ? baseFileName.substring(0, dot) : baseFileName;
        String extension = dot > 0 ? baseFileName.substring(dot) : "";
        int counter = 1;
        while (true) {
            String candidate = stem + "_" + counter + extension;
            if (!isOccupied.test(candidate)) return candidate;
            counter++;
        }
    }

    private static boolean pendingFileExists(Context context, String fileName) {
        for (Object value : pendingDownloads(context).getAll().values()) {
            if (!(value instanceof String serialized)) continue;

            String[] fields = serialized.split("\n", -1);
            if (fields.length >= 3 && fileName.equals(fields[1])) return true;
        }
        return false;
    }

    private static boolean mediaExists(Context context, String fileName, String mimeType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                    MediaStore.MediaColumns.RELATIVE_PATH + "=?";
            // Each MediaStore collection permits only specific primary directories: the Images
            // collection accepts Pictures, the Video collection accepts Movies. Query each with
            // its own relative path so videos are found under Movies/Twitter and images under
            // Pictures/Twitter.
            Uri[] collections = {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            };
            String[] relativePaths = {
                    PICTURES_DIRECTORY + "/" + DOWNLOAD_DIRECTORY + "/",
                    MOVIES_DIRECTORY + "/" + DOWNLOAD_DIRECTORY + "/",
            };
            for (int index = 0; index < collections.length; index++) {
                String[] selectionArgs = new String[]{fileName, relativePaths[index]};
                try (Cursor cursor = resolver.query(
                        collections[index],
                        new String[]{MediaStore.MediaColumns._ID},
                        selection,
                        selectionArgs,
                        null
                )) {
                    if (cursor != null && cursor.moveToFirst()) return true;
                } catch (RuntimeException exception) {
                    Logger.printException(() -> "Failed to query NewX media existence", exception);
                }
            }
            return false;
        }

        File primary = Environment.getExternalStoragePublicDirectory(primaryDirectoryForMime(mimeType));
        File directory = new File(primary, DOWNLOAD_DIRECTORY);
        return new File(directory, fileName).isFile();
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

        DownloadItem(String url, String extension, String mimeType, String label) {
            this.url = url;
            this.extension = extension;
            this.mimeType = mimeType;
            this.label = label;
        }
    }

    private static final class PendingDownload {
        final String temporaryFileName;
        final String fileName;
        final String mimeType;
        final String url;

        PendingDownload(String temporaryFileName, String fileName, String mimeType, String url) {
            this.temporaryFileName = temporaryFileName;
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.url = url;
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
