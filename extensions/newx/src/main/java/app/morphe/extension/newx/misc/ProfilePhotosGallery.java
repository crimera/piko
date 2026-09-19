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

import java.lang.reflect.Constructor;
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

    public static Function1<Object, Object> createFactory(
            List<?> items,
            Object callback,
            float topPaddingDp,
            float bottomPaddingDp
    ) {
        return new GalleryFactory(items, callback, topPaddingDp, bottomPaddingDp);
    }

    public static Function1<Object, Object> createUpdater(
            List<?> items,
            Object callback,
            float topPaddingDp,
            float bottomPaddingDp
    ) {
        return new GalleryUpdater(items, callback, topPaddingDp, bottomPaddingDp);
    }

    private static final class GalleryFactory implements Function1<Object, Object> {
        private final List<?> items;
        private final Object callback;
        private final float topPaddingDp;
        private final float bottomPaddingDp;

        GalleryFactory(List<?> items, Object callback, float topPaddingDp, float bottomPaddingDp) {
            this.items = items;
            this.callback = callback;
            this.topPaddingDp = topPaddingDp;
            this.bottomPaddingDp = bottomPaddingDp;
        }

        @Override
        public Object invoke(Object context) {
            return new GalleryView(
                    (Context) context,
                    items,
                    callback,
                    topPaddingDp,
                    bottomPaddingDp
            );
        }
    }

    private static final class GalleryUpdater implements Function1<Object, Object> {
        private final List<?> items;
        private final Object callback;
        private final float topPaddingDp;
        private final float bottomPaddingDp;

        GalleryUpdater(List<?> items, Object callback, float topPaddingDp, float bottomPaddingDp) {
            this.items = items;
            this.callback = callback;
            this.topPaddingDp = topPaddingDp;
            this.bottomPaddingDp = bottomPaddingDp;
        }

        @Override
        public Object invoke(Object view) {
            if (view instanceof GalleryView galleryView) {
                galleryView.update(items, callback, topPaddingDp, bottomPaddingDp);
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
        private List<?> currentItems;
        private int currentItemCount = -1;
        private boolean loadMoreInFlight = false;
        private long lastLoadMoreTime = 0;
        GalleryView(
                Context context,
                List<?> items,
                Object callback,
                float topPaddingDp,
                float bottomPaddingDp
        ) {
            // The target APK retains this constructor shape. It initializes the nested-scrolling
            // helper before enabling nested scrolling in NestedScrollView's constructor.
            super(context, null);
            this.callback = callback;
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

        void update(List<?> items, Object callback, float topPaddingDp, float bottomPaddingDp) {
            this.callback = callback;
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
            setLoadingMore(true);
            boolean dispatched = requestLoadMore(callback);
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
            loadMoreInFlight = loading;
            loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    public static boolean requestLoadMore(Object callback) {
        if (callback == null) return false;
        try {
            Object urtComponent = findUrtComponent(callback);
            if (urtComponent == null) {
                NewXLogger.printInfo(() -> "[PikoNewX][PhotosGallery] could not find UrtComponent from " + callback.getClass().getName());
                return false;
            }

            Object bottomPaginator = getBottomPaginator(urtComponent);
            if (bottomPaginator == null) {
                NewXLogger.printInfo(() -> "[PikoNewX][PhotosGallery] could not find bottom paginator from " + urtComponent.getClass().getName());
                return false;
            }

            return triggerBottomPaging(bottomPaginator);
        } catch (Throwable t) {
            NewXLogger.printInfo(() -> "[PikoNewX][PhotosGallery] requestLoadMore failed: " + t.getMessage());
            return false;
        }
    }

    private static Object findUrtComponent(Object callback) {
        if (callback == null) return null;
        Class<?> clazz = callback.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(callback);
                if (value != null && isUrtComponent(value)) {
                    return value;
                }
            } catch (Throwable ignored) {
            }
        }
        if (isUrtComponent(callback)) {
            return callback;
        }
        return null;
    }

    private static boolean isUrtComponent(Object obj) {
        if (obj == null) return false;
        Class<?> clazz = obj.getClass();
        String name = clazz.getName();
        if (name.startsWith("com.x.urt.")) {
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals("p") && m.getParameterTypes().length == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Object getBottomPaginator(Object urtComponent) {
        Class<?> clazz = urtComponent.getClass();
        try {
            Method pMethod = clazz.getMethod("p");
            pMethod.setAccessible(true);
            Object paginator = pMethod.invoke(urtComponent);
            if (paginator != null) return paginator;
        } catch (Throwable ignored) {
        }

        for (Field field : clazz.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object val = field.get(urtComponent);
                if (val != null && val.getClass().getName().contains("paging.bottom")) {
                    return val;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean triggerBottomPaging(Object bottomPaginator) {
        Class<?> clazz = bottomPaginator.getClass();

        // Check if bottom pagination has terminated
        try {
            Method eMethod = clazz.getMethod("e");
            eMethod.setAccessible(true);
            Object terminated = eMethod.invoke(bottomPaginator);
            if (Boolean.TRUE.equals(terminated)) {
                NewXLogger.printInfo(() -> "[PikoNewX][PhotosGallery] bottom pagination already terminated");
                return false;
            }
        } catch (Throwable ignored) {
        }

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals("a") && method.getParameterTypes().length == 1) {
                method.setAccessible(true);
                Class<?> paramType = method.getParameterTypes()[0];
                Object event = createPagingEvent(paramType, clazz.getClassLoader());
                if (event != null) {
                    try {
                        method.invoke(bottomPaginator, event);
                        NewXLogger.printInfo(() -> "[PikoNewX][PhotosGallery] triggered bottom pagination");
                        return true;
                    } catch (Throwable t) {
                        NewXLogger.printInfo(() -> "[PikoNewX][PhotosGallery] invoke pagination failed: " + t.getMessage());
                    }
                }
            }
        }
        return false;
    }

    private static Object createPagingEvent(Class<?> targetInterface, ClassLoader loader) {
        String[] candidateNames = new String[]{
                "com.x.urt.paging.e",
                "com.x.urt.paging.d",
                "com.x.urt.paging.f",
                "com.x.urt.paging.b",
                "com.x.urt.paging.a"
        };
        for (String name : candidateNames) {
            try {
                Class<?> cls = Class.forName(name, false, loader);
                if (targetInterface.isAssignableFrom(cls)) {
                    for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
                        ctor.setAccessible(true);
                        Class<?>[] pTypes = ctor.getParameterTypes();
                        if (pTypes.length == 0) {
                            return ctor.newInstance();
                        } else if (pTypes.length == 3 && pTypes[1] == int.class && pTypes[2] == int.class) {
                            return ctor.newInstance(null, 0, 0);
                        } else if (pTypes.length == 1 && pTypes[0] == int.class) {
                            return ctor.newInstance(0);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static final class GalleryGrid extends ViewGroup {
        private static final int COLUMN_COUNT = 3;
        private static final int GAP_DP = 2;
        private final int gapPixels;
        private List<GalleryCell> cells = Collections.emptyList();

        GalleryGrid(Context context) {
            super(context);
            gapPixels = Math.max(1, Math.round(GAP_DP * getResources().getDisplayMetrics().density));
            setBackgroundColor(Color.BLACK);
            setWillNotDraw(true);
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
