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
                Utils.showToastShort("Could not find the selected post");
                return true;
            }
            shareAsImage(context, post);
            return true;
        } catch (IllegalAccessException exception) {
            Utils.showToastShort("Could not find the selected post");
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
            Utils.showToastShort("Could not capture the rendered post");
            return;
        }

        String fileName;
        try {
            if (postId(post) == null) throw new ReflectiveOperationException("Post ID is empty");
            fileName = shareImageFileName(post);
        } catch (ReflectiveOperationException exception) {
            Utils.showToastShort("Could not identify the selected post");
            return;
        }
        View decorView = activity.getWindow().getDecorView();
        decorView.postOnAnimation(() -> decorView.postOnAnimation(
                () -> captureRenderedPost(activity, post, fileName)
        ));
    }

    public static String labelFor(Object action, Object originalLabel) {
        if (isShareImageAction(action)) return "Share Tweet as Image";
        return originalLabel instanceof String ? (String) originalLabel : null;
    }

    public static boolean usesIcon(Object action) {
        return isShareImageAction(action);
    }

    private static void captureRenderedPost(Activity activity, Object post, String fileName) {
        View decorView = activity.getWindow().getDecorView();
        if (!decorView.isAttachedToWindow()) {
            Utils.showToastShort("Post is no longer rendered");
            return;
        }

        String postId;
        try {
            postId = postId(post);
        } catch (ReflectiveOperationException exception) {
            NewXLogger.printException(() -> DEBUG_TAG + ": Could not resolve post ID at capture", exception);
            Utils.showToastShort("Post is no longer rendered");
            return;
        }
        if (postId == null) {
            Utils.showToastShort("Post is no longer rendered");
            return;
        }
        if (boundsAccessorUnavailable) {
            NewXLogger.printInfo(() -> DEBUG_TAG + ": Window bounds accessor is unavailable");
            Utils.showToastShort("Post is no longer rendered");
            return;
        }

        Rect bounds = renderedBounds(postId);
        if (bounds == null) {
            NewXLogger.printException(() -> DEBUG_TAG + ": No resolved bounds for post " + postId);
            Utils.showToastShort("Post is no longer rendered");
            return;
        }
        Rect parentBounds = renderedBounds(resolveParentId(post));
        if (canUnionWithParent(parentBounds, bounds)) {
            bounds = union(bounds, parentBounds);
        }
        final Rect captureBounds = bounds;
        NewXLogger.printInfo(
                () -> DEBUG_TAG + ": Requesting post " + postId + " bounds=" + captureBounds
                        + " window=" + decorView.getWidth() + "x" + decorView.getHeight()
        );
        if (captureBounds.left < 0 || captureBounds.top < 0 ||
                captureBounds.right > decorView.getWidth() ||
                captureBounds.bottom > decorView.getHeight()) {
            Utils.showToastShort("Make the entire post visible before sharing");
            return;
        }

        long pixelCount = (long) captureBounds.width() * captureBounds.height();
        if (pixelCount <= 0 || pixelCount > MAX_CAPTURE_PIXELS) {
            Utils.showToastShort("Rendered post is too large to capture");
            return;
        }

        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(
                    captureBounds.width(), captureBounds.height(), Bitmap.Config.ARGB_8888
            );
        } catch (RuntimeException | OutOfMemoryError error) {
            Utils.showToastShort("Could not allocate the post image");
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
            Utils.showToastShort("Could not capture the rendered post");
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
            Utils.showToastShort("Could not capture the rendered post");
            return;
        }

        Uri uri;
        try {
            uri = saveImage(context, bitmap, fileName);
        } finally {
            bitmap.recycle();
        }
        if (uri == null) {
            Utils.showToastShort("Could not save the post image");
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

    private static String resolveParentId(Object post) {
        Object canonicalPost = canonicalPost(post);
        if (canonicalPost == null) return null;

        String reflectedId = reflectedParentId(canonicalPost);
        if (reflectedId != null) return normalizePostId(reflectedId);

        String labelId = ToStringParser.fieldValue(
                String.valueOf(canonicalPost), "repliedPostId"
        );
        return normalizePostId(labelId);
    }

    private static Object canonicalPost(Object post) {
        Object postResult = NewXUtils.invokeIfPresent(post, "getPostResult");
        return NewXUtils.invokeIfPresent(postResult, "getCanonicalPost");
    }

    private static String reflectedParentId(Object canonicalPost) {
        Object getterValue = NewXUtils.invokeIfPresent(canonicalPost, "getRepliedPostId");
        String getterId = identifierValue(getterValue);
        if (getterId != null) return getterId;

        Object namedFieldValue = fieldValue(canonicalPost, "repliedPostId");
        String namedFieldId = identifierValue(namedFieldValue);
        if (namedFieldId != null) return namedFieldId;

        String labelId = ToStringParser.fieldValue(
                String.valueOf(canonicalPost), "repliedPostId"
        );
        if (labelId == null) return null;

        for (Class<?> type = canonicalPost.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != Long.class ||
                        java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(canonicalPost);
                    if (value != null && labelId.equals(String.valueOf(value))) {
                        return String.valueOf(value);
                    }
                } catch (IllegalAccessException | RuntimeException ignored) {
                    // The toString label remains the safe fallback below.
                }
            }
        }
        return null;
    }

    private static Object fieldValue(Object target, String fieldName) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                if (field.getType() != Long.class ||
                        java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    return null;
                }
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException exception) {
                // Continue through the model hierarchy.
            } catch (IllegalAccessException | RuntimeException exception) {
                return null;
            }
        }
        return null;
    }

    private static boolean canUnionWithParent(Rect parent, Rect selected) {
        if (parent == null || selected == null || parent.width() <= 0 || parent.height() <= 0 ||
                selected.width() <= 0 || selected.height() <= 0) {
            return false;
        }
        if (parent.bottom > selected.top + 4) return false;

        int overlap = Math.min(parent.right, selected.right) -
                Math.max(parent.left, selected.left);
        return overlap > 0 && (long) overlap * 2 > selected.width();
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
        Object postResult = NewXUtils.invoke(post, "getPostResult");
        Object canonicalPost = postResult == null ? null : NewXUtils.invoke(postResult, "getCanonicalPost");
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

        Intent chooser = Intent.createChooser(intent, "Share Tweet as Image");
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
        return normalized.isEmpty() ? null : normalized;
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
