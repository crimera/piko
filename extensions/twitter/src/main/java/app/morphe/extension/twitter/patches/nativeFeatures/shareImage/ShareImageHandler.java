package app.morphe.extension.twitter.patches.nativeFeatures.shareImage;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ScrollView;

import app.morphe.extension.twitter.Utils;
import app.morphe.extension.twitter.entity.Tweet;
import app.morphe.extension.twitter.utils.ViewUtils;

import java.io.OutputStream;

public class ShareImageHandler {

    public static void shareAsImage(Context context, Object tweetObj) {
        if (!(context instanceof Activity)) {
            Utils.toast("Invalid context");
            return;
        }

        try {
            Activity activity = (Activity) context;
            Tweet tweet = new Tweet(tweetObj);
            Utils.toast("Capturing tweet...");
            
            View rootView = activity.getWindow().getDecorView().getRootView();
            View tweetView = searchViewTree(rootView, tweet.getTweetId());
            CaptureTarget target = resolveCaptureTarget(activity, rootView, tweetView);

            if (target == null) {
                target = new CaptureTarget(rootView, null);
            }

            float density = activity.getResources().getDisplayMetrics().density;
            Utils.logger(String.format("Capture: Tweet %d | Density %.1f", tweet.getTweetId(), density));
            Utils.logger("Target: " + target.view.getClass().getSimpleName() + " " + target.view.getWidth() + "x" + target.view.getHeight());
            if (target.clipRect != null) {
                Utils.logger("Clip: " + target.clipRect.toShortString());
            }
            dumpViewTree(activity, target.view, 0);

            Bitmap bitmap;
            try {
                ViewUtils.setScrollbarsVisible(target.view, false);
                bitmap = ViewUtils.viewToBitmap(target.view, target.clipRect);
            } finally {
                ViewUtils.setScrollbarsVisible(target.view, true);
            }
            
            shareImage(activity, bitmap, "tweet_" + tweet.getTweetId());
        } catch (Exception e) {
            Utils.logger(e);
            Utils.toast("Error: " + e.getMessage());
        }
    }

