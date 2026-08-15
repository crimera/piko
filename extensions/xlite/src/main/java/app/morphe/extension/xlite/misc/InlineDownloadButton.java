package app.morphe.extension.xlite.misc;

import android.annotation.SuppressLint;
import android.app.Activity;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.xlite.ui.Theme;
import app.morphe.extension.xlite.utils.XLiteUtils;
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

import app.morphe.extension.shared.Logger;
import app.morphe.extension.xlite.settings.SettingsRegistry;
import app.morphe.extension.xlite.utils.ToStringParser;

@SuppressWarnings("unused")
public final class InlineDownloadButton {
    private static final String SETTING_ID = "xlite.content.inline_download_button";
    private static final String DOWNLOAD_DIRECTORY = "Twitter";
    private static final String PENDING_DOWNLOADS_PREFS = "piko_xlite_inline_downloads";
    private static final String CONFLICT_SETTING = "xlite.content.inline_download_conflict";
    private static final ConflictBehavior DEFAULT_CONFLICT_BEHAVIOR = ConflictBehavior.SKIP;
    private static final int MAX_TRACKED_OBJECTS = 128;
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final List<WeakReference<Object>> DOWNLOAD_ACTIONS = new ArrayList<>();
    private static boolean initialized;
    private static boolean downloadReceiverRegistered;
    private static final ThreadLocal<Boolean> RENDERING_DOWNLOAD_ACTION = new ThreadLocal<>();

    private InlineDownloadButton() {
    }

    public static synchronized void initialize(Context context) {
        if (initialized || context == null) return;

        Context applicationContext = context.getApplicationContext();
        if (!(applicationContext instanceof Application application)) return;

        XLiteUtils.initialize(application);
        registerDownloadReceiver(application);
        resumePendingDownloads(application);
        initialized = true;
    }

    public static List<?> addAction(List<?> actions, Object presenter) {
        if (!isEnabled() || actions == null || !hasMedia(postFor(presenter))) return actions;
        if (containsDownloadAction(actions)) return actions;

        try {
            Object downloadAction = createDownloadAction();
            registerDownloadAction(downloadAction);

            List<Object> result = new ArrayList<>(actions.size() + 1);
            result.addAll(actions);
            result.add(downloadAction);
            return result;
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to add the X-Lite inline download action", exception);
            return actions;
        }
    }

    public static float markIconSize(Object action, float iconSize) {
        RENDERING_DOWNLOAD_ACTION.set(isDownloadAction(action));
        return iconSize;
    }

    public static Object selectIcon(Object nativeIcon, float markedIconSize, Object downloadIcon) {
        boolean useDownloadIcon = Boolean.TRUE.equals(RENDERING_DOWNLOAD_ACTION.get());
        RENDERING_DOWNLOAD_ACTION.remove();
        return useDownloadIcon ? downloadIcon : nativeIcon;
    }

    public static float normalizeIconSize(float markedIconSize) {
        return Math.abs(markedIconSize);
    }

    public static boolean handleEvent(Object presenter, Object event) {
        Object action = findActionEntry(event);
        if (!isDownloadAction(action)) return false;

        Context context = null;
        try {
            XLiteUtils.PresenterData presenterData =
                    XLiteUtils.findPresenterData(presenter, presenterPostClassName());
            context = presenterData.getContext();
            Object post = presenterData.getValue();
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
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Logger.printException(() -> "Failed to process inline download action", exception);
            Utils.showToastShort(exception instanceof PostIdentityException identityError
                    ? identityError.getMessage()
                    : "Could not download post media");
            return true;
        }
    }

