package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.widget.NestedScrollView;

import java.lang.reflect.Modifier;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.newx.ui.LoadingIndicatorView;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.newx.utils.NewXUtils;

import kotlin.jvm.functions.Function1;

/** AndroidView bridge for the profile Photos timeline. */
public final class ProfilePhotosGallery {
    public static final String SETTING_ID = "newx.post_actions_media.gallery_profile_photos";

    private ProfilePhotosGallery() {
    }

    public static boolean isEnabled() {
        return SettingsRegistry.getBooleanOrDefault(SETTING_ID, true);
    }

    private static final Object PENDING_PHOTO_LOCK = new Object();
    private static final long PENDING_PHOTO_TIMEOUT_MS = 1500L;
    private static String pendingPhotoPostId;
    private static int pendingPhotoIndex = -1;
    private static long pendingPhotoExpiresAt;

    public static void requestNativePhoto(String postId, int photoIndex) {
        if (postId == null || postId.trim().isEmpty() || photoIndex < 0) return;
        synchronized (PENDING_PHOTO_LOCK) {
            pendingPhotoPostId = postId;
            pendingPhotoIndex = photoIndex;
            pendingPhotoExpiresAt = System.currentTimeMillis() + PENDING_PHOTO_TIMEOUT_MS;
        }
    }

    public static int peekPendingPhotoIndex(String postId) {
        if (postId == null) return -1;
        synchronized (PENDING_PHOTO_LOCK) {
            if (pendingPhotoIndex < 0
                    || !postId.equals(pendingPhotoPostId)
                    || System.currentTimeMillis() > pendingPhotoExpiresAt) {
                clearPendingPhotoLocked();
                return -1;
            }
            return pendingPhotoIndex;
        }
    }

    public static void clearPendingPhoto() {
        synchronized (PENDING_PHOTO_LOCK) {
            clearPendingPhotoLocked();
        }
    }

    private static void clearPendingPhotoLocked() {
        pendingPhotoPostId = null;
        pendingPhotoIndex = -1;
        pendingPhotoExpiresAt = 0L;
    }

    public static Function1<Object, Object> createFactory(
            List<?> items,
            Object callback,
            Object itemClickCallback,
            float topPaddingDp,
            float bottomPaddingDp
    ) {
        return new GalleryFactory(items, callback, itemClickCallback, topPaddingDp, bottomPaddingDp);
    }

    public static Function1<Object, Object> createUpdater(
            List<?> items,
            Object callback,
            Object itemClickCallback,
            float topPaddingDp,
            float bottomPaddingDp
    ) {
        return new GalleryUpdater(items, callback, itemClickCallback, topPaddingDp, bottomPaddingDp);
    }

    private static final class GalleryFactory implements Function1<Object, Object> {
        private final List<?> items;
        private final Object callback;
        private final Object itemClickCallback;
        private final float topPaddingDp;
        private final float bottomPaddingDp;

        GalleryFactory(
                List<?> items,
                Object callback,
                Object itemClickCallback,
                float topPaddingDp,
                float bottomPaddingDp
        ) {
            this.items = items;
            this.callback = callback;
            this.itemClickCallback = itemClickCallback;
            this.topPaddingDp = topPaddingDp;
            this.bottomPaddingDp = bottomPaddingDp;
        }

        @Override
        public Object invoke(Object context) {
            return new GalleryView(
                    (Context) context,
                    items,
                    callback,
                    itemClickCallback,
                    topPaddingDp,
                    bottomPaddingDp
            );
        }
    }

    private static final class GalleryUpdater implements Function1<Object, Object> {
        private final List<?> items;
        private final Object callback;
        private final Object itemClickCallback;
        private final float topPaddingDp;
        private final float bottomPaddingDp;

        GalleryUpdater(
                List<?> items,
                Object callback,
                Object itemClickCallback,
                float topPaddingDp,
                float bottomPaddingDp
        ) {
            this.items = items;
            this.callback = callback;
            this.itemClickCallback = itemClickCallback;
            this.topPaddingDp = topPaddingDp;
            this.bottomPaddingDp = bottomPaddingDp;
        }

        @Override
        public Object invoke(Object view) {
            if (view instanceof GalleryView galleryView) {
                galleryView.update(
                        items,
                        callback,
                        itemClickCallback,
                        topPaddingDp,
                        bottomPaddingDp
                );
            }
            // AndroidView ignores the updater result; avoid referencing the host's obfuscated
            // Kotlin Unit singleton field from the extension DEX.
            return null;
        }
    }

