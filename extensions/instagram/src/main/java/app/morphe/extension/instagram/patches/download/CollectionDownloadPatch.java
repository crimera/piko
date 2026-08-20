/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.download;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Dialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.View;

import com.instagram.common.session.UserSession;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.crimera.downloader.DownloadRequest;
import app.morphe.extension.crimera.downloader.MediaDownloader;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Utils;

public final class CollectionDownloadPatch {
    private static final long PAGE_TIMEOUT_MS = 45_000L;
    private static final long REFRESH_NO_OP_TIMEOUT_MS = 15_000L;
    private static final long POLL_INTERVAL_MS = 200L;
    private static final int MAX_PAGE_REQUESTS = 100;
    private static final int FRAGMENT_SEARCH_DEPTH = 3;
    private static final String SAVED_COLLECTION_CLASS = "com.instagram.save.model.SavedCollection";
    private static final String MEDIA_CLASS = "com.instagram.feed.media.Media";
    private static final String SOURCE_FIELD_NAME = "sourceFieldName";
    private static final String SOURCE_STATE_FIELD_NAME = "sourceStateFieldName";
    private static final String SOURCE_HAS_CURSOR_METHOD_NAME = "sourceHasCursorMethodName";
    private static final String SOURCE_CAN_LOAD_MORE_METHOD_NAME = "sourceCanLoadMoreMethodName";
    private static final String STATE_HAS_MORE_FIELD_NAME = "stateHasMoreFieldName";
    private static final String STATE_REQUEST_ALLOWED_METHOD_NAME =
            "stateRequestAllowedMethodName";
    private static final String LOAD_NEXT_PAGE_METHOD_NAME = "loadNextPageMethodName";
    private static final String REFRESH_COLLECTION_METHOD_NAME = "refreshCollectionMethodName";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static PendingDownload pendingDownload;
    private static int requestGeneration;

    private static final class SearchNode {
        final Object value;
        final int depth;

        SearchNode(Object value, int depth) {
            this.value = value;
            this.depth = depth;
        }
    }

    private static final class NativeState {
        final int mediaCount;
        final boolean sourceReady;
        final boolean hasCursor;
        final boolean canLoadMore;
        final boolean hasMore;
        final boolean requestAllowed;

        NativeState(
                int mediaCount,
                boolean sourceReady,
                boolean hasCursor,
                boolean canLoadMore,
                boolean hasMore,
                boolean requestAllowed
        ) {
            this.mediaCount = mediaCount;
            this.sourceReady = sourceReady;
            this.hasCursor = hasCursor;
            this.canLoadMore = canLoadMore;
            this.hasMore = hasMore;
            this.requestAllowed = requestAllowed;
        }

        String signature() {
            return mediaCount + ":" + sourceReady + ":" + hasCursor + ":" +
                    canLoadMore + ":" + hasMore + ":" + requestAllowed;
        }
    }

    private static final class PendingDownload {
        final WeakReference<Context> context;
        final Context applicationContext;
        final WeakReference<Object> postsFragment;
        final UserSession userSession;
        final int generation;

        CollectionDownloadNotification notification;
        BroadcastReceiver cancelReceiver;
        boolean receiverRegistered;
        boolean cancelled;
        boolean downloadStarted;
        Dialog confirmationDialog;
        MediaDownloader downloader;
        MediaDownloader.BatchHandle batchHandle;
        boolean serviceStarted;
        boolean refreshAttempted;
        boolean waitingForTransition;
        boolean waitingForRefresh;
        String waitingSignature;
        long waitingSince;
        String passiveSignature;
        long passiveSince;
        int pageRequests;
        int reportedMediaCount = -1;

        PendingDownload(
                Context context,
                Object postsFragment,
                UserSession userSession,
                int generation
        ) {
            this.context = new WeakReference<>(context);
            Context appContext = context.getApplicationContext();
            this.applicationContext = appContext != null ? appContext : context;
            this.postsFragment = new WeakReference<>(postsFragment);
            this.userSession = userSession;
            this.generation = generation;
            this.passiveSince = SystemClock.uptimeMillis();
        }

