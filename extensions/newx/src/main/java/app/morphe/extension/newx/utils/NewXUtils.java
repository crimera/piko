package app.morphe.extension.newx.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Utils;

/**
 * Shared context, reflection, string, and UI utilities for NewX features.
 */
public final class NewXUtils {
    private static WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    private static boolean lifecycleCallbacksRegistered;
    // Presenter shapes are fixed per class; cache the accessible field walk so
    // share/options lookups pay reflection once instead of per invocation.
    private static final ConcurrentHashMap<Class<?>, Field[]> PRESENTER_FIELDS =
            new ConcurrentHashMap<>();

    private NewXUtils() {
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

    public static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper wrapper) {
            if (current instanceof Activity activity) return activity;
            Context baseContext = wrapper.getBaseContext();
            if (baseContext == current) return null;
            current = baseContext;
        }
        return current instanceof Activity activity ? activity : null;
    }

    public static Activity findUsableActivity(Context context) {
        Activity activity = findActivity(context);
        if (isUsable(activity)) return activity;

        activity = resumedActivity.get();
        return isUsable(activity) ? activity : null;
    }

    public static PresenterData findPresenterData(Object presenter, String valueTypeName)
            throws IllegalAccessException {
        if (presenter == null) return new PresenterData(null, null);

        Context context = null;
        Object value = null;
        for (Field field : presenterFields(presenter.getClass())) {
            boolean isContext = Context.class.isAssignableFrom(field.getType());
            boolean isValue = (valueTypeName != null && valueTypeName.equals(field.getType().getName()))
                    || ("com.x.models.timelines.items.UrtTimelinePost".equals(valueTypeName)
                    && field.getType().getName().startsWith("com.x.models.timelines.items."));
            if (!isContext && !isValue) continue;

            Object fieldValue = field.get(presenter);
            if (context == null && isContext && fieldValue instanceof Context) {
                context = (Context) fieldValue;
            }
            if (value == null && isValue && fieldValue != null && !isContext) value = fieldValue;
        }
        return new PresenterData(context, value);
    }

    private static Field[] presenterFields(Class<?> presenterClass) {
        Field[] cached = PRESENTER_FIELDS.get(presenterClass);
        if (cached != null) return cached;
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = presenterClass; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                fields.add(field);
            }
        }
        Field[] resolved = fields.toArray(new Field[0]);
        Field[] raced = PRESENTER_FIELDS.putIfAbsent(presenterClass, resolved);
        return raced != null ? raced : resolved;
    }

    private static void clearActivity(Activity activity) {
        if (resumedActivity.get() == activity) resumedActivity.clear();
    }

    private static boolean isUsable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    public static boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    public static boolean isAscii(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) > 127) return false;
        }
        return true;
    }

    /** Case-insensitive ASCII substring search without allocating. Needle must be lowercase. */
    public static boolean containsIgnoreCaseAscii(String text, String lowerNeedle) {
        int limit = text.length() - lowerNeedle.length();
        outer:
        for (int index = 0; index <= limit; index++) {
            for (int needleIndex = 0; needleIndex < lowerNeedle.length(); needleIndex++) {
                char candidate = text.charAt(index + needleIndex);
                if (candidate >= 'A' && candidate <= 'Z') candidate += 'a' - 'A';
                if (candidate != lowerNeedle.charAt(needleIndex)) continue outer;
            }
            return true;
        }
        return false;
    }

    public static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null || methodName == null) return null;
        return lookupMethod(target.getClass(), methodName).invoke(target);
    }

    private static final ConcurrentHashMap<MethodKey, Method> METHOD_CACHE =
            new ConcurrentHashMap<>();

    private static final class MethodKey {
        private final Class<?> owner;
        private final String name;

        private MethodKey(Class<?> owner, String name) {
            this.owner = owner;
            this.name = name;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof MethodKey key)) return false;
            return owner == key.owner && name.equals(key.name);
        }

        @Override public int hashCode() {
            return owner.hashCode() * 31 + name.hashCode();
        }
    }

    private static Method lookupMethod(Class<?> owner, String name)
            throws ReflectiveOperationException {
        MethodKey key = new MethodKey(owner, name);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached;
        // getMethod walks the hierarchy under a lock; cache per class-shape.
        Method resolved = owner.getMethod(name);
        Method raced = METHOD_CACHE.putIfAbsent(key, resolved);
        return raced != null ? raced : resolved;
    }

    public static final class PresenterData {
        private final Context context;
        private final Object value;

        private PresenterData(Context context, Object value) {
            this.context = context;
            this.value = value;
        }

        public Context getContext() {
            return context;
        }

        public Object getValue() {
            return value;
        }
    }

    public static Object invokeIfPresent(Object target, String methodName) {
        if (target == null || methodName == null) return null;
        try {
            return invoke(target, methodName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final Pattern UNSAFE_FILE_CHARS =
            Pattern.compile("[^A-Za-z0-9._-]");

    private static final Pattern STATUS_URL_PATTERN =
            Pattern.compile("https?://(?:[a-zA-Z0-9-]+\\.)*(?:twitter|x|fxtwitter|vxtwitter|fixupx|twx)\\.com/([A-Za-z0-9_]+)/status/\\d+");

    public static String sanitizeFileName(String value) {
        if (value == null) return "";
        return UNSAFE_FILE_CHARS.matcher(value).replaceAll("_");
    }

    /**
     * Resolves the source post identity used by NewX media features. The input may be either the
     * timeline post wrapper or its contextual-post model; both expose the same semantic fields in
     * their toString representation across the supported releases.
     */
    public static String sourcePostId(Object post) {
        return safeFileSegment(rawSourcePostId(post), "post");
    }

    public static String sourceUsername(Object post) {
        return safeFileSegment(rawSourceScreenName(post), "twitter");
    }

    /** Text form of {@link #sourceUsername(Object)}, for callers that already hold the toString. */
    public static String sourceUsername(String postText) {
        return safeFileSegment(rawSourceScreenName(postText), "twitter");
    }

    /**
     * Resolves the source post id before filename sanitization. Used where the identifier is
     * substituted into a user-configured template instead of a fixed filename.
     */
    public static String rawSourcePostId(Object post) {
        return rawSourcePostId(postText(post));
    }

    /**
     * Text form of {@link #rawSourcePostId(Object)}, for callers that already hold the toString.
     * A post's toString can be large and is expensive to rebuild per field lookup.
     */
    public static String rawSourcePostId(String postText) {
        String originalPostText = originalRepostedPostText(postText);
        if (originalPostText != null) {
            String originalPostId = ToStringParser.fieldValue(originalPostText, "id");
            if (originalPostId != null) return originalPostId;
        }

        String sourcePostId = sourceMediaField(postText, "sourcePostIdentifier");
        if (sourcePostId != null) return sourcePostId;

        String canonicalText = canonicalPostText(postText);
        String postId = ToStringParser.fieldValue(canonicalText, "id");
        if (postId != null) return postId;

        return ToStringParser.fieldValue(postText, "id");
    }

    /**
     * Resolves the author handle before filename sanitization. Suspended-account posts expose a
     * placeholder author, so the media mention/expanded-URL fallbacks are tried first.
     */
    public static String rawSourceScreenName(Object post) {
        return rawSourceScreenName(postText(post));
    }

    /** Text form of {@link #rawSourceScreenName(Object)}. */
    public static String rawSourceScreenName(String postText) {
        String originalPostText = originalRepostedPostText(postText);
        if (originalPostText != null) {
            String originalScreenName = rawAuthorField(originalPostText, "screenName");
            if (originalScreenName != null) return originalScreenName;
        }

        if (sourceMediaField(postText, "sourcePostIdentifier") != null) {
            String mentionScreenName = firstMentionScreenName(postText);
            if (mentionScreenName != null) return mentionScreenName;

            String expandedUrlScreenName = mediaExpandedUrlScreenName(postText);
            if (expandedUrlScreenName != null) return expandedUrlScreenName;
        }

        String canonicalScreenName = rawAuthorField(canonicalPostText(postText), "screenName");
        if (canonicalScreenName != null) return canonicalScreenName;

        return rawAuthorField(postText, "screenName");
    }

    /**
     * Resolves the author display name shown on the post. The author model is polymorphic: only
     * the user-backed variants ({@code MinimalUser}, {@code ProfileUser}) carry a name.
     */
    public static String rawSourceDisplayName(Object post) {
        return rawSourceDisplayName(postText(post));
    }

    /** Text form of {@link #rawSourceDisplayName(Object)}. */
    public static String rawSourceDisplayName(String postText) {
        String originalPostText = originalRepostedPostText(postText);
        if (originalPostText != null) {
            String originalName = rawAuthorField(originalPostText, "name");
            if (originalName != null) return originalName;
        }

        String canonicalName = rawAuthorField(canonicalPostText(postText), "name");
        if (canonicalName != null) return canonicalName;

        return rawAuthorField(postText, "name");
    }

    /** Reads a raw source-media lineage field such as {@code sourcePostIdentifier}. */
    public static String rawSourceMediaField(Object post, String fieldName) {
        return rawSourceMediaField(postText(post), fieldName);
    }

    /** Text form of {@link #rawSourceMediaField(Object, String)}. */
    public static String rawSourceMediaField(String postText, String fieldName) {
        return sourceMediaField(postText, fieldName);
    }

    private static String rawAuthorField(String text, String fieldName) {
        if (text == null) return null;

        String author = ToStringParser.fieldValue(text, "author");
        return author == null ? null : ToStringParser.fieldValue(author, fieldName);
    }

    private static String postText(Object post) {
        return post == null ? null : post.toString();
    }

    private static String canonicalPostText(Object post) {
        return canonicalPostText(postText(post));
    }

    private static String canonicalPostText(String postText) {
        if (postText == null) return null;

        String canonicalPost = ToStringParser.fieldValue(postText, "canonicalPost");
        return canonicalPost != null ? canonicalPost : postText;
    }

    private static String originalRepostedPostText(String postText) {
        if (postText == null) return null;

        String repostedPost = ToStringParser.fieldValue(postText, "rePostedPost");
        if (repostedPost == null) return null;

        String canonicalPost = ToStringParser.fieldValue(repostedPost, "canonicalPost");
        return canonicalPost != null ? canonicalPost : repostedPost;
    }

    private static String firstMentionScreenName(String text) {
        String entityList = ToStringParser.fieldValue(text, "entityList");
        if (entityList == null) return null;

        String mentions = ToStringParser.fieldValue(entityList, "mentions");
        return mentions == null ? null : ToStringParser.fieldValue(mentions, "screenName");
    }

    private static String mediaExpandedUrlScreenName(String text) {
        if (text == null) return null;

        String entityList = ToStringParser.fieldValue(text, "entityList");
        String searchScope = entityList != null ? entityList : text;
        String expandedUrl = ToStringParser.fieldValue(searchScope, "expandedUrl");
        String screenName = screenNameFromUrl(expandedUrl);
        return screenName != null ? screenName : screenNameFromUrl(searchScope);
    }

    private static String screenNameFromUrl(String url) {
        if (url == null) return null;

        Matcher matcher = STATUS_URL_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String sourceMediaField(String text, String fieldName) {
        if (text == null) return null;

        String sourceInfo = ToStringParser.fieldValue(text, "sourceInfo");
        return sourceInfo == null ? null : ToStringParser.fieldValue(sourceInfo, fieldName);
    }

    private static String safeFileSegment(String value, String fallback) {
        if (value == null) return fallback;

        String sanitized = value.trim().replaceFirst("^@", "")
                .replaceAll("[^A-Za-z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._-]+|[._-]+$", "");
        return sanitized.isEmpty() ? fallback : sanitized;
    }

    private static volatile Handler mainHandler;

    private static Handler mainHandler() {
        Handler handler = mainHandler;
        if (handler != null) return handler;
        handler = new Handler(Looper.getMainLooper());
        mainHandler = handler;
        return handler;
    }

    public static void runOnUiThread(Runnable runnable) {
        if (runnable == null) return;
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                runnable.run();
                return;
            }
        } catch (Exception ignored) {
        }
        mainHandler().post(runnable);
    }

    public static String identifierToString(Object identifier) {
        if (identifier == null) return null;
        if (identifier instanceof String string) {
            string = string.trim();
            return string.isEmpty() ? null : string;
        }
        if (identifier instanceof Number number) {
            return number.longValue() > 0 ? String.valueOf(number) : null;
        }
        try {
            Object value = invokeIfPresent(identifier, "getValue");
            String string = value == null ? null : String.valueOf(value).trim();
            if (string != null && !string.isEmpty()) return string;

            value = invokeIfPresent(identifier, "getStr");
            string = value == null ? null : String.valueOf(value).trim();
            if (string != null && !string.isEmpty()) return string;

            value = invokeIfPresent(identifier, "a");
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
}