    private static final class GalleryView extends NestedScrollView {
        private final LinearLayout content;
        private final GalleryGrid grid;
        private final LoadingIndicatorView loadingIndicator;
        private Object callback;
        private Object itemClickCallback;
        private List<?> currentItems;
        private int currentItemCount = -1;
        private boolean loadMoreInFlight = false;
        private long lastLoadMoreTime = 0;
        GalleryView(
                Context context,
                List<?> items,
                Object callback,
                Object itemClickCallback,
                float topPaddingDp,
                float bottomPaddingDp
        ) {
            // The target APK retains this constructor shape. It initializes the nested-scrolling
            // helper before enabling nested scrolling in NestedScrollView's constructor.
            super(context, null);
            this.callback = callback;
            this.itemClickCallback = itemClickCallback;
            float density = getResources().getDisplayMetrics().density;
            int topPadding = Math.round(Math.max(0f, topPaddingDp) * density);
            int bottomPadding = Math.round(Math.max(0f, bottomPaddingDp) * density);
            setPadding(0, topPadding, 0, bottomPadding);
            setFillViewport(true);
            setBackgroundColor(Color.TRANSPARENT);
            setClipToPadding(true);
            setVerticalScrollBarEnabled(false);
            setHorizontalScrollBarEnabled(false);
            setOnScrollChangeListener((View.OnScrollChangeListener) (view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (scrollY <= oldScrollY) return;
                checkLoadMore();
            });

            content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setBackgroundColor(Color.TRANSPARENT);

            grid = new GalleryGrid(context);
            grid.setItemClickCallback(itemClickCallback);
            content.addView(grid, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            loadingIndicator = new LoadingIndicatorView(context);
            loadingIndicator.setVisibility(View.GONE);
            LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(1, Theme.dpToPx(context, 48f))
            );
            loadingParams.topMargin = Theme.dpToPx(context, 8f);
            content.addView(loadingIndicator, loadingParams);

            addView(content, new NestedScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            setItems(items);
        }

        void update(
                List<?> items,
                Object callback,
                Object itemClickCallback,
                float topPaddingDp,
                float bottomPaddingDp
        ) {
            this.callback = callback;
            this.itemClickCallback = itemClickCallback;
            grid.setItemClickCallback(itemClickCallback);
            float density = getResources().getDisplayMetrics().density;
            setPadding(
                    0,
                    Math.round(Math.max(0f, topPaddingDp) * density),
                    0,
                    Math.round(Math.max(0f, bottomPaddingDp) * density)
            );
            setItems(items);
        }

        void setItems(List<?> items) {
            List<?> nextItems = items == null ? Collections.emptyList() : items;
            boolean itemsChanged = currentItems != nextItems
                    && !nextItems.equals(currentItems);
            boolean itemCountChanged = currentItemCount != nextItems.size();
            List<GalleryCell> cells = extractCells(nextItems);
            grid.setCells(cells);
            if (itemsChanged || itemCountChanged) {
                final int itemCount = nextItems.size();
                final int cellCount = cells.size();
                logPagination(
                        "items updated count=" + itemCount +
                                " cells=" + cellCount +
                                " previousCount=" + currentItemCount +
                                " inFlight=" + loadMoreInFlight
                );
            }
            currentItems = nextItems;
            currentItemCount = nextItems.size();
            if (!loadMoreInFlight || itemsChanged || itemCountChanged) {
                setLoadingMore(false);
            }
        }

        private void checkLoadMore() {
            if (loadMoreInFlight || getChildCount() == 0) return;
            View child = getChildAt(0);
            int contentHeight = child.getHeight();
            int scrollY = getScrollY();
            int height = getHeight();
            if (contentHeight <= 0 || height <= 0) return;

            int remaining = child.getBottom() - (scrollY + height - getPaddingBottom());
            if (remaining > 0) return;

            long now = System.currentTimeMillis();
            if (now - lastLoadMoreTime <= 1200) return;

            lastLoadMoreTime = now;
            logPagination(
                    "threshold reached remaining=" + remaining +
                            " contentHeight=" + contentHeight +
                            " viewportHeight=" + height +
                            " callback=" + (callback == null ? "null" : callback.getClass().getName())
            );
            setLoadingMore(true);
            boolean dispatched = requestLoadMore(callback);
            logPagination("dispatch result=" + dispatched);
            if (!dispatched) {
                setLoadingMore(false);
                return;
            }

            // The footer extends the scroll content after the threshold is reached. Keep the
            // viewport at the new bottom so the indeterminate indicator is immediately visible.
            post(() -> {
                if (loadMoreInFlight) {
                    scrollTo(0, child.getBottom());
                }
            });

            // Safety timeout if network drops or no new items arrive.
            postDelayed(() -> {
                if (loadMoreInFlight) {
                    setLoadingMore(false);
                }
            }, 6000);
        }

        private void setLoadingMore(boolean loading) {
            if (loadMoreInFlight != loading) {
                logPagination("loading=" + loading);
            }
            loadMoreInFlight = loading;
            loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }
    private static final String PAGINATION_LOG_PREFIX =
            "[PikoNewX][PhotosGallery][Pagination] ";

    private static void logPagination(String message) {
        NewXLogger.printInfo(() -> PAGINATION_LOG_PREFIX + message);
    }

    private static void logPaginationFailure(String message, Throwable throwable) {
        NewXLogger.printException(() -> PAGINATION_LOG_PREFIX + message, throwable);
    }

    public static boolean requestLoadMore(Object callback) {
        if (callback == null) {
            logPagination("requestLoadMore callback=null");
            return false;
        }

        logPagination("requestLoadMore callback=" + callback.getClass().getName());
        try {
            Object pagingEvent = getPagingEvent();
            if (pagingEvent == null) {
                logPagination("requestLoadMore event=null");
                return false;
            }
            logPagination("event=" + pagingEvent.getClass().getName());

            Object urtComponent = findUrtComponent(callback, pagingEvent);
            if (urtComponent == null) {
                logPagination("UrtComponent not found callback=" + callback.getClass().getName());
                return false;
            }
            logPagination("UrtComponent=" + urtComponent.getClass().getName());

            Object bottomPaginator = getBottomPaginator(urtComponent, pagingEvent);
            if (bottomPaginator == null) {
                logPagination("bottom paginator not found component=" + urtComponent.getClass().getName());
                return false;
            }
            logPagination("bottom paginator=" + bottomPaginator.getClass().getName());

            return triggerBottomPaging(bottomPaginator, pagingEvent);
        } catch (Throwable t) {
            logPaginationFailure("requestLoadMore failed", t);
            return false;
        }
    }

    private static Object findUrtComponent(Object callback, Object pagingEvent) {
        if (callback == null || pagingEvent == null) return null;
        Class<?> clazz = callback.getClass();
        Field[] fields = clazz.getDeclaredFields();
        logPagination("searching component callbackFields=" + fields.length);
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(callback);
                if (value == null) continue;
                boolean matches = isUrtComponent(value, pagingEvent);
                logPagination(
                        "callback field=" + field.getName() +
                                " type=" + value.getClass().getName() +
                                " matches=" + matches
                );
                if (matches) return value;
            } catch (Throwable t) {
                logPaginationFailure("callback field inspection failed field=" + field.getName(), t);
            }
        }
        boolean callbackMatches = isUrtComponent(callback, pagingEvent);
        logPagination("callback itself matches=" + callbackMatches);
        if (callbackMatches) return callback;
        return null;
    }