    private static void shareImage(Activity activity, Bitmap bitmap, String filename) {
        try {
            cleanupOldFiles(activity);
            ContentResolver resolver = activity.getContentResolver();
            String displayName = filename + ".png";

            // Overwrite existing
            try {
                String selection = MediaStore.MediaColumns.DISPLAY_NAME + " = ? AND " + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
                resolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, new String[]{displayName, "Pictures/Piko/%"});
            } catch (Exception ignored) {}

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Piko");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            }

            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return;

            try (OutputStream os = resolver.openOutputStream(uri)) {
                if (os != null) ViewUtils.saveBitmap(bitmap, os);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
            }

            Intent intent = new Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            activity.startActivity(Intent.createChooser(intent, "Share Tweet Image"));
        } catch (Exception e) {
            Utils.logger(e);
        }
    }

    private static void cleanupOldFiles(Context context) {
        try {
            long cutoff = (System.currentTimeMillis() - (24 * 60 * 60 * 1000)) / 1000;
            String selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND " + MediaStore.MediaColumns.DATE_ADDED + " < ?";
            context.getContentResolver().delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, new String[]{"Pictures/Piko/%", String.valueOf(cutoff)});
        } catch (Exception ignored) {}
    }

    private static View searchViewTree(View view, Long targetId) {
        Object tag = view.getTag();
        if (tag instanceof Long && tag.equals(targetId)) return view;
        if (tag != null && tag.toString().contains(targetId.toString())) return view;

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = searchViewTree(group.getChildAt(i), targetId);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static CaptureTarget resolveCaptureTarget(Activity activity, View rootView, View tweetView) {
        if (tweetView == null) return null;

        View tweetRow = findTweetRowContainer(activity, tweetView);
        if (tweetRow == null) tweetRow = tweetView;

        if (isTweetDetailScreen(activity)) {
            ViewGroup threadContainer = findThreadContainer(tweetRow);
            if (threadContainer != null) {
                Rect threadBounds = computeThreadBounds(activity, threadContainer, tweetRow);
                if (threadBounds != null) return new CaptureTarget(threadContainer, threadBounds);
            }
        }

        View expanded = expandToTweetContainer(activity, rootView, tweetRow);
        View target = expanded != null ? expanded : tweetRow;
        
        int adjustedBottom = getBottomWithoutDivider(activity, target);
        if (adjustedBottom < target.getHeight()) {
            return new CaptureTarget(target, new Rect(0, 0, target.getWidth(), adjustedBottom));
        }

        return new CaptureTarget(target, null);
    }

    private static int getBottomWithoutDivider(Activity activity, View target) {
        int h = target.getHeight();

        // 1. Aggressive Anchor Cropping
        // We look for the action bar or stats container. If found, we crop 1dp INSIDE them
        // to absolutely guarantee no gray separator slop from the parent container.
        String[] anchors = {"tweet_inline_actions", "stats_container"};
        for (String name : anchors) {
            int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
            if (id == 0) continue;
            View anchor = target.findViewById(id);
            if (anchor != null && anchor.getVisibility() == View.VISIBLE) {
                int relBot = getRelativeBottom(anchor, target);
                int crop = relBot - dp(activity, 1);
                if (crop > 0 && crop < h) {
                    Utils.logger("Bottom anchored to #" + name + " at " + crop);
                    return crop;
                }
            }
        }

        // 2. Linear "Slop" Reduction
        // If no anchors are found, we simply iterate through immediate children from bottom to top.
        // Any thin view (<= 4dp) at the bottom is treated as a divider or "slop" and removed.
        if (target instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) target;
            int currentBot = h;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;

                // If child is thin, crop it and move up
                if (child.getHeight() <= dp(activity, 4)) {
                    currentBot = child.getTop();
                    Utils.logger("Reduced slop bar: #" + getResourceName(activity, child));
                } else {
                    // First "real" content found, stop reduction
                    break;
                }
            }
            return currentBot;
        }

        return h;
    }

    private static String getResourceName(Context context, View v) {
        try {
            int id = v.getId();
            if (id == View.NO_ID || id == 0) return v.getClass().getSimpleName();
            return context.getResources().getResourceEntryName(id);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static int getRelativeBottom(View child, View parent) {
        int bottom = child.getBottom();
        View current = (View) child.getParent();
        while (current != null && current != parent) {
            bottom += current.getTop();
            current = (current.getParent() instanceof View) ? (View) current.getParent() : null;
        }
        return bottom;
    }

    private static boolean isTweetDetailScreen(Activity activity) {
        String[] ids = {"persistent_reply", "tweet_inline_actions_top_divider", "stats_container"};
        for (String s : ids) {
            int id = activity.getResources().getIdentifier(s, "id", activity.getPackageName());
            if (id != 0 && activity.findViewById(id) != null) return true;
        }
        return false;
    }

    private static View findViewById(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        return id != 0 ? activity.findViewById(id) : null;
    }

    private static View findTweetRowContainer(Activity activity, View view) {
        int rowId = activity.getResources().getIdentifier("outer_layout_row_view_tweet", "id", activity.getPackageName());
        View ancestor = rowId != 0 ? findAncestorById(view, rowId) : null;
        return ancestor != null ? ancestor : findAncestorByTweetView(view);
    }

    private static View findAncestorById(View view, int id) {
        for (View v = view; v != null; v = getParent(v)) if (v.getId() == id) return v;
        return null;
    }

    private static View findAncestorByTweetView(View view) {
        for (View v = view; v != null; v = getParent(v)) if (isTweetView(v)) return v;
        return null;
    }

    private static boolean isTweetView(View v) {
        String name = v.getClass().getName();
        return !name.contains("TweetViewContentHostContainer") && (name.endsWith("TweetView") || name.contains(".TweetView"));
    }

    private static ViewGroup findThreadContainer(View view) {
        for (View v = view; v != null; v = getParent(v)) {
            if (v instanceof ViewGroup && (v instanceof ListView || v.getClass().getName().contains("RecyclerView"))) {
                return (ViewGroup) v;
            }
        }
        return null;
    }

    private static Rect computeThreadBounds(Activity activity, ViewGroup container, View tweetRow) {
        View itemRoot = findItemRoot(container, tweetRow);
        if (itemRoot == null) return null;

        int targetIdx = indexOfChild(container, itemRoot);
        if (targetIdx < 0) return null;

        int topId = activity.getResources().getIdentifier("tweet_connector_top", "id", activity.getPackageName());
        int botId = activity.getResources().getIdentifier("tweet_connector_bottom", "id", activity.getPackageName());

        int startIdx = targetIdx;
        while (startIdx > 0 && isTweetItem(container.getChildAt(startIdx - 1)) && (hasVisible(container.getChildAt(startIdx), topId) || hasVisible(container.getChildAt(startIdx - 1), botId))) {
            startIdx--;
        }

        int endIdx = targetIdx;
        if (!hasVisible(tweetRow, activity.getResources().getIdentifier("tweet_reply_context", "id", activity.getPackageName()))) {
            while (endIdx < container.getChildCount() - 1 && isTweetItem(container.getChildAt(endIdx + 1)) && (hasVisible(container.getChildAt(endIdx + 1), topId) || hasVisible(container.getChildAt(endIdx), botId))) {
                endIdx++;
            }
        }

        int top = container.getChildAt(startIdx).getTop();
        View lastView = container.getChildAt(endIdx);
        int bottom = lastView.getBottom();

        int adjustedLastBottom = getBottomWithoutDivider(activity, lastView);
        if (adjustedLastBottom < lastView.getHeight()) {
            bottom = lastView.getTop() + adjustedLastBottom;
        }

        int sortId = activity.getResources().getIdentifier("reply_sorting", "id", activity.getPackageName());
        View sortView = sortId != 0 ? container.getChildAt(endIdx).findViewById(sortId) : null;
        if (sortView != null && sortView.getVisibility() == View.VISIBLE) {
            bottom = Math.min(bottom, container.getChildAt(endIdx).getTop() + sortView.getTop());
        }

        return bottom > top ? new Rect(0, top, container.getWidth(), bottom) : null;
    }

    private static boolean hasVisible(View v, int id) {
        if (id == 0) return false;
        View child = v.findViewById(id);
        return child != null && child.getVisibility() == View.VISIBLE;
    }

    private static int indexOfChild(ViewGroup parent, View child) {
        for (int i = 0; i < parent.getChildCount(); i++) if (parent.getChildAt(i) == child) return i;
        return -1;
    }

    private static View findItemRoot(ViewGroup container, View view) {
        View v = view;
        while (getParent(v) != null && getParent(v) != container) v = getParent(v);
        return getParent(v) == container ? v : null;
    }

    private static boolean isTweetItem(View v) {
        if (v == null) return false;
        if (isTweetView(v)) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (isTweetItem(g.getChildAt(i))) return true;
        }
        return false;
    }

    private static View getParent(View v) {
        return (v.getParent() instanceof View) ? (View) v.getParent() : null;
    }

    private static View expandToTweetContainer(Activity activity, View root, View tweetView) {
        if (tweetView == null) return null;
        int minW = dp(activity, 240), minH = dp(activity, 200), maxH = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.9f);
        View best = null;
        int maxArea = 0;

        for (View v = tweetView; v != null && v != root && !isScroll(v); v = getParent(v)) {
            if (v.getWidth() >= minW && v.getHeight() >= minH && v.getHeight() <= maxH) {
                int area = v.getWidth() * v.getHeight();
                if (area > maxArea) {
                    best = v;
                    maxArea = area;
                }
            }
        }
        return best;
    }

    private static boolean isScroll(View v) {
        return v instanceof ScrollView || v instanceof ListView || v.getClass().getName().contains("RecyclerView") || v.getClass().getName().contains("NestedScrollView");
    }

    private static int dp(Activity a, int dp) {
        return Math.round(dp * a.getResources().getDisplayMetrics().density);
    }

    private static void dumpViewTree(Activity activity, View v, int depth) {
        if (v == null || v.getVisibility() != View.VISIBLE || depth > 20) return;
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append("| ");
        
        int id = v.getId();
        String idName = (id != View.NO_ID && id != 0) ? activity.getResources().getResourceEntryName(id) : "";
        String className = v.getClass().getSimpleName();
        
        // Compact format: Class [WxH] @L,T #ID
        sb.append(String.format("%s [%dx%d] @%d,%d%s", 
            className, v.getWidth(), v.getHeight(), v.getLeft(), v.getTop(), 
            idName.isEmpty() ? "" : " #" + idName));
        
        Utils.logger(sb.toString());
        
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpViewTree(activity, group.getChildAt(i), depth + 1);
            }
        }
    }

    private static class CaptureTarget {
        View view; Rect clipRect;
        CaptureTarget(View v, Rect r) { view = v; clipRect = r; }
    }
}