    private static boolean isEnabled() {
        try {
            return SettingsRegistry.getBoolean(SETTING_ID);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static boolean hasMedia(Object post) {
        if (post == null) return false;

        try {
            List<?> media = mediaFor(post);
            if (!media.isEmpty()) return true;
            Object directMedia = XLiteUtils.invokeIfPresent(post, "getMedia");
            return directMedia instanceof List<?> list && !list.isEmpty();
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to check X-Lite post media", exception);
            return false;
        }
    }

    private static Object createDownloadAction() {
        return null;
    }

    private static boolean containsDownloadAction(List<?> actions) {
        for (Object action : actions) {
            if (isDownloadAction(action)) return true;
        }
        return false;
    }

    private static void registerDownloadAction(Object action) {
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
        } catch (IllegalAccessException exception) {
            Logger.printException(() -> "Failed to read the X-Lite inline action event", exception);
        }
        return null;
    }

    private static String presenterPostClassName() {
        return getPresenterPostClassName();
    }

    private static String getPresenterPostClassName() {
        return "";
    }

    private static Object canonicalPost(Object post) {
        return getCanonicalPost(post);
    }

    private static Object getCanonicalPost(Object post) {
        return null;
    }

    private static Object getPostMedia(Object canonicalPost) {
        return null;
    }

    private static Object postFor(Object presenter) {
        try {
            return XLiteUtils.findPresenterData(presenter, presenterPostClassName()).getValue();
        } catch (IllegalAccessException | RuntimeException exception) {
            Logger.printException(() -> "Failed to find the X-Lite inline action post", exception);
            return null;
        }
    }

    private static List<?> mediaFor(Object post) {
        Object canonicalPost = canonicalPost(post);
        if (canonicalPost == null) return java.util.Collections.emptyList();
        Object media = getPostMedia(canonicalPost);
        return media instanceof List<?> list ? list : java.util.Collections.emptyList();
    }

    private static String sourcePostId(Object post) {
        String postText = postText(post);
        String originalPostText = originalRepostedPostText(postText);
        if (originalPostText != null) {
            String originalPostId = ToStringParser.fieldValue(originalPostText, "id");
            if (originalPostId != null) return safeFileSegment(originalPostId, "post");
            throw unresolvedIdentity("Reposted post is missing its original post id", postText);
        }

        if (hasRepostedMedia(postText)) {
            String sourcePostId = sourceMediaField(postText, "sourcePostIdentifier");
            if (sourcePostId != null) return safeFileSegment(sourcePostId, "post");
            throw unresolvedIdentity("Reposted media is missing its source post id", postText);
        }

        String canonicalText = canonicalPostText(post);
        String postId = ToStringParser.fieldValue(canonicalText, "id");
        if (postId != null) return safeFileSegment(postId, "post");
        throw unresolvedIdentity("Could not resolve the post id", postText);
    }

    private static String sourceUsername(Object post) {
        String postText = postText(post);
        String originalPostText = originalRepostedPostText(postText);
        if (originalPostText != null) {
            String originalAuthor = ToStringParser.fieldValue(originalPostText, "author");
            String originalScreenName = originalAuthor == null
                    ? null
                    : ToStringParser.fieldValue(originalAuthor, "screenName");
            if (originalScreenName != null) return safeFileSegment(originalScreenName, "twitter");
            throw unresolvedIdentity("Reposted post has no original author's screen name", postText);
        }

        // Folded RT posts carry sourceInfo on their mirrored media and the
        // original author's screen name as the first RT mention.
        if (hasRepostedMedia(postText)) {
            String sourceScreenName = firstMentionScreenName(postText);
            if (sourceScreenName != null) return safeFileSegment(sourceScreenName, "twitter");
            throw unresolvedIdentity("Reposted media has no source screen name in its mentions", postText);
        }

        String canonicalText = canonicalPostText(post);
        String author = ToStringParser.fieldValue(canonicalText, "author");
        String screenName = author == null ? null : ToStringParser.fieldValue(author, "screenName");
        if (screenName != null) return safeFileSegment(screenName, "twitter");
        throw unresolvedIdentity("Could not resolve the post's screen name", postText);
    }

    private static String postText(Object post) {
        return post == null ? null : post.toString();
    }

    private static String canonicalPostText(Object post) {
        Object canonicalPost = canonicalPost(post);
        return canonicalPost == null ? null : canonicalPost.toString();
    }

    private static String originalRepostedPostText(String postText) {
        String repostedPost = ToStringParser.fieldValue(postText, "rePostedPost");
        if (repostedPost == null) return null;
        return ToStringParser.fieldValue(repostedPost, "canonicalPost");
    }

    private static PostIdentityException unresolvedIdentity(String message, String postText) {
        Logger.printInfo(() -> "Post identity unresolved (" + message + "): " + postText);
        return new PostIdentityException(message);
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
                Logger.printException(() -> "Failed to read X-Lite media", exception);
            }
        }
        return downloads;
    }

    private static DownloadItem downloadItem(Object media) {
        String value = media.toString();
        if (value.startsWith("MediaContentImage(")) {
            String url = ToStringParser.fieldValue(value, "imageUrl");
            if (!XLiteUtils.isHttpUrl(url)) return null;
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
            if (XLiteUtils.isHttpUrl(url) &&
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
        for (int index = 0; index < downloads.size(); index++) {
            switch (enqueueDownload(context, downloads.get(index), username, postId, index, downloads.size())) {
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
                enqueueDownload(context, download, username, postId, index, mediaCount);
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
            int mediaCount
    ) {
        if (!XLiteUtils.isHttpUrl(download.url)) return EnqueueState.FAILED;

        DownloadManager manager = downloadManager(context);
        if (manager == null) return EnqueueState.FAILED;

        String baseFileName = downloadFileName(username, postId, download.extension, index, mediaCount);
        String fileName;
        try {
            fileName = resolveTargetFileName(context, baseFileName, conflictBehavior());
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to resolve X-Lite download target", exception);
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
                            Environment.DIRECTORY_PICTURES,
                            DOWNLOAD_DIRECTORY + "/" + temporaryFileName
                    );

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
            Logger.printException(() -> "Failed to enqueue X-Lite media download", exception);
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
                    : moveLegacyDownload(context, pending.temporaryFileName, pending.fileName);
            if (!moved) {
                Utils.showToastShort("Could not finalize download: " + pending.fileName);
                return;
            }

            removePendingDownload(context, manager, downloadId);
            Utils.showToastShort("Downloaded: " + pending.fileName);
        } catch (IOException | RuntimeException exception) {
            Logger.printException(() -> "Failed to finalize X-Lite media download", exception);
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
        String relativePath = Environment.DIRECTORY_PICTURES + "/" + DOWNLOAD_DIRECTORY + "/";

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
            Logger.printException(() -> "Failed to replace existing X-Lite media", exception);
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
            String fileName
    ) throws IOException {
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File directory = new File(pictures, DOWNLOAD_DIRECTORY);
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
        try {
            String value = SettingsRegistry.getString(CONFLICT_SETTING);
            if (value != null) {
                for (ConflictBehavior behavior : ConflictBehavior.values()) {
                    if (behavior.name().equalsIgnoreCase(value)) return behavior;
                }
            }
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to read X-Lite download conflict behavior", exception);
        }
        return DEFAULT_CONFLICT_BEHAVIOR;
    }

    @androidx.annotation.Nullable
    private static String resolveTargetFileName(
            Context context,
            String baseFileName,
            ConflictBehavior behavior
    ) {
        return resolveTargetFileName(
                baseFileName,
                behavior,
                fileName -> mediaExists(context, fileName) || pendingFileExists(context, fileName)
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

    private static boolean mediaExists(Context context, String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            String relativePath = Environment.DIRECTORY_PICTURES + "/" + DOWNLOAD_DIRECTORY + "/";
            String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                    MediaStore.MediaColumns.RELATIVE_PATH + "=?";
            String[] selectionArgs = new String[]{fileName, relativePath};
            Uri[] collections = {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            };
            for (Uri collection : collections) {
                try (Cursor cursor = resolver.query(
                        collection,
                        new String[]{MediaStore.MediaColumns._ID},
                        selection,
                        selectionArgs,
                        null
                )) {
                    if (cursor != null && cursor.moveToFirst()) return true;
                } catch (RuntimeException exception) {
                    Logger.printException(() -> "Failed to query X-Lite media existence", exception);
                }
            }
            return false;
        }

        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File directory = new File(pictures, DOWNLOAD_DIRECTORY);
        return new File(directory, fileName).isFile();
    }

    static Activity currentActivity() {
        return XLiteUtils.findUsableActivity(null);
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

    private static final class PostIdentityException extends RuntimeException {
        PostIdentityException(String message) {
            super(message);
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
