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

import app.morphe.extension.shared.Utils;

/**
 * Shared context, reflection, string, and UI utilities for NewX features.
 */
public final class NewXUtils {
    private static WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    private static boolean lifecycleCallbacksRegistered;

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
        for (Class<?> type = presenter.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;

                boolean isContext = Context.class.isAssignableFrom(field.getType());
                boolean isValue = (valueTypeName != null && valueTypeName.equals(field.getType().getName()))
                        || ("com.x.models.timelines.items.UrtTimelinePost".equals(valueTypeName)
                        && field.getType().getName().startsWith("com.x.models.timelines.items."));
                if (!isContext && !isValue) continue;

                field.setAccessible(true);
                Object fieldValue = field.get(presenter);
                if (context == null && isContext && fieldValue instanceof Context) {
                    context = (Context) fieldValue;
                }
                if (value == null && isValue && fieldValue != null && !isContext) value = fieldValue;
            }
        }
        return new PresenterData(context, value);
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

    public static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null || methodName == null) return null;
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
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

    public static String sanitizeFileName(String value) {
        if (value == null) return "";
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
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
        new Handler(Looper.getMainLooper()).post(runnable);
    }
}
