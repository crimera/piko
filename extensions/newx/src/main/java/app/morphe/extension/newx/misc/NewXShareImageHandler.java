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
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.newx.utils.NewXUtils;
import kotlin.jvm.functions.Function1;

/** Bridges NewX's rendered Compose post row to an Android image share intent. */
public final class NewXShareImageHandler {
    private static final String DEBUG_TAG = "DEBUG-share-image";
    private static final String OPTION_NAME = NewXPostOptionActions.SHARE_IMAGE_ACTION;
    private static final String SETTING_ID = "newx.content.share_post_as_image";
    private static final String URT_POST_CLASS = "com.x.models.timelines.items.UrtTimelinePost";
    private static final int MAX_CAPTURE_PIXELS = 16_000_000;
    private static final int MAX_RENDERED_POSTS = 128;
    private static volatile Handler mainHandler;
    private static final Object RENDERED_POSTS_LOCK = new Object();
    private static final Map<String, WeakReference<PositionCallback>> RENDERED_POSTS =
            new LinkedHashMap<>(MAX_RENDERED_POSTS, 0.75f, true);
    private static final Map<String, Rect> RENDERED_BOUNDS =
            new LinkedHashMap<>(MAX_RENDERED_POSTS, 0.75f, true);
    private static final Map<Class<?>, BoundsReader> BOUNDS_READERS = new ConcurrentHashMap<>();
    private static final Function1<Object, Object> NO_POSITION_CALLBACK = coordinates -> null;

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
        if (postId == null) return NO_POSITION_CALLBACK;
        String normalizedPostId = postId.trim();
        if (normalizedPostId.isEmpty()) return NO_POSITION_CALLBACK;
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

        String id;
        String fileName;
        try {
            id = postId(post);
            fileName = shareImageFileName(post);
        } catch (ReflectiveOperationException exception) {
            Utils.showToastShort("Could not identify the selected post");
            return;
        }
        if (id == null) {
            Utils.showToastShort("Could not identify the selected post");
            return;
        }

