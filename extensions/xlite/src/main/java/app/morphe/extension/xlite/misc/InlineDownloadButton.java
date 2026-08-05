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
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;

import com.x.models.InlineActionEntry;
import com.x.models.PostActionType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.xlite.settings.SettingsRegistry;

@SuppressWarnings("unused")
public final class InlineDownloadButton {
    private static final String SETTING_ID = "xlite.content.inline_download_button";
    private static final String CARRIER_ACTION_NAME = "TwitterShare";
    private static final String CONTEXTUAL_POST_CLASS = "com.x.models.ContextualPost";
    private static final String DOWNLOAD_DIRECTORY = "Twitter";
    private static final String PENDING_DOWNLOADS_PREFS = "piko_xlite_inline_downloads";
    private static final String CONFLICT_SETTING = "xlite.content.inline_download_conflict";
    private static final ConflictBehavior DEFAULT_CONFLICT_BEHAVIOR = ConflictBehavior.SKIP;
    private static final int MAX_TRACKED_OBJECTS = 128;
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final List<WeakReference<InlineActionEntry>> DOWNLOAD_ACTIONS = new ArrayList<>();
    private static WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    private static boolean lifecycleCallbacksRegistered;
    private static boolean downloadReceiverRegistered;

    private InlineDownloadButton() {
    }

