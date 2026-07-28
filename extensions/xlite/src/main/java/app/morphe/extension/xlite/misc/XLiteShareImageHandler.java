package app.morphe.extension.xlite.misc;

import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import app.morphe.extension.xlite.settings.SettingsRegistry;
import kotlin.jvm.functions.Function1;

/** Bridges X-Lite's rendered Compose post row to an Android image share intent. */
public final class XLiteShareImageHandler {
    private static final String DEBUG_TAG = "DEBUG-share-image";
    private static final String OPTION_NAME = "ViewDebugDialog";
    private static final String SETTING_ID = "xlite.content.share_post_as_image";
    private static final String POST_IDENTIFIER_CLASS = "com.x.models.PostIdentifier";
    private static final String URT_POST_CLASS = "com.x.models.timelines.items.UrtTimelinePost";
    private static final int MAX_CAPTURE_PIXELS = 16_000_000;
    private static final int MAX_RENDERED_POSTS = 128;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Object RENDERED_POSTS_LOCK = new Object();
    private static final Map<String, WeakReference<PositionCallback>> RENDERED_POSTS = new HashMap<>();
    private static final Map<String, Rect> RENDERED_BOUNDS = new HashMap<>();
    private static final Function1<Object, Object> NO_POSITION_CALLBACK = coordinates -> null;
    private static WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    private static boolean lifecycleCallbacksRegistered;

    private XLiteShareImageHandler() {
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
    }