    private static Object getPagingEvent() {
        try {
            Object event = createPagingEvent();
            logPagination("createPagingEvent returned=" +
                    (event == null ? "null" : event.getClass().getName()));
            return event;
        } catch (Throwable t) {
            logPaginationFailure("createPagingEvent failed", t);
            return null;
        }
    }

    private static List<Method> allMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Collections.addAll(methods, current.getDeclaredMethods());
        }
        return methods;
    }

    private static boolean isPagingDispatchMethod(Method method, Object pagingEvent) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return pagingEvent != null &&
                !Modifier.isStatic(method.getModifiers()) &&
                method.getReturnType() == void.class &&
                parameterTypes.length == 1 &&
                parameterTypes[0].isInterface() &&
                parameterTypes[0].isInstance(pagingEvent);
    }

    private static boolean isBottomPaginatorType(Class<?> type, Object pagingEvent) {
        if (type == null || pagingEvent == null) return false;

        // A UrtComponent getter commonly declares the paginator interface rather than
        // its concrete implementation. Interface methods include inherited dispatch APIs.
        if (type.isInterface()) {
            for (Method method : type.getMethods()) {
                if (isPagingDispatchMethod(method, pagingEvent)) return true;
            }
            return false;
        }

        for (Method method : allMethods(type)) {
            if (isPagingDispatchMethod(method, pagingEvent)) return true;
        }
        for (Class<?> interfaceType : type.getInterfaces()) {
            if (isBottomPaginatorType(interfaceType, pagingEvent)) return true;
        }
        return false;
    }

    private static boolean isBottomPaginator(Object value, Object pagingEvent) {
        if (value == null || pagingEvent == null) return false;
        return isBottomPaginatorType(value.getClass(), pagingEvent);
    }

    private static boolean isUrtComponent(Object obj, Object pagingEvent) {
        if (obj == null || pagingEvent == null) return false;
        for (Method method : allMethods(obj.getClass())) {
            if (Modifier.isStatic(method.getModifiers()) ||
                    method.getParameterTypes().length != 0) {
                continue;
            }

            Class<?> returnType = method.getReturnType();
            if (isBottomPaginatorType(returnType, pagingEvent)) return true;
            if (!returnType.isInterface()) continue;
            try {
                method.setAccessible(true);
                if (isBottomPaginator(method.invoke(obj), pagingEvent)) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static Object getBottomPaginator(Object urtComponent, Object pagingEvent) {
        Class<?> clazz = urtComponent.getClass();
        String expectedPaginatorClass = createBottomPaginatorClassName();
        int getterCandidates = 0;
        for (Method method : allMethods(clazz)) {
            if (Modifier.isStatic(method.getModifiers()) ||
                    method.getParameterTypes().length != 0) {
                continue;
            }

            Class<?> returnType = method.getReturnType();
            if (!returnType.isInterface() &&
                    !isBottomPaginatorType(returnType, pagingEvent)) {
                continue;
            }
            getterCandidates++;
            try {
                method.setAccessible(true);
                Object paginator = method.invoke(urtComponent);
                boolean shapeMatches = isBottomPaginator(paginator, pagingEvent);
                boolean matches = shapeMatches &&
                        expectedPaginatorClass.equals(paginator.getClass().getName());
                logPagination(
                        "paginator getter=" + method.getName() +
                                " declared=" + returnType.getName() +
                                " value=" + (paginator == null ? "null" : paginator.getClass().getName()) +
                                " shapeMatches=" + shapeMatches +
                                " matches=" + matches
                );
                if (matches) return paginator;
            } catch (Throwable t) {
                logPaginationFailure("paginator getter failed method=" + method.getName(), t);
            }
        }

        int fieldCandidates = 0;
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                if (Modifier.isStatic(field.getModifiers()) ||
                        (!fieldType.isInterface() &&
                                !isBottomPaginatorType(fieldType, pagingEvent))) {
                    continue;
                }
                fieldCandidates++;
                try {
                    field.setAccessible(true);
                    Object paginator = field.get(urtComponent);
                    boolean shapeMatches = isBottomPaginator(paginator, pagingEvent);
                    boolean matches = shapeMatches &&
                            expectedPaginatorClass.equals(paginator.getClass().getName());
                    logPagination(
                            "paginator field=" + field.getName() +
                                    " declared=" + fieldType.getName() +
                                    " value=" + (paginator == null ? "null" : paginator.getClass().getName()) +
                                    " shapeMatches=" + shapeMatches +
                                    " matches=" + matches
                    );
                    if (matches) return paginator;
                } catch (Throwable t) {
                    logPaginationFailure("paginator field failed field=" + field.getName(), t);
                }
            }
        }
        logPagination(
                "paginator candidates getters=" + getterCandidates +
                        " fields=" + fieldCandidates + " none matched expected=" + expectedPaginatorClass
        );
        return null;
    }

    private static boolean triggerBottomPaging(Object bottomPaginator, Object pagingEvent) {
        List<Method> terminationMethods = new ArrayList<>();
        for (Method method : allMethods(bottomPaginator.getClass())) {
            if (!Modifier.isStatic(method.getModifiers()) &&
                    method.getParameterTypes().length == 0 &&
                    (method.getReturnType() == boolean.class ||
                            method.getReturnType() == Boolean.class)) {
                terminationMethods.add(method);
            }
        }
        if (terminationMethods.size() == 1) {
            Method terminationMethod = terminationMethods.get(0);
            try {
                terminationMethod.setAccessible(true);
                boolean terminated = Boolean.TRUE.equals(terminationMethod.invoke(bottomPaginator));
                logPagination("termination method=" + terminationMethod.getName() + " result=" + terminated);
                if (terminated) {
                    logPagination("bottom pagination already terminated");
                    return false;
                }
            } catch (Throwable t) {
                logPaginationFailure("termination check failed method=" + terminationMethod.getName(), t);
            }
        } else {
            logPagination("termination method count=" + terminationMethods.size());
        }

        int dispatchCandidates = 0;
        for (Method method : allMethods(bottomPaginator.getClass())) {
            if (!isPagingDispatchMethod(method, pagingEvent)) continue;
            dispatchCandidates++;
            logPagination(
                    "dispatch method=" + method.getDeclaringClass().getName() +
                            "." + method.getName() +
                            " event=" + pagingEvent.getClass().getName()
            );
            method.setAccessible(true);
            try {
                method.invoke(bottomPaginator, pagingEvent);
                logPagination("dispatch invocation returned");
                return true;
            } catch (Throwable t) {
                logPaginationFailure("dispatch invocation failed method=" + method.getName(), t);
                return false;
            }
        }
        logPagination("dispatch candidates=" + dispatchCandidates + " none matched");
        return false;
    }

    private static Object createPagingEvent() {
        throw new IllegalStateException("NewX paging event bridge was not patched");
    }
    private static String createBottomPaginatorClassName() {
        throw new IllegalStateException("NewX bottom paginator class bridge was not patched");
    }

    private static final class GalleryGrid extends ViewGroup {
        private static final int COLUMN_COUNT = 3;
        private static final int GAP_DP = 2;
        private final int gapPixels;
        private List<GalleryCell> cells = Collections.emptyList();
        private Object itemClickCallback;

        GalleryGrid(Context context) {
            super(context);
            gapPixels = Math.max(1, Math.round(GAP_DP * getResources().getDisplayMetrics().density));
            setBackgroundColor(Color.BLACK);
            setWillNotDraw(true);
        }

        void setItemClickCallback(Object callback) {
            itemClickCallback = callback;
        }

        void setCells(List<GalleryCell> nextCells) {
            if (cells.equals(nextCells)) return;
            int oldSize = cells.size();
            cells = nextCells;

            if (oldSize == 0 || nextCells.size() < oldSize) {
                removeAllViews();
                for (GalleryCell cell : cells) {
                    addView(createCellView(cell));
                }
            } else {
                for (int i = oldSize; i < nextCells.size(); i++) {
                    addView(createCellView(nextCells.get(i)));
                }
            }
            requestLayout();
        }

        private View createCellView(GalleryCell cell) {
            ImageView image = new ImageView(getContext());
            image.setBackgroundColor(Color.BLACK);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setTag(cell.url);
            image.setOnClickListener(view -> openPhoto(cell));
            load(image, cell.url);
            return image;
        }

        private void load(ImageView image, String url) {
            MediaThumbnailLoader.load(
                    getContext(),
                    thumbnailUrl(url),
                    url,
                    bitmap -> {
                        if (url.equals(image.getTag()) && !bitmap.isRecycled()) {
                            image.setImageBitmap(bitmap);
                        }
                    }
            );
        }

        private void openPhoto(GalleryCell cell) {
            String postId = null;
            try {
                postId = NewXUtils.sourcePostId(cell.timelineItem);
            } catch (RuntimeException ignored) {
            }

            if (postId != null
                    && !postId.equals("post")
                    && itemClickCallback instanceof Function1) {
                requestNativePhoto(postId, Math.max(0, cell.photoIndex - 1));
                try {
                    Function1<Object, Object> callback = (Function1<Object, Object>) itemClickCallback;
                    callback.invoke(cell.timelineItem);
                } catch (Throwable exception) {
                    clearPendingPhoto();
                    NewXLogger.printInfo(() ->
                            "[PikoNewX][PhotosGallery] native viewer callback failed: "
                                    + exception.getMessage());
                }
            }

            openPhotoIntent(cell);
        }

        private void openPhotoIntent(GalleryCell cell) {
            Context context = getContext();
            String target = photoDeepLink(cell);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            intent.setPackage("com.twitter.android");
            try {
                context.startActivity(intent);
                return;
            } catch (RuntimeException first) {
                NewXLogger.printInfo(() -> "[PikoNewX][PhotosGallery] X viewer unavailable, falling back");
            }
            Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
            if (!(context instanceof Activity)) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            try {
                context.startActivity(fallback);
            } catch (RuntimeException exception) {
                NewXLogger.printInfo(() -> "[PikoNewX][PhotosGallery] no viewer for " + target);
            }
        }

        private static String photoDeepLink(GalleryCell cell) {
            try {
                String postId = NewXUtils.sourcePostId(cell.timelineItem);
                String username = NewXUtils.sourceUsername(cell.timelineItem);
                if (postId != null && username != null
                        && !postId.equals("post") && !username.equals("twitter")) {
                    return "https://x.com/" + username + "/status/" + postId
                            + "/photo/" + Math.max(1, cell.photoIndex);
                }
            } catch (RuntimeException ignored) {
            }
            return cell.url;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int cellSize = Math.max(1, (width - gapPixels * (COLUMN_COUNT - 1)) / COLUMN_COUNT);
            int rowCount = (getChildCount() + COLUMN_COUNT - 1) / COLUMN_COUNT;
            int height = rowCount == 0
                    ? 0
                    : rowCount * cellSize + gapPixels * (rowCount - 1);

            for (int index = 0; index < getChildCount(); index++) {
                getChildAt(index).measure(
                        MeasureSpec.makeMeasureSpec(cellSize, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(cellSize, MeasureSpec.EXACTLY)
                );
            }

            setMeasuredDimension(
                    resolveSize(width, widthMeasureSpec),
                    resolveSize(height, heightMeasureSpec)
            );
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;
            int cellSize = Math.max(1, (width - gapPixels * (COLUMN_COUNT - 1)) / COLUMN_COUNT);

            for (int index = 0; index < getChildCount(); index++) {
                int column = index % COLUMN_COUNT;
                int row = index / COLUMN_COUNT;
                int childLeft = column * (cellSize + gapPixels);
                int childTop = row * (cellSize + gapPixels);
                getChildAt(index).layout(
                        childLeft,
                        childTop,
                        childLeft + cellSize,
                        childTop + cellSize
                );
            }
        }
    }

    private static final class GalleryCell {
        final String url;
        final Object timelineItem;
        final int photoIndex;

        GalleryCell(String url, Object timelineItem, int photoIndex) {
            this.url = url;
            this.timelineItem = timelineItem;
            this.photoIndex = photoIndex;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof GalleryCell cell)) return false;
            return photoIndex == cell.photoIndex
                    && url.equals(cell.url)
                    && timelineItem == cell.timelineItem;
        }

        @Override
        public int hashCode() {
            int result = url.hashCode();
            result = 31 * result + System.identityHashCode(timelineItem);
            result = 31 * result + photoIndex;
            return result;
        }
    }

    private static List<GalleryCell> extractCells(List<?> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();

        List<GalleryCell> cells = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Object item : items) {
            if (item == null) continue;
            List<String> urls = orderedMediaUrls(item.toString());
            int photoIndex = 0;
            for (String url : urls) {
                photoIndex++;
                if (!seen.add(url)) continue;
                cells.add(new GalleryCell(url, item, photoIndex));
                if (cells.size() >= 2000) return cells;
            }
        }
        return cells;
    }

    private static List<String> orderedMediaUrls(String value) {
        List<String> urls = new ArrayList<>();
        collectOrdered(value, "MediaContentImage(", "imageUrl=", urls);
        collectOrdered(value, "MediaContentVideo(", "imageUrl=", urls);
        collectOrdered(value, "MediaContentGif(", "previewUrl=", urls);
        if (!urls.isEmpty()) return urls;
        collectOrdered(value, "mediaContent", "imageUrl=", urls);
        return urls;
    }

    private static void collectOrdered(String value, String mediaMarker, String fieldMarker, List<String> urls) {
        int searchOffset = 0;
        while (true) {
            int mediaStart = value.indexOf(mediaMarker, searchOffset);
            if (mediaStart < 0) return;

            int fieldStart = value.indexOf(fieldMarker, mediaStart + mediaMarker.length());
            if (fieldStart < 0) return;

            int valueStart = fieldStart + fieldMarker.length();
            int comma = value.indexOf(',', valueStart);
            int close = value.indexOf(')', valueStart);
            int valueEnd = comma < 0 ? close : close < 0 ? comma : Math.min(comma, close);
            if (valueEnd < 0) return;

            String url = value.substring(valueStart, valueEnd).trim();
            if ((url.startsWith("http://") || url.startsWith("https://")) && !urls.contains(trimUrl(url))) {
                urls.add(trimUrl(url));
            }
            searchOffset = valueEnd + 1;
        }
    }

    private static String trimUrl(String url) {
        int end = url.length();
        while (end > 0) {
            char character = url.charAt(end - 1);
            if (character == '"' || character == '\'' || character == ']') {
                end--;
                continue;
            }
            break;
        }
        return url.substring(0, end);
    }

    private static String thumbnailUrl(String url) {
        try {
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
        } catch (RuntimeException ignored) {
            return url;
        }
    }
}
