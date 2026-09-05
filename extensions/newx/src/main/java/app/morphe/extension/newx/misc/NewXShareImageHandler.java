package app.morphe.extension.newx.misc;

import android.app.Activity;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.newx.utils.NewXUtils;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
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
import java.util.HashMap;
import java.util.Map;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.settings.SettingsRegistry;
import kotlin.jvm.functions.Function1;

/** Bridges NewX's rendered Compose post row to an Android image share intent. */
public final class NewXShareImageHandler {
    private static final String DEBUG_TAG = "DEBUG-share-image";
    private static final String OPTION_NAME = NewXPostOptionActions.SHARE_IMAGE_ACTION;
    private static final String SETTING_ID = "newx.content.share_post_as_image";
    private static final String POST_IDENTIFIER_CLASS = "com.x.models.PostIdentifier";
    private static final String URT_POST_CLASS = "com.x.models.timelines.items.UrtTimelinePost";
    private static final int MAX_CAPTURE_PIXELS = 16_000_000;
    private static final int MAX_RENDERED_POSTS = 128;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Object RENDERED_POSTS_LOCK = new Object();
    private static final Map<String, WeakReference<PositionCallback>> RENDERED_POSTS = new HashMap<>();
    private static final Map<String, Rect> RENDERED_BOUNDS = new HashMap<>();
    private static final Function1<Object, Object> NO_POSITION_CALLBACK = coordinates -> null;

    private NewXShareImageHandler() {
    }

    public static java.util.List<?> addOption(java.util.List<?> groups) {
        return NewXPostOptions.addOption(groups, OPTION_NAME, isEnabled());
    }

    public static Function1<Object, Object> positionCallback(Object postIdentifier) {
        String id = identifierValue(postIdentifier);
        if (id == null) return NO_POSITION_CALLBACK;

        PositionCallback callback = new PositionCallback(id);
        registerRenderedPost(id, callback);
        return callback;
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
            MAIN_HANDLER.post(() -> shareAsImage(context, post));
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
        try {
            id = postId(post);
        } catch (ReflectiveOperationException exception) {
            Utils.showToastShort("Could not identify the selected post");
            return;
        }
        if (id == null) {
            Utils.showToastShort("Could not identify the selected post");
            return;
        }

        View decorView = activity.getWindow().getDecorView();
        decorView.postOnAnimation(() -> decorView.postOnAnimation(() -> captureRenderedPost(activity, id)));
    }

    public static String labelFor(Object action, Object originalLabel) {
        if (isShareImageAction(action)) return "Share Tweet as Image";
        return originalLabel instanceof String ? (String) originalLabel : null;
    }

    public static boolean usesIcon(Object action) {
        return isShareImageAction(action);
    }

    private static void captureRenderedPost(Activity activity, String postId) {
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
                    result -> finishCapture(activity, bitmap, postId, result),
                    MAIN_HANDLER
            );
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> DEBUG_TAG + ": PixelCopy request failed", exception);
            bitmap.recycle();
            Utils.showToastShort("Could not capture the rendered post");
        }
    }

    private static void finishCapture(Context context, Bitmap bitmap, String postId, int result) {
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
            uri = saveImage(context, bitmap, postId);
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

    private static void registerRenderedPost(String postId, PositionCallback target) {
        synchronized (RENDERED_POSTS_LOCK) {
            removeClearedTargets();
            if (RENDERED_POSTS.size() >= MAX_RENDERED_POSTS) {
                RENDERED_POSTS.clear();
                RENDERED_BOUNDS.clear();
            }
            RENDERED_POSTS.put(postId, new WeakReference<>(target));
        }
    }

    private static void registerRenderedBounds(String postId, Rect bounds) {
        synchronized (RENDERED_POSTS_LOCK) {
            if (RENDERED_BOUNDS.size() >= MAX_RENDERED_POSTS && !RENDERED_BOUNDS.containsKey(postId)) {
                RENDERED_POSTS.clear();
                RENDERED_BOUNDS.clear();
            }
            RENDERED_BOUNDS.put(postId, new Rect(bounds));
        }
    }

    private static void removeClearedTargets() {
        RENDERED_POSTS.entrySet().removeIf(entry -> entry.getValue().get() == null);
    }

    private static Rect resolveWindowBounds(Object layoutBounds) {
        Rect result = null;
        for (Method method : layoutBounds.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || method.getReturnType().isPrimitive()) continue;

            try {
                Rect candidate = readIntRect(method.invoke(layoutBounds));
                if (candidate == null || candidate.width() <= 0 || candidate.height() <= 0) continue;
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
        return result;
    }

    private static Rect readIntRect(Object value) throws IllegalAccessException {
        if (value == null) return null;

        Field[] fields = value.getClass().getDeclaredFields();
        int coordinateCount = 0;
        for (Field field : fields) {
            if ((field.getType() != int.class && field.getType() != float.class) || java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            coordinateCount++;
        }
        if (coordinateCount != 4) return null;

        int[] coordinates = new int[4];
        int coordinateIndex = 0;
        for (Field field : fields) {
            if ((field.getType() != int.class && field.getType() != float.class) || java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            if (field.getType() == float.class) {
                coordinates[coordinateIndex++] = Math.round(field.getFloat(value));
            } else {
                coordinates[coordinateIndex++] = field.getInt(value);
            }
        }
        return new Rect(coordinates[0], coordinates[1], coordinates[2], coordinates[3]);
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

    private static Uri saveImage(Context context, Bitmap bitmap, String postId) {
        ContentResolver resolver = context.getContentResolver();
        String fileName = "tweet_" + NewXUtils.sanitizeFileName(postId) + ".png";
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
        if (identifier == null) return null;
        if (identifier instanceof String string) {
            string = string.trim();
            return string.isEmpty() ? null : string;
        }
        if (identifier instanceof Number number) {
            return number.longValue() > 0 ? String.valueOf(number) : null;
        }
        try {
            Object value = NewXUtils.invokeIfPresent(identifier, "getValue");
            String string = value == null ? null : String.valueOf(value).trim();
            if (string != null && !string.isEmpty()) return string;

            value = NewXUtils.invokeIfPresent(identifier, "getStr");
            string = value == null ? null : String.valueOf(value).trim();
            if (string != null && !string.isEmpty()) return string;

            value = NewXUtils.invokeIfPresent(identifier, "a");
            string = value == null ? null : String.valueOf(value).trim();
            if (string != null && !string.isEmpty()) return string;
        } catch (Exception ignored) {
        }
        String string = String.valueOf(identifier).trim();
        if (!string.isEmpty() && !string.startsWith(identifier.getClass().getName())) {
            return string;
        }
        return null;
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