        View decorView = activity.getWindow().getDecorView();
        decorView.postOnAnimation(() -> decorView.postOnAnimation(
                () -> captureRenderedPost(activity, id, fileName)
        ));
    }

    public static String labelFor(Object action, Object originalLabel) {
        if (isShareImageAction(action)) return "Share Tweet as Image";
        return originalLabel instanceof String ? (String) originalLabel : null;
    }

    public static boolean usesIcon(Object action) {
        return isShareImageAction(action);
    }

    private static void captureRenderedPost(Activity activity, String postId, String fileName) {
        View decorView = activity.getWindow().getDecorView();
        if (!decorView.isAttachedToWindow()) {
            Utils.showToastShort("Post is no longer rendered");
            return;
        }

        Rect bounds = renderedBounds(postId);
        if (bounds == null) {
            NewXLogger.printException(() -> DEBUG_TAG + ": No resolved bounds for post " + postId);
            Utils.showToastShort("Post is no longer rendered");
            return;
        }
        NewXLogger.printInfo(
                () -> DEBUG_TAG + ": Requesting post " + postId + " bounds=" + bounds
                        + " window=" + decorView.getWidth() + "x" + decorView.getHeight()
        );
        if (bounds.left < 0 || bounds.top < 0 || bounds.right > decorView.getWidth() || bounds.bottom > decorView.getHeight()) {
            Utils.showToastShort("Make the entire post visible before sharing");
            return;
        }

        long pixelCount = (long) bounds.width() * bounds.height();
        if (pixelCount <= 0 || pixelCount > MAX_CAPTURE_PIXELS) {
            Utils.showToastShort("Rendered post is too large to capture");
            return;
        }

        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);
        } catch (RuntimeException | OutOfMemoryError error) {
            Utils.showToastShort("Could not allocate the post image");
            return;
        }

        try {
            PixelCopy.request(
                    activity.getWindow(),
                    bounds,
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
        synchronized (RENDERED_POSTS_LOCK) {
            Rect bounds = RENDERED_BOUNDS.get(postId);
            return bounds == null ? null : new Rect(bounds);
        }
    }

    private static PositionCallback registerRenderedPost(String postId) {
        synchronized (RENDERED_POSTS_LOCK) {
            removeClearedTargets();
            WeakReference<PositionCallback> existingReference = RENDERED_POSTS.get(postId);
            PositionCallback existing = existingReference == null ? null : existingReference.get();
            if (existing != null) return existing;

            evictRenderedPostIfNeeded();
            PositionCallback callback = new PositionCallback(postId);
            RENDERED_POSTS.put(postId, new WeakReference<>(callback));
            return callback;
        }
    }

    private static void evictRenderedPostIfNeeded() {
        if (RENDERED_POSTS.size() < MAX_RENDERED_POSTS &&
                RENDERED_BOUNDS.size() < MAX_RENDERED_POSTS) {
            return;
        }

        Iterator<String> renderedPostIds = RENDERED_POSTS.keySet().iterator();
        if (renderedPostIds.hasNext()) {
            String oldestPostId = renderedPostIds.next();
            renderedPostIds.remove();
            RENDERED_BOUNDS.remove(oldestPostId);
            return;
        }

        Iterator<String> boundPostIds = RENDERED_BOUNDS.keySet().iterator();
        if (boundPostIds.hasNext()) {
            boundPostIds.next();
            boundPostIds.remove();
        }
    }

    private static void registerRenderedBounds(String postId, Rect bounds) {
        synchronized (RENDERED_POSTS_LOCK) {
            Rect previous = RENDERED_BOUNDS.get(postId);
            if (bounds.equals(previous)) return;

            evictRenderedPostIfNeeded();
            RENDERED_BOUNDS.put(postId, new Rect(bounds));
        }
    }

    private static void removeClearedTargets() {
        Iterator<Map.Entry<String, WeakReference<PositionCallback>>> entries =
                RENDERED_POSTS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, WeakReference<PositionCallback>> entry = entries.next();
            if (entry.getValue().get() != null) continue;
            entries.remove();
            RENDERED_BOUNDS.remove(entry.getKey());
        }
    }

    private static BoundsReader boundsReader(Class<?> layoutBoundsClass) {
        BoundsReader reader = BOUNDS_READERS.get(layoutBoundsClass);
        if (reader != null) return reader;
        synchronized (BOUNDS_READERS) {
            reader = BOUNDS_READERS.get(layoutBoundsClass);
            if (reader != null) return reader;
            Method[] methods = layoutBoundsClass.getMethods();
            ArrayList<Method> candidates = new ArrayList<>(methods.length);
            for (Method method : methods) {
                if (method.getParameterCount() == 0 && !method.getReturnType().isPrimitive()) {
                    candidates.add(method);
                }
            }
            reader = new BoundsReader(candidates.toArray(new Method[0]));
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

        int first = coordinate(fields[0], value);
        int second = coordinate(fields[1], value);
        int third = coordinate(fields[2], value);
        int fourth = coordinate(fields[3], value);
        return new Rect(first, second, third, fourth);
    }

    private static int coordinate(Field field, Object value) throws IllegalAccessException {
        return field.getType() == float.class
                ? Math.round(field.getFloat(value))
                : field.getInt(value);
    }

    private static final Map<Class<?>, Field[]> RECTANGLE_FIELDS = new ConcurrentHashMap<>();

    private static Field[] rectangleFields(Class<?> type) {
        Field[] declaredFields = type.getDeclaredFields();
        ArrayList<Field> coordinates = new ArrayList<>(4);
        for (Field field : declaredFields) {
            if ((field.getType() != int.class && field.getType() != float.class) ||
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
        private final Method[] allMethods;
        private volatile Method[] resolvedMethods;

        private BoundsReader(Method[] allMethods) {
            this.allMethods = allMethods;
        }

        private Rect read(Object layoutBounds) {
            Method[] methods = resolvedMethods;
            ArrayList<Method> validMethods = methods == null ? new ArrayList<>() : null;
            Rect result = null;
            for (Method method : methods == null ? allMethods : methods) {
                try {
                    Rect candidate = readIntRect(method.invoke(layoutBounds));
                    if (validMethods != null) validMethods.add(method);
                    if (candidate == null || candidate.width() <= 0 || candidate.height() <= 0) {
                        continue;
                    }
                    if (result == null || candidate.top > result.top ||
                            (candidate.top == result.top && candidate.left > result.left)) {
                        result = candidate;
                    }
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    NewXLogger.printInfo(
                            () -> DEBUG_TAG + ": Ignoring non-rectangle bounds candidate",
                            exception
                    );
                }
            }
            if (resolvedMethods == null && validMethods != null && !validMethods.isEmpty()) {
                resolvedMethods = validMethods.toArray(new Method[0]);
            }
            return result;
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
