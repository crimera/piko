package app.morphe.extension.shared;

import android.content.Context;

/**
 * Test-source stand-in for {@code app.morphe.extension.shared.Utils} from the {@code compileOnly}
 * morphe-extensions-library artifact, which is absent from the unit-test runtime classpath.
 *
 * <p>Only the static context is modelled. Until a test calls {@link #setContext} the class behaves
 * like the missing library did ({@link #getContext} and every other helper throw), so tests that
 * never set a context keep exercising the same "shared helper unavailable" fallbacks. Tests must
 * call {@link #reset()} when done so the state does not leak into other tests in the same JVM.
 */
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

    /** Test-only: returns the stub to its initial "library unavailable" state. */
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
