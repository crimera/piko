package app.morphe.extension.newx.misc;

import android.app.Activity;
import app.morphe.extension.shared.Utils;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.PixelCopy;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.newx.utils.NewXUtils;
import app.morphe.extension.newx.utils.ToStringParser;
import kotlin.jvm.functions.Function1;

/** Bridges NewX's rendered Compose post row to an Android image share intent. */
public final class NewXShareImageHandler {
    private static final String DEBUG_TAG = "DEBUG-share-image";
    private static final String OPTION_NAME = NewXPostOptionActions.SHARE_IMAGE_ACTION;
    private static final String SETTING_ID = "newx.content.share_post_as_image";
    private static final String URT_POST_CLASS = "com.x.models.timelines.items.UrtTimelinePost";
    private static final String SPATIAL_BOUNDS_CLASS = "androidx.compose.ui.spatial.c";
    private static final String INT_RECT_CLASS = "androidx.compose.ui.unit.k";
    private static final int MAX_CAPTURE_PIXELS = 16_000_000;
    private static final int MAX_RENDERED_BOUNDS = 128;
    private static volatile Handler mainHandler;
    private static final Object RENDERED_BOUNDS_LOCK = new Object();
    private static final Map<String, Rect> RENDERED_BOUNDS =
            new LinkedHashMap<String, Rect>(MAX_RENDERED_BOUNDS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Rect> eldest) {
                    return size() > MAX_RENDERED_BOUNDS;
                }
            };
    private static final Map<Class<?>, BoundsReader> BOUNDS_READERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field[]> RECTANGLE_FIELDS = new ConcurrentHashMap<>();
    private static final Function1<Object, Object> NO_POSITION_CALLBACK = coordinates -> null;
    private static volatile boolean boundsAccessorUnavailable;

    private NewXShareImageHandler() {
    }

    public static java.util.List<?> addOption(java.util.List<?> groups) {
        return NewXPostOptions.addOption(groups, OPTION_NAME, isEnabled());
    }

    public static Function1<Object, Object> positionCallback(Object postIdentifier) {
        if (!isEnabled()) return NO_POSITION_CALLBACK;
        return createPositionCallback(identifierValue(postIdentifier));
    }

    /** Converts the resolved identifier through its model toString without reflective accessors. */
    public static Function1<Object, Object> positionCallbackFromIdentifier(Object postIdentifier) {
        if (!isEnabled() || postIdentifier == null) return NO_POSITION_CALLBACK;
        return createPositionCallback(String.valueOf(postIdentifier));
    }

    /** Avoids repeated reflective identifier decoding in the post renderer. */
    public static Function1<Object, Object> positionCallback(String postId) {
        if (!isEnabled()) return NO_POSITION_CALLBACK;
        return createPositionCallback(postId);
    }

    private static Function1<Object, Object> createPositionCallback(String postId) {
        String normalizedPostId = normalizePostId(postId);
        if (normalizedPostId == null) return NO_POSITION_CALLBACK;
        return registerRenderedPost(normalizedPostId);
    }

    public static boolean handleOptionAction(Object presenter, Object action) {
        if (!isShareImageAction(action)) return false;

        try {
            NewXUtils.PresenterData presenterData = NewXUtils.findPresenterData(presenter, URT_POST_CLASS);
            Context context = presenterData.getContext();
            Object post = presenterData.getValue();
            if (context == null || post == null) {
                Utils.showToastShort(app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_post_missing"));
                return true;
            }
            shareAsImage(context, post);
            return true;
        } catch (IllegalAccessException exception) {
            Utils.showToastShort(app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_post_missing"));
            return true;
        }
    }

    public static void shareAsImage(Context context, Object post) {
        if (context == null || post == null) return;

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler().post(() -> shareAsImage(context, post));
            return;
        }

        Activity activity = NewXUtils.findUsableActivity(context);
        if (activity == null) {
            NewXLogger.printException(
                    () -> DEBUG_TAG + ": No activity for context " + context.getClass().getName()
            );
            Utils.showToastShort(app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_capture_failed"));
            return;
        }

        String fileName;
        try {
            if (postId(post) == null) throw new ReflectiveOperationException("Post ID is empty");
            fileName = shareImageFileName(post);
        } catch (ReflectiveOperationException exception) {
            Utils.showToastShort(app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_post_identify_failed"));
            return;
        }
        View decorView = activity.getWindow().getDecorView();
        decorView.postOnAnimation(() -> decorView.postOnAnimation(
                () -> captureRenderedPost(activity, post, fileName)
        ));
    }

    public static String labelFor(Object action, Object originalLabel) {
        if (isShareImageAction(action)) return app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_share_image_label");
        return originalLabel instanceof String ? (String) originalLabel : null;
    }

    public static boolean usesIcon(Object action) {
        return isShareImageAction(action);
    }

    private static void captureRenderedPost(Activity activity, Object post, String fileName) {
        View decorView = activity.getWindow().getDecorView();
        if (!decorView.isAttachedToWindow()) {
            Utils.showToastShort(app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_post_not_visible"));
            return;
        }

        String postId;
        try {
            postId = postId(post);
        } catch (ReflectiveOperationException exception) {
            NewXLogger.printException(() -> DEBUG_TAG + ": Could not resolve post ID at capture", exception);
            Utils.showToastShort(app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_post_not_visible"));
            return;
        }
        if (postId == null) {
            Utils.showToastShort(app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_post_not_visible"));
            return;
        }
        NewXLogger.printInfo(
                () -> DEBUG_TAG + ": post class=" + post.getClass().getName() +
                        " id=" + postId + " source=" + NewXUtils.sourcePostId(post)
        );
        if (boundsAccessorUnavailable) {
            NewXLogger.printInfo(() -> DEBUG_TAG + ": Window bounds accessor is unavailable");
            Utils.showToastShort(app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_post_not_visible"));
            return;
        }

        Rect bounds = renderedBounds(postId);
        if (bounds == null) {
            NewXLogger.printException(() -> DEBUG_TAG + ": No resolved bounds for post " + postId);
            Utils.showToastShort(app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_post_not_visible"));
            return;
        }
        final Rect selectedBounds = bounds;
        ParentResolution parent = resolveParent(post);
        NewXLogger.printInfo(
                () -> DEBUG_TAG + ": canonical=" + parent.canonicalClass +
                        " parentId=" + parent.parentId + " path=" + parent.path
        );
        Rect parentBounds = renderedBounds(parent.parentId);
        NewXLogger.printInfo(
                () -> DEBUG_TAG + ": map size=" + renderedBoundsSize() +
                        " selectedBounds=" + boundsDescription(selectedBounds) +
                " parentKey=" + parent.parentId +
                " parentBounds=" + boundsDescription(parentBounds)
        );
        int adjacencySlop = adjacencySlopPx(decorView);
        int overlap = horizontalOverlap(parentBounds, selectedBounds);
        boolean unionParent = canUnionWithParent(parentBounds, selectedBounds, adjacencySlop);
        String windowBounds = boundsDescription(selectedBounds);
        if (unionParent) {
            bounds = union(selectedBounds, parentBounds);
        }
        final Rect captureBounds = bounds;
        NewXLogger.printInfo(
                () -> DEBUG_TAG + ": adjacency parent.bottom=" +
                        (parentBounds == null ? "null" : parentBounds.bottom) +
                        " selected.top=" + selectedBounds.top +
                        " gap=" + (parentBounds == null ? "null" :
                                selectedBounds.top - parentBounds.bottom) +
                        " overlapPx=" + overlap +
                        " slopPx=" + adjacencySlop +
                        " union=" + unionParent +
                        " captureBounds=" + boundsDescription(captureBounds) +
                        " window=" + decorView.getWidth() + "x" + decorView.getHeight() +
                        " windowBounds=" + windowBounds
        );
        if (captureBounds.left < 0 || captureBounds.top < 0 ||
                captureBounds.right > decorView.getWidth() ||
                captureBounds.bottom > decorView.getHeight()) {
            Utils.showToastShort(app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_post_show_entire"));
            return;
        }

        long pixelCount = (long) captureBounds.width() * captureBounds.height();
        if (pixelCount <= 0 || pixelCount > MAX_CAPTURE_PIXELS) {
            Utils.showToastShort(app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_capture_too_large"));
            return;
        }

        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(
                    captureBounds.width(), captureBounds.height(), Bitmap.Config.ARGB_8888
            );
        } catch (RuntimeException | OutOfMemoryError error) {
            Utils.showToastShort(app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_capture_allocate"));
            return;
        }

        try {
            PixelCopy.request(
                    activity.getWindow(),
                    captureBounds,
                    bitmap,
                    result -> finishCapture(activity, bitmap, fileName, postId, result),
                    mainHandler()
            );
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> DEBUG_TAG + ": PixelCopy request failed", exception);
            bitmap.recycle();
            Utils.showToastShort(app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_capture_failed"));
        }
    }

    private static void finishCapture(
            Context context,
            Bitmap bitmap,
            String fileName,
            String postId,
            int result
    ) {
        if (result != PixelCopy.SUCCESS) {
            NewXLogger.printException(
                    () -> DEBUG_TAG + ": PixelCopy result=" + result + " for post " + postId
            );
            bitmap.recycle();
            Utils.showToastShort(app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_capture_failed"));
            return;
        }

        Uri uri;
        try {
            uri = saveImage(context, bitmap, fileName);
        } finally {
            bitmap.recycle();
        }
        if (uri == null) {
            Utils.showToastShort(app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_capture_save"));
            return;
        }
        shareImage(context, uri);
    }

    private static Rect renderedBounds(String postId) {
        String normalizedPostId = normalizePostId(postId);
        if (normalizedPostId == null) return null;
        synchronized (RENDERED_BOUNDS_LOCK) {
            Rect bounds = RENDERED_BOUNDS.get(normalizedPostId);
            return bounds == null ? null : new Rect(bounds);
        }
    }

    private static ParentResolution resolveParent(Object post) {
        Object canonicalPost = null;
        try {
            canonicalPost = findCanonicalPost(findPostResult(post));
            if (canonicalPost != null) {
                ParentIdResolution canonicalParent = parentIdFromCanonical(canonicalPost);
                if (canonicalParent != null) {
                    return new ParentResolution(
                            normalizePostId(canonicalParent.id),
                            canonicalParent.path,
                            canonicalPost.getClass().getName()
                    );
                }
            }
        } catch (RuntimeException ignored) {
            // The wrapper label fallback below keeps reflection failures fail-closed.
        }

        String wrapperParentId = normalizePostId(
                ToStringParser.fieldValue(safeObjectString(post), "repliedPostId")
        );
        if (wrapperParentId != null) {
            return new ParentResolution(wrapperParentId, "label", canonicalClass(canonicalPost));
        }
        return new ParentResolution(null, "none", canonicalClass(canonicalPost));
    }

    private static Object findPostResult(Object post) {
        if (post == null) return null;
        Object namedResult = readInstanceField(post, "a");
        if (namedResult != null) return namedResult;
        return firstModelFieldValue(post);
    }

    private static Object findCanonicalPost(Object postResult) {
        if (isCanonicalPost(postResult)) return postResult;
        return firstCanonicalFieldValue(postResult);
    }

    private static ParentIdResolution parentIdFromCanonical(Object canonicalPost) {
        String labelId = ToStringParser.fieldValue(
                safeObjectString(canonicalPost), "repliedPostId"
        );
        if (labelId == null) return null;

        for (Class<?> type = canonicalPost.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                        (field.getType() != Long.class && field.getType() != long.class)) {
                    continue;
                }
                Object value = readField(canonicalPost, field);
                if (value != null && labelId.equals(String.valueOf(value))) {
                    return new ParentIdResolution(String.valueOf(value), "field");
                }
            }
        }
        return new ParentIdResolution(labelId, "label");
    }

    private static String canonicalClass(Object canonicalPost) {
        return canonicalPost == null ? "null" : canonicalPost.getClass().getName();
    }

    private static String safeObjectString(Object value) {
        if (value == null) return null;
        try {
            return String.valueOf(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Object firstModelFieldValue(Object target) {
        if (target == null) return null;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                        !field.getType().getName().startsWith("com.x.models.")) {
                    continue;
                }
                Object value = readField(target, field);
                if (value != null) return value;
            }
        }
        return null;
    }

    private static Object firstCanonicalFieldValue(Object target) {
        if (target == null) return null;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                Object value = readField(target, field);
                if (isCanonicalPost(value)) return value;
            }
        }
        return null;
    }

    private static boolean isCanonicalPost(Object value) {
        if (value == null) return false;
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            if ("com.x.models.t0".equals(type.getName())) return true;
        }
        try {
            return String.valueOf(value).startsWith("CanonicalPost(");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static Object readInstanceField(Object target, String fieldName) {
        if (target == null) return null;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) return null;
                return readField(target, field);
            } catch (NoSuchFieldException exception) {
                // Continue through the wrapper hierarchy.
            } catch (RuntimeException exception) {
                return null;
            }
        }
        return null;
    }

    private static Object readField(Object target, Field field) {
        try {
            field.setAccessible(true);
            if (field.getType() == long.class) return field.getLong(target);
            return field.get(target);
        } catch (IllegalAccessException | RuntimeException exception) {
            return null;
        }
    }

    private static int renderedBoundsSize() {
        synchronized (RENDERED_BOUNDS_LOCK) {
            return RENDERED_BOUNDS.size();
        }
    }

    private static int adjacencySlopPx(View decorView) {
        float density = decorView.getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(8f * density));
    }

    private static int horizontalOverlap(Rect parent, Rect selected) {
        if (parent == null || selected == null) return 0;
        return Math.max(0, Math.min(parent.right, selected.right) -
                Math.max(parent.left, selected.left));
    }

    private static boolean canUnionWithParent(Rect parent, Rect selected, int slopPx) {
        if (parent == null || selected == null || parent.width() <= 0 || parent.height() <= 0 ||
                selected.width() <= 0 || selected.height() <= 0) {
            return false;
        }
        if (parent.bottom > selected.top + slopPx) return false;

        int overlap = horizontalOverlap(parent, selected);
        return overlap > 0 && (long) overlap * 2 > selected.width();
    }

    private static String boundsDescription(Rect bounds) {
        return bounds == null
                ? "null"
                : bounds.left + "," + bounds.top + "," + bounds.right + "," + bounds.bottom;
    }

    private static final class ParentResolution {
        private final String parentId;
        private final String path;
        private final String canonicalClass;

        private ParentResolution(String parentId, String path, String canonicalClass) {
            this.parentId = parentId;
            this.path = path;
            this.canonicalClass = canonicalClass;
        }
    }

    private static final class ParentIdResolution {
        private final String id;
        private final String path;

        private ParentIdResolution(String id, String path) {
            this.id = id;
            this.path = path;
        }
    }

    private static Rect union(Rect first, Rect second) {
        return new Rect(
                Math.min(first.left, second.left),
                Math.min(first.top, second.top),
                Math.max(first.right, second.right),
                Math.max(first.bottom, second.bottom)
        );
    }

    private static PositionCallback registerRenderedPost(String postId) {
        return new PositionCallback(postId);
    }

    private static void registerRenderedBounds(String postId, Rect bounds) {
        String normalizedPostId = normalizePostId(postId);
        if (normalizedPostId == null || bounds == null) return;
        synchronized (RENDERED_BOUNDS_LOCK) {
            Rect previous = RENDERED_BOUNDS.get(normalizedPostId);
            if (bounds.equals(previous)) return;
            RENDERED_BOUNDS.put(normalizedPostId, new Rect(bounds));
        }
    }

    private static BoundsReader boundsReader(Class<?> layoutBoundsClass) {
        BoundsReader reader = BOUNDS_READERS.get(layoutBoundsClass);
        if (reader != null) return reader;
        synchronized (BOUNDS_READERS) {
            reader = BOUNDS_READERS.get(layoutBoundsClass);
            if (reader != null) return reader;
            reader = new BoundsReader(layoutBoundsClass);
            BOUNDS_READERS.put(layoutBoundsClass, reader);
            return reader;
        }
    }

    private static Rect resolveWindowBounds(Object layoutBounds) {
        if (layoutBounds == null) return null;
        return boundsReader(layoutBounds.getClass()).read(layoutBounds);
    }

    private static Rect readIntRect(Object value) throws IllegalAccessException {
        if (value == null) return null;

        if (!INT_RECT_CLASS.equals(value.getClass().getName())) return null;

        Class<?> valueClass = value.getClass();
        Field[] fields = RECTANGLE_FIELDS.get(valueClass);
        if (fields == null) {
            synchronized (RECTANGLE_FIELDS) {
                fields = RECTANGLE_FIELDS.get(valueClass);
                if (fields == null) {
                    fields = rectangleFields(valueClass);
                    RECTANGLE_FIELDS.put(valueClass, fields);
                }
            }
        }
        if (fields.length == 0) return null;

        return new Rect(
                fields[0].getInt(value),
                fields[1].getInt(value),
                fields[2].getInt(value),
                fields[3].getInt(value)
        );
    }

    private static Field[] rectangleFields(Class<?> type) {
        Field[] declaredFields = type.getDeclaredFields();
        ArrayList<Field> coordinates = new ArrayList<>(4);
        for (Field field : declaredFields) {
            if (field.getType() != int.class ||
                    java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            coordinates.add(field);
        }
        return coordinates.size() == 4
                ? coordinates.toArray(new Field[0])
                : new Field[0];
    }

    private static final class BoundsReader {
        private final Method accessor;

        private BoundsReader(Class<?> layoutBoundsClass) {
            accessor = resolveAccessor(layoutBoundsClass);
        }

        private Rect read(Object layoutBounds) {
            if (accessor == null) return null;
            try {
                Rect bounds = readIntRect(accessor.invoke(layoutBounds));
                if (bounds == null) boundsAccessorUnavailable = true;
                return bounds;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                boundsAccessorUnavailable = true;
                NewXLogger.printException(
                        () -> DEBUG_TAG + ": Could not read window bounds from " +
                                SPATIAL_BOUNDS_CLASS + ".c()",
                        exception
                );
                return null;
            }
        }

        private static Method resolveAccessor(Class<?> layoutBoundsClass) {
            if (!SPATIAL_BOUNDS_CLASS.equals(layoutBoundsClass.getName())) {
                boundsAccessorUnavailable = true;
                NewXLogger.printInfo(
                        () -> DEBUG_TAG + ": Unexpected bounds callback type " +
                                layoutBoundsClass.getName()
                );
                return null;
            }
            try {
                Method method = layoutBoundsClass.getMethod("c");
                if (method.getParameterCount() != 0 ||
                        !INT_RECT_CLASS.equals(method.getReturnType().getName())) {
                    boundsAccessorUnavailable = true;
                    NewXLogger.printInfo(
                            () -> DEBUG_TAG + ": Bounds accessor c() has unexpected signature"
                    );
                    return null;
                }
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                boundsAccessorUnavailable = true;
                NewXLogger.printException(
                        () -> DEBUG_TAG + ": Missing " + SPATIAL_BOUNDS_CLASS + ".c()",
                        exception
                );
                return null;
            }
        }
    }

    private static boolean isEnabled() {
        return SettingsRegistry.getBooleanOrDefault(SETTING_ID, false);
    }

    private static boolean isShareImageAction(Object action) {
        return NewXPostOptions.isAction(action, OPTION_NAME);
    }

    // Retained until rendered-UI capture passes device verification.
    @SuppressWarnings("unused")
    private static Bitmap renderPost(Object post) throws ReflectiveOperationException {
        final int width = 1080;
        final int padding = 72;
        Object author = NewXUtils.invoke(post, "getAuthor");
        Object canonicalPost = findCanonicalPost(findPostResult(post));
        String name = stringValue(NewXUtils.invoke(author, "getName"), "X user");
        String screenName = stringValue(NewXUtils.invoke(author, "getScreenName"), "");
        String text = stringValue(canonicalPost == null ? null : NewXUtils.invoke(canonicalPost, "getText"), "");

        TextPaint bodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(Color.rgb(15, 20, 25));
        bodyPaint.setTextSize(42f);
        bodyPaint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        StaticLayout body = StaticLayout.Builder.obtain(text, 0, text.length(), bodyPaint, width - (padding * 2))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build();

        int height = Math.max(420, padding * 2 + 118 + body.getHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        Paint avatarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        avatarPaint.setColor(Color.rgb(29, 155, 240));
        canvas.drawCircle(padding + 42, padding + 42, 42, avatarPaint);

        Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setColor(Color.rgb(15, 20, 25));
        namePaint.setTextSize(38f);
        namePaint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        canvas.drawText(name, padding + 108, padding + 34, namePaint);

        Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setColor(Color.rgb(83, 100, 113));
        handlePaint.setTextSize(30f);
        canvas.drawText(screenName.isEmpty() ? "" : "@" + screenName, padding + 108, padding + 76, handlePaint);

        canvas.save();
        canvas.translate(padding, padding + 128);
        body.draw(canvas);
        canvas.restore();
        return bitmap;
    }

    static String shareImageFileName(String username, String postId) {
        return "tweet_" + safeFileSegment(username, "twitter") + "_" +
                safeFileSegment(postId, "post") + ".png";
    }

    private static String shareImageFileName(Object post) {
        return shareImageFileName(
                NewXUtils.sourceUsername(post),
                NewXUtils.sourcePostId(post)
        );
    }

    private static String safeFileSegment(String value, String fallback) {
        if (value == null) return fallback;

        String sanitized = value.trim().replaceFirst("^@", "")
                .replaceAll("[^A-Za-z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._-]+|[._-]+$", "");
        return sanitized.isEmpty() ? fallback : sanitized;
    }

    private static Handler mainHandler() {
        Handler handler = mainHandler;
        if (handler != null) return handler;
        handler = new Handler(Looper.getMainLooper());
        mainHandler = handler;
        return handler;
    }

    private static Uri saveImage(Context context, Bitmap bitmap, String fileName) {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");

        String selection;
        String[] selectionArguments;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String relativePath = Environment.DIRECTORY_PICTURES + "/Piko/";
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                    MediaStore.MediaColumns.RELATIVE_PATH + "=?";
            selectionArguments = new String[]{fileName, relativePath};
        } else {
            File directory = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Piko"
            );
            if (!directory.exists() && !directory.mkdirs()) return null;
            String path = new File(directory, fileName).getAbsolutePath();
            values.put(MediaStore.MediaColumns.DATA, path);
            selection = MediaStore.MediaColumns.DATA + "=?";
            selectionArguments = new String[]{path};
        }

        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri uri;
        try {
            resolver.delete(collection, selection, selectionArguments);
            uri = resolver.insert(collection, values);
        } catch (RuntimeException exception) {
            return null;
        }
        if (uri == null) return null;

        boolean saved = false;
        try (java.io.OutputStream output = resolver.openOutputStream(uri, "w")) {
            saved = output != null && bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (java.io.IOException | RuntimeException exception) {
            saved = false;
        }
        if (!saved) {
            resolver.delete(uri, null, null);
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
        }
        return uri;
    }

    private static void shareImage(Context context, Uri uri) {
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("image", uri));

        Intent chooser = Intent.createChooser(intent, app.morphe.extension.newx.settings.NewXStrings.str("piko_newx_ui_share_image_label"));
        if (!(context instanceof Activity)) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooser);
    }

    private static String postId(Object post) throws ReflectiveOperationException {
        return identifierValue(NewXUtils.invoke(post, "getId"));
    }

    private static String identifierValue(Object identifier) {
        return NewXUtils.identifierToString(identifier);
    }

    private static String normalizePostId(String postId) {
        if (postId == null) return null;
        String normalized = postId.trim();
        return normalized.isEmpty() || "0".equals(normalized) ? null : normalized;
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) return fallback;
        String string = String.valueOf(value).trim();
        return string.isEmpty() ? fallback : string;
    }

    private static final class PositionCallback implements Function1<Object, Object> {
        private final String postId;

        private PositionCallback(String postId) {
            this.postId = postId;
        }

        @Override
        public Object invoke(Object layoutBounds) {
            Rect bounds = resolveWindowBounds(layoutBounds);
            if (bounds != null) registerRenderedBounds(postId, bounds);
            return null;
        }
    }
}
