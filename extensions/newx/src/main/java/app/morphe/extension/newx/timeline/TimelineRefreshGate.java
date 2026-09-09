package app.morphe.extension.newx.timeline;

import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class TimelineRefreshGate {
    private static final long PENDING_REFRESH_TIMEOUT_MILLIS = 15_000L;

    private static final AtomicLong pendingPostDeepLinkDeadline = new AtomicLong();
    private static final AtomicLong pendingForYouFilterRefreshDeadline = new AtomicLong();

    private TimelineRefreshGate() {
    }

    public static void markPostDeepLink(Intent intent) {
        if (!isPostDeepLink(intent)) return;

        pendingPostDeepLinkDeadline.set(
                SystemClock.uptimeMillis() + PENDING_REFRESH_TIMEOUT_MILLIS
        );
    }

    public static boolean isPostDeepLinkPending() {
        return isPending(pendingPostDeepLinkDeadline);
    }

    public static boolean consumePostDeepLink() {
        return consume(pendingPostDeepLinkDeadline);
    }

    public static void markForYouFilterRefresh() {
        pendingForYouFilterRefreshDeadline.set(
                SystemClock.uptimeMillis() + PENDING_REFRESH_TIMEOUT_MILLIS
        );
    }

    public static boolean isForYouFilterRefreshPending() {
        return isPending(pendingForYouFilterRefreshDeadline);
    }

    public static boolean consumeForYouFilterRefresh() {
        return consume(pendingForYouFilterRefreshDeadline);
    }

    public static boolean isTimelineDataEmpty(List<?> pages) {
        if (pages == null || pages.isEmpty()) return true;

        // URT exposes timeline data as pages; an empty page is still an empty initial load.
        for (Object page : pages) {
            if (page == null) continue;
            if (!(page instanceof List<?>)) return false;
            if (!((List<?>) page).isEmpty()) return false;
        }
        return true;
    }

    private static boolean isPostDeepLink(Intent intent) {
        if (intent == null) return false;

        Uri data = intent.getData();
        String path = data == null ? null : data.getPath();
        return path != null && path.contains("/status/");
    }

    private static boolean isPending(AtomicLong deadlineHolder) {
        long deadline = deadlineHolder.get();
        if (deadline == 0L) return false;
        if (deadline > SystemClock.uptimeMillis()) return true;

        deadlineHolder.compareAndSet(deadline, 0L);
        return false;
    }

    private static boolean consume(AtomicLong deadlineHolder) {
        while (true) {
            long deadline = deadlineHolder.get();
            if (deadline == 0L) return false;

            if (deadline <= SystemClock.uptimeMillis()) {
                if (deadlineHolder.compareAndSet(deadline, 0L)) {
                    return false;
                }
                continue;
            }

            if (deadlineHolder.compareAndSet(deadline, 0L)) {
                return true;
            }
        }
    }
}