        void startFeedback() {
            String cancelAction = applicationContext.getPackageName()
                    + ".piko.CANCEL_COLLECTION_DOWNLOAD."
                    + generation
                    + "."
                    + UUID.randomUUID();
            Intent cancel = new Intent(cancelAction).setPackage(applicationContext.getPackageName());
            PendingIntent cancelIntent = PendingIntent.getBroadcast(
                    applicationContext,
                    generation,
                    cancel,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            cancelReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ignored, Intent intent) {
                    if (cancelAction.equals(intent.getAction())) {
                        cancelPending(PendingDownload.this, true);
                    }
                }
            };
            IntentFilter filter = new IntentFilter(cancelAction);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applicationContext.registerReceiver(
                        cancelReceiver,
                        filter,
                        Context.RECEIVER_NOT_EXPORTED
                );
            } else {
                applicationContext.registerReceiver(cancelReceiver, filter);
            }
            receiverRegistered = true;
            notification = new CollectionDownloadNotification(applicationContext, cancelIntent);
            notification.showLoading(0);
        }

        void releaseReceiver() {
            if (!receiverRegistered || cancelReceiver == null) return;
            receiverRegistered = false;
            try {
                applicationContext.unregisterReceiver(cancelReceiver);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private CollectionDownloadPatch() {}

    /*
     * This patch drives Instagram's native saved-collection Posts paginator until the collection is
     * fully loaded, then hands the resulting media set to the downloader batch path.
     *
     * The Posts tab is authoritative here. Live comparison on Instagram 439 showed the Reels tab
     * was a strict subset of the fully loaded Posts grid, so combining both would duplicate work.
     *
     * The placeholder field and method names below are rewritten by the bytecode patch to the real
     * obfuscated members for the current Instagram target.
     */

    public static void addMenuItem(final Object actionBuilder, final Object optionsSheet) {
        try {
            if (!Pref.enableDownload()) return;

            final Context context = findFieldValue(optionsSheet, Context.class);
            addNormalAction(actionBuilder, str("piko_download_collection"), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    beginDownload(optionsSheet, context);
                }
            });
        } catch (Throwable error) {
            logError("Could not add the saved collection download action", error);
        }
    }

    private static void addNormalAction(Object actionBuilder, String title, View.OnClickListener listener)
            throws Exception {
        Method method = actionBuilder.getClass().getDeclaredMethod(
                "normalActionMethod",
                String.class,
                View.OnClickListener.class
        );
        method.setAccessible(true);
        method.invoke(actionBuilder, title, listener);
    }

    private static void beginDownload(Object optionsSheet, Context context) {
        PendingDownload pending = null;
        try {
            if (pendingDownload != null) {
                Utils.showToastShort(str("piko_collection_download_in_progress"));
                return;
            }

            Object postsFragment = findPostsFragment(optionsSheet);
            UserSession userSession = findFieldValue(postsFragment, UserSession.class);
            pending = new PendingDownload(
                    context,
                    postsFragment,
                    userSession,
                    ++requestGeneration
            );
            pendingDownload = pending;
            pending.startFeedback();
            Utils.showToastShort(str("piko_preparing_collection_download"));
            final PendingDownload startedDownload = pending;
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    drive(startedDownload);
                }
            });
        } catch (Throwable error) {
            if (pending != null) cleanupPending(pending, true);
            else pendingDownload = null;
            logError("Could not start the saved collection download", error);
            Utils.showToastShort(str("piko_download_collection_failed"));
        }
    }

    private static void drive(final PendingDownload pending) {
        if (pendingDownload != pending || requestGeneration != pending.generation) return;

        try {
            Object postsFragment = pending.postsFragment.get();
            if (postsFragment == null) {
                failPending(pending, "Saved collection fragment was released", null);
                return;
            }

            NativeState state = readNativeState(postsFragment);
            if (pending.notification != null && state.mediaCount != pending.reportedMediaCount) {
                pending.reportedMediaCount = state.mediaCount;
                pending.notification.showLoading(state.mediaCount);
            }
            long now = SystemClock.uptimeMillis();

            if (pending.waitingForTransition) {
                if (!state.signature().equals(pending.waitingSignature)) {
                    pending.waitingForTransition = false;
                    pending.waitingForRefresh = false;
                    pending.passiveSignature = state.signature();
                    pending.passiveSince = now;
                } else {
                    long timeout = pending.waitingForRefresh
                            ? REFRESH_NO_OP_TIMEOUT_MS
                            : PAGE_TIMEOUT_MS;
                    if (now - pending.waitingSince < timeout) {
                        scheduleDrive(pending);
                        return;
                    }
                    if (!pending.waitingForRefresh) {
                        failPending(pending, "Saved collection page request timed out", null);
                        return;
                    }
                    pending.waitingForTransition = false;
                    pending.waitingForRefresh = false;
                }
            }

            if (!state.sourceReady && !pending.refreshAttempted) {
                pending.refreshAttempted = true;
                invokeRefresh(postsFragment);
                waitForTransition(pending, state, true);
                return;
            }

            if (!state.sourceReady || !state.requestAllowed) {
                if (!state.signature().equals(pending.passiveSignature)) {
                    pending.passiveSignature = state.signature();
                    pending.passiveSince = now;
                } else if (now - pending.passiveSince >= PAGE_TIMEOUT_MS) {
                    failPending(pending, "Saved collection native loading timed out", null);
                    return;
                }
                scheduleDrive(pending);
                return;
            }

            pending.passiveSignature = state.signature();
            pending.passiveSince = now;

            if (state.canLoadMore) {
                if (pending.pageRequests >= MAX_PAGE_REQUESTS) {
                    failPending(pending, "Saved collection exceeded the page safety limit", null);
                    return;
                }
                pending.pageRequests++;
                invokeLoadNext(postsFragment);
                waitForTransition(pending, state, false);
                return;
            }

            if (state.mediaCount > 0 && (!state.hasCursor || !state.hasMore)) {
                showConfirmation(pending, postsFragment);
                return;
            }

            if (state.mediaCount == 0 && !pending.refreshAttempted) {
                pending.refreshAttempted = true;
                invokeRefresh(postsFragment);
                waitForTransition(pending, state, true);
                return;
            }

            if (state.mediaCount == 0 && !state.hasCursor) {
                cleanupPending(pending, true);
                Utils.showToastShort(str("piko_download_collection_empty"));
                return;
            }

            failPending(pending, "Saved collection native paginator cannot advance", null);
        } catch (Throwable error) {
            failPending(pending, "Could not prepare the saved collection download", error);
        }
    }

    private static void waitForTransition(
            PendingDownload pending,
            NativeState before,
            boolean refresh
    ) {
        pending.waitingForTransition = true;
        pending.waitingForRefresh = refresh;
        pending.waitingSignature = before.signature();
        pending.waitingSince = SystemClock.uptimeMillis();
        scheduleDrive(pending);
    }

    private static void scheduleDrive(final PendingDownload pending) {
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                drive(pending);
            }
        }, POLL_INTERVAL_MS);
    }

    private static NativeState readNativeState(Object postsFragment) throws Exception {
        int mediaCount = mediaSnapshot(postsFragment).size();
        Object source = readSource(postsFragment);
        if (source == null) {
            return new NativeState(mediaCount, false, false, false, false, false);
        }

        Object state = readSourceState(source);
        if (state == null) {
            return new NativeState(mediaCount, false, false, false, false, false);
        }

        return new NativeState(
                mediaCount,
                true,
                sourceHasCursor(source),
                sourceCanLoadMore(source),
                stateHasMore(state),
                stateRequestAllowed(state)
        );
    }

    private static Object readSource(Object postsFragment) throws Exception {
        return readNamedField(postsFragment, SOURCE_FIELD_NAME);
    }

    private static Object readSourceState(Object source) throws Exception {
        return readNamedField(source, SOURCE_STATE_FIELD_NAME);
    }

    private static boolean sourceHasCursor(Object source) throws Exception {
        return invokeBoolean(source, SOURCE_HAS_CURSOR_METHOD_NAME);
    }

    private static boolean sourceCanLoadMore(Object source) throws Exception {
        return invokeBoolean(source, SOURCE_CAN_LOAD_MORE_METHOD_NAME);
    }

    private static boolean stateHasMore(Object state) throws Exception {
        return (Boolean) readNamedField(state, STATE_HAS_MORE_FIELD_NAME);
    }

    private static boolean stateRequestAllowed(Object state) throws Exception {
        Method method = findMethod(state.getClass(), STATE_REQUEST_ALLOWED_METHOD_NAME, boolean.class);
        return (Boolean) method.invoke(state, false);
    }

    private static void invokeLoadNext(Object postsFragment) throws Exception {
        invokeVoid(postsFragment, LOAD_NEXT_PAGE_METHOD_NAME);
    }

    private static void invokeRefresh(Object postsFragment) throws Exception {
        invokeVoid(postsFragment, REFRESH_COLLECTION_METHOD_NAME);
    }

    private static boolean invokeBoolean(Object owner, String methodName) throws Exception {
        Method method = findMethod(owner.getClass(), methodName);
        return (Boolean) method.invoke(owner);
    }

    private static void invokeVoid(Object owner, String methodName) throws Exception {
        Method method = findMethod(owner.getClass(), methodName);
        method.invoke(owner);
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Continue through inherited Instagram implementation classes.
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name);
    }

    private static Object readNamedField(Object owner, String name) throws Exception {
        for (Class<?> current = owner.getClass(); current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
                // Continue through inherited Instagram implementation classes.
            }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }

    private static void showConfirmation(PendingDownload pending, Object postsFragment)
            throws Exception {
        Context context = pending.context.get();
        if (context == null) {
            failPending(pending, "Saved collection context was released", null);
            return;
        }

        final List<Object> media = mediaSnapshot(postsFragment);
        if (media.isEmpty()) {
            cleanupPending(pending, true);
            Utils.showToastShort(str("piko_download_collection_empty"));
            return;
        }

        final DownloadUtils.CollectionDownloadPlan plan =
                DownloadUtils.prepareCollectionDownload(pending.userSession, media);
        final List<DownloadRequest> requests = plan.getRequests();
        if (requests.isEmpty()) {
            failPending(pending, "Saved collection produced no download requests", null);
            return;
        }

        InstagramDialogBox dialog = new InstagramDialogBox(context);
        dialog.setTitle(str("piko_download_collection"));
        dialog.setMessage(str("piko_download_collection_confirm", media.size(), requests.size()));
        dialog.setNegativeButton(
                context.getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface ignored, int which) {
                        cancelPending(pending, true);
                    }
                }
        );
        dialog.setPositiveButton(
                str("piko_download_files", requests.size()),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface ignored, int which) {
                        pending.downloadStarted = true;
                        download(pending, plan);
                    }
                }
        );
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface ignored) {
                pending.confirmationDialog = null;
                if (!pending.downloadStarted && !pending.cancelled && pendingDownload == pending) {
                    cancelPending(pending, true);
                }
            }
        });
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Dialog nativeDialog = dialog.getDialog();
        if (nativeDialog == null) throw new IllegalStateException("Instagram dialog is unavailable");
        pending.confirmationDialog = nativeDialog;
        if (pending.notification != null) {
            pending.notification.showReady(media.size(), requests.size());
        }
        nativeDialog.show();
    }

    private static void download(
            PendingDownload pending,
            DownloadUtils.CollectionDownloadPlan plan
    ) {
        try {
            if (!Utils.isNetworkConnected()) {
                Utils.showToastShort(str("piko_no_internet"));
                failDownload(pending, "Network is unavailable", null, false);
                return;
            }

            Context context = pending.context.get();
            if (context == null) {
                failDownload(pending, "Saved collection context was released", null);
                return;
            }

            final int preparationFailures = plan.getFailedPosts();
            final List<DownloadRequest> requests = plan.getRequests();

            Notification notification = pending.notification.showDownloading(0, requests.size());
            CollectionDownloadService.start(context, notification);
            pending.serviceStarted = true;
            pending.downloader = new MediaDownloader(context);
            pending.batchHandle = pending.downloader.downloadBatch(
                    requests,
                    new MediaDownloader.BatchListener() {
                        @Override
                        public void onProgress(int processed, int total) {
                            MAIN.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (pendingDownload == pending && !pending.cancelled) {
                                        pending.notification.showDownloading(processed, total);
                                    }
                                }
                            });
                        }

                        @Override
                        public void onCompleted(int downloaded, int skipped, int failed) {
                            MAIN.post(new Runnable() {
                                @Override
                                public void run() {
                                    completeDownload(
                                            pending,
                                            downloaded,
                                            skipped,
                                            failed,
                                            preparationFailures
                                    );
                                }
                            });
                        }

                        @Override
                        public void onCancelled(int processed, int total) {
                            MAIN.post(new Runnable() {
                                @Override
                                public void run() {
                                    cancelPending(pending, true);
                                }
                            });
                        }

                        @Override
                        public void onError(Throwable error) {
                            MAIN.post(new Runnable() {
                                @Override
                                public void run() {
                                    failDownload(
                                            pending,
                                            "Could not download the saved collection",
                                            error
                                    );
                                }
                            });
                        }
                    }
            );
        } catch (Throwable error) {
            failDownload(pending, "Could not start the saved collection download", error);
        }
    }

    private static Object findPostsFragment(Object optionsSheet) throws Exception {
        Object provider = findCollectionProvider(optionsSheet);
        for (Field field : allFields(provider.getClass())) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object candidate = field.get(provider);
            Object postsFragment = findPostsFragment(candidate, FRAGMENT_SEARCH_DEPTH);
            if (postsFragment != null) return postsFragment;
        }
        throw new IllegalStateException("Saved collection fragment is unavailable");
    }

    private static Object findPostsFragment(Object root, int maxDepth) {
        if (root == null) return null;

        Deque<SearchNode> queue = new ArrayDeque<>();
        Map<Object, Boolean> visited = new IdentityHashMap<>();
        queue.addLast(new SearchNode(root, 0));

        while (!queue.isEmpty()) {
            SearchNode node = queue.removeFirst();
            Object candidate = node.value;
            if (visited.put(candidate, Boolean.TRUE) != null) continue;
            if (isPostsFragment(candidate)) return candidate;

            if (candidate instanceof SparseArray) {
                SparseArray<?> values = (SparseArray<?>) candidate;
                for (int index = 0; index < values.size(); index++) {
                    Object value = values.valueAt(index);
                    if (value == null) continue;
                    if (isPostsFragment(value)) return value;
                    if (node.depth < maxDepth) {
                        queue.addLast(new SearchNode(value, node.depth + 1));
                    }
                }
                continue;
            }

            if (node.depth >= maxDepth) continue;
            for (Field field : allFields(candidate.getClass())) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(candidate);
                    if (shouldInspect(value)) {
                        queue.addLast(new SearchNode(value, node.depth + 1));
                    }
                } catch (Throwable ignored) {
                    // Some framework-owned fields are not reflectively accessible.
                }
            }
        }
        return null;
    }

    private static boolean isPostsFragment(Object candidate) {
        Class<?> type = candidate.getClass();
        return hasFieldType(type, SAVED_COLLECTION_CLASS) &&
                hasFieldType(type, UserSession.class.getName()) &&
                hasFieldAssignableTo(type, Map.class);
    }

    private static boolean shouldInspect(Object value) {
        if (value == null) return false;
        if (value instanceof SparseArray) return true;
        return value.getClass().getName().startsWith("X.");
    }

    private static Object findCollectionProvider(Object optionsSheet) throws Exception {
        for (Field field : allFields(optionsSheet.getClass())) {
            field.setAccessible(true);
            Object candidate = field.get(optionsSheet);
            if (candidate == null) continue;

            for (Method method : candidate.getClass().getDeclaredMethods()) {
                if (method.getParameterTypes().length == 0 &&
                        SAVED_COLLECTION_CLASS.equals(method.getReturnType().getName())) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("Saved collection provider is unavailable");
    }

    private static boolean hasFieldType(Class<?> owner, String typeName) {
        for (Field field : allFields(owner)) {
            if (typeName.equals(field.getType().getName())) return true;
        }
        return false;
    }

    private static boolean hasFieldAssignableTo(Class<?> owner, Class<?> fieldType) {
        for (Field field : allFields(owner)) {
            if (fieldType.isAssignableFrom(field.getType())) return true;
        }
        return false;
    }

    private static List<Object> mediaSnapshot(Object postsFragment) throws Exception {
        for (Field field : allFields(postsFragment.getClass())) {
            if (!Map.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            Object value = field.get(postsFragment);
            if (!(value instanceof Map)) continue;

            Map<?, ?> map = (Map<?, ?>) value;
            if (map.isEmpty()) continue;
            Object firstKey = map.keySet().iterator().next();
            if (firstKey != null && MEDIA_CLASS.equals(firstKey.getClass().getName())) {
                return new ArrayList<Object>(map.keySet());
            }
        }
        return new ArrayList<>();
    }

    private static <T> T findFieldValue(Object owner, Class<T> type) throws Exception {
        for (Field field : allFields(owner.getClass())) {
            if (!type.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            Object value = field.get(owner);
            if (value != null) return type.cast(value);
        }
        throw new IllegalStateException(type.getSimpleName() + " is unavailable");
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Collections.addAll(fields, current.getDeclaredFields());
        }
        return fields;
    }

    private static void failPending(PendingDownload pending, String message, Throwable error) {
        if (pendingDownload != pending) return;
        if (error != null) logError(message, error);
        else PikoUtils.logger(message);

        Notification terminalNotification = pending.notification != null
                ? pending.notification.buildFailed()
                : null;
        cleanupPending(pending, false, terminalNotification);
        Utils.showToastShort(str("piko_download_collection_failed"));
    }

    private static void failDownload(PendingDownload pending, String message, Throwable error) {
        failDownload(pending, message, error, true);
    }

    private static void failDownload(
            PendingDownload pending,
            String message,
            Throwable error,
            boolean showToast
    ) {
        if (pendingDownload != pending) return;
        if (error != null) logError(message, error);
        else PikoUtils.logger(message);

        if (pending.batchHandle != null) pending.batchHandle.cancel();
        Notification terminalNotification = pending.notification != null
                ? pending.notification.buildFailed()
                : null;
        cleanupPending(pending, false, terminalNotification);
        if (showToast) Utils.showToastShort(str("piko_collection_download_failed"));
    }

    private static void completeDownload(
            PendingDownload pending,
            int downloaded,
            int skipped,
            int failedFiles,
            int skippedPosts
    ) {
        if (pendingDownload != pending || pending.cancelled) return;
        Notification terminalNotification =
                pending.notification.buildComplete(
                        downloaded,
                        skipped,
                        failedFiles,
                        skippedPosts
                );
        cleanupPending(pending, false, terminalNotification);
        Utils.showToastShort(str("piko_collection_download_complete"));
    }

    private static void cancelPending(PendingDownload pending, boolean showToast) {
        if (pendingDownload != pending || pending.cancelled) return;
        pending.cancelled = true;

        if (pending.batchHandle != null) pending.batchHandle.cancel();
        Dialog confirmationDialog = pending.confirmationDialog;
        pending.confirmationDialog = null;
        cleanupPending(pending, true);

        if (confirmationDialog != null && confirmationDialog.isShowing()) {
            confirmationDialog.dismiss();
        }
        if (showToast) Utils.showToastShort(str("piko_collection_download_cancelled"));
    }

    private static void cleanupPending(PendingDownload pending, boolean cancelNotification) {
        cleanupPending(pending, cancelNotification, null);
    }

    private static void cleanupPending(
            PendingDownload pending,
            boolean cancelNotification,
            Notification terminalNotification
    ) {
        if (pendingDownload == pending) {
            pendingDownload = null;
            requestGeneration++;
        }
        pending.releaseReceiver();
        if (cancelNotification && pending.notification != null) pending.notification.cancel();
        if (pending.serviceStarted) {
            pending.serviceStarted = false;
            if (terminalNotification != null) {
                CollectionDownloadService.finish(pending.applicationContext, terminalNotification);
            } else {
                CollectionDownloadService.stop(pending.applicationContext);
            }
        } else if (terminalNotification != null && pending.notification != null) {
            pending.notification.show(terminalNotification);
        }
        pending.confirmationDialog = null;
        pending.batchHandle = null;
        pending.downloader = null;
    }

    private static void logError(String message, Throwable error) {
        PikoUtils.logger(message);
        PikoUtils.logger(error);
    }
}
