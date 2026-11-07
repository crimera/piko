package app.morphe.extension.shared;

import android.content.Context;

public final class Utils {
    private static volatile boolean contextSet;
    private static volatile Context context;

    private Utils() {
    }

    public static Context getContext() {
        if (!contextSet) throw new IllegalStateException("Utils test stub: context never set");
        return context;
    }

    public static void setContext(Context value) {
        context = value;
        contextSet = true;
    }

    public static void reset() {
        context = null;
        contextSet = false;
    }

    public static void showToastShort(String message) {
        throw unmodelled();
    }

    public static void setClipboard(CharSequence text) {
        throw unmodelled();
    }

    public static void restartApp(Context value) {
        throw unmodelled();
    }

    public static String getPatchesReleaseVersion() {
        throw unmodelled();
    }

    public static void runOnBackgroundThread(Runnable runnable) {
        throw unmodelled();
    }

    public static void runOnMainThread(Runnable runnable) {
        throw unmodelled();
    }

    private static IllegalStateException unmodelled() {
        return new IllegalStateException("Utils test stub: helper not modelled");
    }
}