    public static synchronized void initialize(Context context) {
        if (lifecycleCallbacksRegistered || context == null) return;

        Context applicationContext = context.getApplicationContext();
        if (!(applicationContext instanceof Application application)) return;

        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle state) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                resumedActivity = new WeakReference<>(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {
                clearActivity(activity);
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle state) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                clearActivity(activity);
            }
        });
        lifecycleCallbacksRegistered = true;
        registerDownloadReceiver(application);
        resumePendingDownloads(application);
    }

    public static List<?> addAction(List<?> actions, Object presenter) {
        if (!isEnabled() || actions == null || !hasMedia(postFor(presenter))) return actions;
        if (containsDownloadAction(actions)) return actions;

        try {
            InlineActionEntry downloadAction = createDownloadAction();
            registerDownloadAction(downloadAction);

            List<Object> result = new ArrayList<>(actions.size() + 1);
            result.addAll(actions);
            result.add(downloadAction);
            return result;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Logger.printException(() -> "Failed to add the X-Lite inline download action", exception);
            return actions;
        }
    }

    public static float markIconSize(InlineActionEntry action, float iconSize) {
        if (!isDownloadAction(action)) return iconSize;
        return -Math.abs(iconSize);
    }

    public static Object selectIcon(Object nativeIcon, float markedIconSize, Object downloadIcon) {
        return isMarkedIconSize(markedIconSize) ? downloadIcon : nativeIcon;
    }

    public static String contentDescription(String nativeDescription, float markedIconSize) {
        return isMarkedIconSize(markedIconSize) ? "Download" : nativeDescription;
    }

    public static float normalizeIconSize(float markedIconSize) {
        return Math.abs(markedIconSize);
    }

    public static boolean handleEvent(Object presenter, Object event) {
        InlineActionEntry action = findActionEntry(event);
        if (!isDownloadAction(action)) return false;

        Context context = null;
        try {
            PresenterData presenterData = findPresenterData(presenter);
            context = presenterData.context;
            if (context == null || presenterData.post == null) {
                Utils.showToastShort("Could not find the selected post");
                return true;
            }

            List<DownloadItem> downloads = downloadItems(mediaFor(presenterData.post));
            if (downloads.isEmpty()) {
                Utils.showToastShort("No downloadable media found");
                return true;
            }

            String username = username(presenterData.post);
            String postId = postId(presenterData.post);
            if (downloads.size() == 1) {
                enqueueSingleDownload(context, downloads.get(0), username, postId, 0, 1);
            } else {
                showMediaPicker(context, downloads, username, postId);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Logger.printException(() -> "Failed to process inline download action", exception);
            Utils.showToastShort("Could not download post media");
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
            return !mediaFor(post).isEmpty();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Logger.printException(() -> "Failed to check X-Lite post media", exception);
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static InlineActionEntry createDownloadAction() throws ReflectiveOperationException {
        Class actionClass = PostActionType.class;
        Object carrierAction = Enum.valueOf(actionClass, CARRIER_ACTION_NAME);
        Constructor<InlineActionEntry> constructor = InlineActionEntry.class.getDeclaredConstructor(
                PostActionType.class,
                Long.class,
                boolean.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(carrierAction, null, true);
    }

    private static boolean containsDownloadAction(List<?> actions) {
        for (Object action : actions) {
            if (action instanceof InlineActionEntry entry && isDownloadAction(entry)) return true;
        }
        return false;
    }

    private static void registerDownloadAction(InlineActionEntry action) {
        synchronized (DOWNLOAD_ACTIONS) {
            removeClearedDownloadActions();
            if (DOWNLOAD_ACTIONS.size() >= MAX_TRACKED_OBJECTS) DOWNLOAD_ACTIONS.clear();
            DOWNLOAD_ACTIONS.add(new WeakReference<>(action));
        }
    }

    private static boolean isDownloadAction(InlineActionEntry candidate) {
        if (candidate == null) return false;

        synchronized (DOWNLOAD_ACTIONS) {
            Iterator<WeakReference<InlineActionEntry>> iterator = DOWNLOAD_ACTIONS.iterator();
            while (iterator.hasNext()) {
                InlineActionEntry action = iterator.next().get();
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

    private static boolean isMarkedIconSize(float iconSize) {
        return Float.floatToRawIntBits(iconSize) < 0;
    }

    private static InlineActionEntry findActionEntry(Object event) {
        if (event == null) return null;

        try {
            for (Field field : event.getClass().getDeclaredFields()) {
                if (field.getType() != InlineActionEntry.class) continue;
                field.setAccessible(true);
                return (InlineActionEntry) field.get(event);
            }
        } catch (IllegalAccessException exception) {
            Logger.printException(() -> "Failed to read the X-Lite inline action event", exception);
        }
        return null;
    }

    private static Object postFor(Object presenter) {
        try {
            return findPresenterData(presenter).post;
        } catch (IllegalAccessException | RuntimeException exception) {
            Logger.printException(() -> "Failed to find the X-Lite inline action post", exception);
            return null;
        }
    }

    private static PresenterData findPresenterData(Object presenter) throws IllegalAccessException {
        if (presenter == null) return new PresenterData(null, null);

        Context context = null;
        Object post = null;
        for (Field field : presenter.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (Context.class.isAssignableFrom(field.getType())) context = (Context) field.get(presenter);
            if (CONTEXTUAL_POST_CLASS.equals(field.getType().getName())) post = field.get(presenter);
        }
        return new PresenterData(context, post);
    }

    private static List<?> mediaFor(Object post) throws ReflectiveOperationException {
        Object media = XLiteUtils.invoke(post, "getMedia");
        return media instanceof List<?> list ? list : java.util.Collections.emptyList();
    }

    private static String postId(Object post) throws ReflectiveOperationException {
        String value = identifierValue(XLiteUtils.invoke(post, "getId"));
        return safeFileSegment(value, "post");
    }

    private static String username(Object post) throws ReflectiveOperationException {
        Object author = XLiteUtils.invoke(post, "getAuthor");
        Object value = author == null ? null : XLiteUtils.invoke(author, "getScreenName");
        return safeFileSegment(value == null ? null : String.valueOf(value), "twitter");
    }

    private static String identifierValue(Object identifier) {
        if (identifier == null) return null;

        try {
            Object value = XLiteUtils.invoke(identifier, "getValue");
            String id = value == null ? null : String.valueOf(value).trim();
            if (id != null && !id.isEmpty()) return id;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Object value = XLiteUtils.invoke(identifier, "getStr");
            String id = value == null ? null : String.valueOf(value).trim();
            return id == null || id.isEmpty() ? null : id;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static List<DownloadItem> downloadItems(List<?> media) {
        List<DownloadItem> downloads = new ArrayList<>(media.size());
        for (Object item : media) {
            if (item == null) continue;

            try {
                DownloadItem download = downloadItem(item);
                if (download != null) downloads.add(download);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                Logger.printException(() -> "Failed to read X-Lite media", exception);
            }
        }
        return downloads;
    }

    private static DownloadItem downloadItem(Object media) throws ReflectiveOperationException {
        Object imageUrl = XLiteUtils.invokeIfPresent(media, "getImageUrl");
        if (imageUrl instanceof String url && XLiteUtils.isHttpUrl(url)) {
            String originalUrl = originalImageUrl(url);
            return new DownloadItem(originalUrl, "jpg", "image/jpeg", "Image");
        }

        Object variantsValue = XLiteUtils.invokeIfPresent(media, "getVariants");
        if (!(variantsValue instanceof List<?> variants)) return null;

        Variant bestVariant = bestMp4Variant(variants);
        if (bestVariant == null) return null;

        String simpleName = media.getClass().getSimpleName();
        String label = simpleName.endsWith("Gif") ? "GIF" : "Video";
        return new DownloadItem(bestVariant.url, "mp4", "video/mp4", label);
    }

    private static Variant bestMp4Variant(List<?> variants) throws ReflectiveOperationException {
        Variant best = null;
        for (Object candidate : variants) {
            if (candidate == null) continue;

            Object urlValue = XLiteUtils.invoke(candidate, "getUrl");
            Object contentTypeValue = XLiteUtils.invoke(candidate, "getContentType");
            if (!(urlValue instanceof String url) || !XLiteUtils.isHttpUrl(url)) continue;

            String contentType = contentTypeValue == null ? "" : String.valueOf(contentTypeValue);
            if (!contentType.equalsIgnoreCase("video/mp4") && !url.toLowerCase().contains(".mp4")) {
                continue;
            }

            Object bitRateValue = XLiteUtils.invoke(candidate, "getBitRate");
            int bitRate = bitRateValue instanceof Number number ? number.intValue() : 0;
            if (best == null || bitRate > best.bitRate) best = new Variant(url, bitRate);
        }
        return best;
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
        int exists = 0;
        int failed = 0;
        for (int index = 0; index < downloads.size(); index++) {
            switch (enqueueDownload(context, downloads.get(index), username, postId, index, downloads.size())) {
                case QUEUED -> queued++;
                case EXISTS -> exists++;
                case FAILED -> failed++;
            }
        }
        showQueueResult(context, queued, exists, failed);
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
            case EXISTS -> Utils.showToastShort("Already downloaded");
            case FAILED -> Utils.showToastShort("Could not start download");
        }
    }

    private static EnqueueState enqueueDownload(
            Context context,
            DownloadItem download,
            String username,
            String postId,
            int index,
            int mediaCount
    ) {
        String baseFileName = downloadFileName(username, postId, download.extension, index, mediaCount);
        String fileName;
        try {
            fileName = resolveTargetFileName(context, baseFileName, conflictBehavior());
        } catch (IOException | RuntimeException exception) {
            Logger.printException(() -> "Failed to resolve X-Lite download target", exception);
            return EnqueueState.FAILED;
        }
        if (fileName == null) return EnqueueState.EXISTS;

        String temporaryFileName = temporaryDownloadFileName(fileName);
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(download.url))
                    .setTitle(fileName)
                    .setDescription("Downloading media from @" + username)
                    .setMimeType(download.mimeType)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_PICTURES,
                            DOWNLOAD_DIRECTORY + "/" + temporaryFileName
                    );

            DownloadManager manager =
                    (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("DownloadManager is unavailable");

            long downloadId = manager.enqueue(request);
            savePendingDownload(
                    context,
                    downloadId,
                    temporaryFileName,
                    fileName,
                    download.mimeType,
                    download.url
            );
            finishPendingDownloadAsync(context, manager, downloadId);
            return EnqueueState.QUEUED;
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to enqueue X-Lite media download", exception);
            return EnqueueState.FAILED;
        }
    }

    private static void enqueueFallbackDownload(
            Context context,
            DownloadManager manager,
            String fallbackUrl,
            String fileName,
            String mimeType
    ) {
        String temporaryFileName = temporaryDownloadFileName(fileName);
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(fallbackUrl))
                    .setTitle(fileName)
                    .setDescription("Downloading media")
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
            savePendingDownload(context, downloadId, temporaryFileName, fileName, mimeType, fallbackUrl);
            finishPendingDownloadAsync(context, manager, downloadId);
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to enqueue fallback media download", exception);
            Utils.showToastShort("Download failed: " + fileName);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private static void registerDownloadReceiver(Context context) {
        if (downloadReceiverRegistered) return;

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId < 0) return;

                DownloadManager manager =
                        (DownloadManager) receiverContext.getSystemService(Context.DOWNLOAD_SERVICE);
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
        DownloadManager manager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
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
            clearPendingDownload(context, downloadId);
            manager.remove(downloadId);
            if (pending.url != null && pending.url.contains("name=orig")) {
                String fallbackUrl = pending.url.replace("name=orig", "name=4096x4096");
                enqueueFallbackDownload(context, manager, fallbackUrl, pending.fileName, pending.mimeType);
                return;
            }
            Utils.showToastShort("Download failed: " + pending.fileName);
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

            clearPendingDownload(context, downloadId);
            manager.remove(downloadId);
            Utils.showToastShort("Downloaded: " + pending.fileName);
        } catch (IOException | RuntimeException exception) {
            Logger.printException(() -> "Failed to finalize X-Lite media download", exception);
            Utils.showToastShort("Could not finalize download: " + pending.fileName);
        }
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
        deleteExistingMedia(resolver, collection, fileName, relativePath);
        return true;
    }

    private static void deleteExistingMedia(
            ContentResolver resolver,
            Uri collection,
            String fileName,
            String relativePath
    ) {
        try {
            String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                    MediaStore.MediaColumns.RELATIVE_PATH + "=?";
            resolver.delete(collection, selection, new String[]{fileName, relativePath});
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to replace existing X-Lite media", exception);
        }
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
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex <= 0) return fileName + "_tmp";
        return fileName.substring(0, extensionIndex) + "_tmp" + fileName.substring(extensionIndex);
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

    private static void showQueueResult(Context context, int queued, int exists, int failed) {
        if (failed == 0 && exists == 0) {
            String message = queued == 1 ? "Download started" : queued + " downloads started";
            Utils.showToastShort(message);
            return;
        }
        if (queued == 0) {
            if (failed == 0 && exists > 0) {
                Utils.showToastShort(exists == 1 ? "Already downloaded" : exists + " media already downloaded");
                return;
            }
            Utils.showToastShort("Could not start download");
            return;
        }
        List<String> parts = new ArrayList<>();
        parts.add(queued == 1 ? "1 download started" : queued + " downloads started");
        if (exists > 0) parts.add(exists == 1 ? "1 already downloaded" : exists + " already downloaded");
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
    ) throws IOException {
        switch (behavior) {
            case OVERWRITE:
                return baseFileName;
            case SKIP:
                return mediaExists(context, baseFileName) ? null : baseFileName;
            case RENAME:
                if (!mediaExists(context, baseFileName)) return baseFileName;
                return uniqueFileName(context, baseFileName);
            default:
                return baseFileName;
        }
    }

    private static String uniqueFileName(Context context, String baseFileName) throws IOException {
        int dot = baseFileName.lastIndexOf('.');
        String stem = dot > 0 ? baseFileName.substring(0, dot) : baseFileName;
        String extension = dot > 0 ? baseFileName.substring(dot) : "";
        int counter = 1;
        while (true) {
            String candidate = stem + "_" + counter + extension;
            if (!mediaExists(context, candidate)) return candidate;
            counter++;
        }
    }

    private static boolean mediaExists(Context context, String fileName) throws IOException {
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
        Activity activity = resumedActivity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return null;
        return activity;
    }

    private static void clearActivity(Activity activity) {
        if (resumedActivity.get() == activity) resumedActivity.clear();
    }

    private static final class PresenterData {
        final Context context;
        final Object post;

        PresenterData(Context context, Object post) {
            this.context = context;
            this.post = post;
        }
    }

    private enum EnqueueState {
        QUEUED,
        EXISTS,
        FAILED,
    }

    private enum ConflictBehavior {
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
