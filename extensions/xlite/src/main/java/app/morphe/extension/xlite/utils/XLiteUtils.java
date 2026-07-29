package app.morphe.extension.xlite.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;

import app.morphe.extension.shared.Utils;

/**
 * Shared context, reflection, string, and UI utilities for X-Lite features.
 */
public final class XLiteUtils {

    private XLiteUtils() {
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

    public static boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    public static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null || methodName == null) return null;
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
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