    public static java.util.List<?> addOption(java.util.List<?> groups) {
        if (!isEnabled() || groups == null || groups.isEmpty()) return groups;

        try {
            Object action = findAction(groups);
            if (action == null || containsAction(groups, action)) return groups;

            Object group = createOptionGroup(groups, action);
            if (group == null) return groups;

            java.util.ArrayList<Object> copy = new java.util.ArrayList<>(groups);
            copy.add(group);
            return copy;
        } catch (ReflectiveOperationException exception) {
            return groups;
        }
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
            Context context = null;
            Object post = null;
            for (Field field : presenter.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                if (Context.class.isAssignableFrom(field.getType())) context = (Context) field.get(presenter);
                if (URT_POST_CLASS.equals(field.getType().getName())) post = field.get(presenter);
            }
            if (context == null || post == null) {
                showToast(context, "Could not find the selected post");
                return true;
            }
            shareAsImage(context, post);
            return true;
        } catch (IllegalAccessException exception) {
            showToast(null, "Could not find the selected post");
            return true;
        }
    }

    public static void shareAsImage(Context context, Object post) {
        if (context == null || post == null) return;

        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN_HANDLER.post(() -> shareAsImage(context, post));
            return;
        }

        Activity contextActivity = findActivity(context);
        Activity activity = contextActivity != null ? contextActivity : currentActivity();
        if (activity == null) {
            Log.e(DEBUG_TAG, "No activity for context " + context.getClass().getName());
            showToast(context, "Could not capture the rendered post");
            return;
        }

        String id;
        try {
            id = postId(post);
        } catch (ReflectiveOperationException exception) {
            showToast(context, "Could not identify the selected post");
            return;
        }
        if (id == null) {
            showToast(context, "Could not identify the selected post");
            return;
        }

        View decorView = activity.getWindow().getDecorView();
        decorView.postOnAnimation(() -> decorView.postOnAnimation(() -> captureRenderedPost(activity, id)));
    }

    public static String labelFor(Object action, Object originalLabel) {
        if (isShareImageAction(action)) return "Share Tweet as Image";
        return originalLabel instanceof String ? (String) originalLabel : null;
    }

    public static boolean usesShareIcon(Object action) {
        return isShareImageAction(action);
    }

    private static void captureRenderedPost(Activity activity, String postId) {
        View decorView = activity.getWindow().getDecorView();
        if (!decorView.isAttachedToWindow()) {
            showToast(activity, "Post is no longer rendered");
            return;
        }

        Rect bounds = renderedBounds(postId);
        if (bounds == null) {
            Log.e(DEBUG_TAG, "No resolved bounds for post " + postId);
            showToast(activity, "Post is no longer rendered");
            return;
        }
        Log.d(DEBUG_TAG, "Requesting post " + postId + " bounds=" + bounds + " window=" + decorView.getWidth() + "x" + decorView.getHeight());
        if (bounds.left < 0 || bounds.top < 0 || bounds.right > decorView.getWidth() || bounds.bottom > decorView.getHeight()) {
            showToast(activity, "Make the entire post visible before sharing");
            return;
        }

        long pixelCount = (long) bounds.width() * bounds.height();
        if (pixelCount <= 0 || pixelCount > MAX_CAPTURE_PIXELS) {
            showToast(activity, "Rendered post is too large to capture");
            return;
        }

        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);
        } catch (RuntimeException | OutOfMemoryError error) {
            showToast(activity, "Could not allocate the post image");
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
            Log.e(DEBUG_TAG, "PixelCopy request failed", exception);
            bitmap.recycle();
            showToast(activity, "Could not capture the rendered post");
        }
    }

    private static void finishCapture(Context context, Bitmap bitmap, String postId, int result) {
        if (result != PixelCopy.SUCCESS) {
            Log.e(DEBUG_TAG, "PixelCopy result=" + result + " for post " + postId);
            bitmap.recycle();
            showToast(context, "Could not capture the rendered post");
            return;
        }

        Uri uri;
        try {
            uri = saveImage(context, bitmap, postId);
        } finally {
            bitmap.recycle();
        }
        if (uri == null) {
            showToast(context, "Could not save the post image");
            return;
        }
        shareImage(context, uri);
    }

    private static Activity currentActivity() {
        Activity activity = resumedActivity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return null;
        return activity;
    }

    private static void clearActivity(Activity activity) {
        if (resumedActivity.get() == activity) resumedActivity.clear();
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
                Log.d(DEBUG_TAG, "Ignoring non-rectangle bounds candidate", exception);
            }
        }
        return result;
    }

    private static Rect readIntRect(Object value) throws IllegalAccessException {
        if (value == null) return null;

        Field[] fields = value.getClass().getDeclaredFields();
        int coordinateCount = 0;
        for (Field field : fields) {
            if (field.getType() != int.class || java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            coordinateCount++;
        }
        if (coordinateCount != 4) return null;

        int[] coordinates = new int[4];
        int coordinateIndex = 0;
        for (Field field : fields) {
            if (field.getType() != int.class || java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            coordinates[coordinateIndex++] = field.getInt(value);
        }
        return new Rect(coordinates[0], coordinates[1], coordinates[2], coordinates[3]);
    }

    private static boolean isEnabled() {
        try {
            return SettingsRegistry.getBoolean(SETTING_ID);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object findAction(java.util.List<?> groups) throws ReflectiveOperationException {
        for (Object group : groups) {
            for (Object action : actions(group)) {
                if (!(action instanceof Enum<?>)) continue;
                return Enum.valueOf((Class<? extends Enum>) action.getClass(), OPTION_NAME);
            }
        }
        return null;
    }

    private static boolean containsAction(java.util.List<?> groups, Object action) throws ReflectiveOperationException {
        for (Object group : groups) {
            if (actions(group).contains(action)) return true;
        }
        return false;
    }

    private static Object createOptionGroup(java.util.List<?> groups, Object action) throws ReflectiveOperationException {
        Object exemplar = groups.get(0);
        java.lang.reflect.Constructor<?> constructor = null;
        for (java.lang.reflect.Constructor<?> candidate : exemplar.getClass().getDeclaredConstructors()) {
            if (candidate.getParameterCount() != 1 || !java.util.List.class.isAssignableFrom(candidate.getParameterTypes()[0])) continue;
            constructor = candidate;
            break;
        }
        if (constructor == null) return null;

        constructor.setAccessible(true);
        return constructor.newInstance(java.util.Collections.singletonList(action));
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Object> actions(Object group) throws ReflectiveOperationException {
        for (Field field : group.getClass().getDeclaredFields()) {
            if (!java.util.List.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            Object value = field.get(group);
            if (value instanceof java.util.List<?>) return (java.util.List<Object>) value;
        }
        return java.util.Collections.emptyList();
    }

    private static boolean isShareImageAction(Object action) {
        return action instanceof Enum<?> && OPTION_NAME.equals(((Enum<?>) action).name());
    }

    // Retained until rendered-UI capture passes device verification.
    @SuppressWarnings("unused")
    private static Bitmap renderPost(Object post) throws ReflectiveOperationException {
        final int width = 1080;
        final int padding = 72;
        Object author = invoke(post, "getAuthor");
        Object postResult = invoke(post, "getPostResult");
        Object canonicalPost = postResult == null ? null : invoke(postResult, "getCanonicalPost");
        String name = stringValue(invoke(author, "getName"), "X user");
        String screenName = stringValue(invoke(author, "getScreenName"), "");
        String text = stringValue(canonicalPost == null ? null : invoke(canonicalPost, "getText"), "");

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
        String fileName = "tweet_" + safeFileName(postId) + ".png";
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

    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            Context baseContext = ((ContextWrapper) current).getBaseContext();
            if (baseContext == current) return null;
            current = baseContext;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    private static String postId(Object post) throws ReflectiveOperationException {
        return identifierValue(invoke(post, "getId"));
    }

    private static String identifierValue(Object identifier) {
        if (identifier == null || !POST_IDENTIFIER_CLASS.equals(identifier.getClass().getName())) return null;
        try {
            Object value = invoke(identifier, "getValue");
            String string = value == null ? null : String.valueOf(value).trim();
            if (string != null && !string.isEmpty()) return string;

            value = invoke(identifier, "getStr");
            string = value == null ? null : String.valueOf(value).trim();
            return string == null || string.isEmpty() ? null : string;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Object invoke(Object target, String name) throws ReflectiveOperationException {
        if (target == null) return null;
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) return fallback;
        String string = String.valueOf(value).trim();
        return string.isEmpty() ? fallback : string;
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void showToast(Context context, String message) {
        if (context == null) return;
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
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
