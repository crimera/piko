package app.morphe.extension.twitter.patches.nativeFeatures.shareImage;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import app.morphe.extension.twitter.Utils;
import app.morphe.extension.twitter.entity.Tweet;
import app.morphe.extension.twitter.utils.ViewUtils;

import java.io.OutputStream;
import java.util.List;

public class ShareImageHandler {

    private static final String ID_INLINE_ACTIONS = "tweet_inline_actions";
    private static final String ID_STATS_CONTAINER = "stats_container";
    private static final String ID_OUTER_ROW = "outer_layout_row_view_tweet";

    public static void shareAsImage(Context context, Object tweetObj) {
        if (!(context instanceof Activity)) {
            Utils.toast("Invalid context");
            return;
        }

        Activity activity = (Activity) context;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread(() -> shareAsImage(activity, tweetObj));
            return;
        }

        try {
            Tweet tweet = new Tweet(tweetObj);
            Utils.toast("Capturing tweet...");
            
            View rootView = activity.getWindow().getDecorView().getRootView();
            View tweetView = searchViewTree(rootView, tweet.getTweetId(), 0);
            CaptureTarget target = resolveCaptureTarget(activity, rootView, tweetView);

            if (target == null) target = new CaptureTarget(rootView, null);

            Utils.logger(String.format("Capture: Tweet %d | Density %.1f", 
                tweet.getTweetId(), activity.getResources().getDisplayMetrics().density));
            Utils.logger("Target: " + target.view.getClass().getSimpleName() + " " + target.view.getWidth() + "x" + target.view.getHeight());
            
            if (target.clipRect != null) {
                Utils.logger("Clip: " + target.clipRect.toShortString());
            }
            
            dumpViewTree(activity, target.view, 0);

            Bitmap bitmap = null;
            try {
                ViewUtils.setScrollbarsVisible(target.view, false);
                bitmap = ViewUtils.viewToBitmap(target.view, target.clipRect);
            } finally {
                ViewUtils.setScrollbarsVisible(target.view, true);
            }
            
            if (bitmap == null) {
                Utils.toast("Failed to capture image");
                return;
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
                ViewUtils.saveBitmap(bitmap, os);
            } finally {
                bitmap.recycle();
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                intent.setClipData(ClipData.newRawUri("image", uri));
            }

            Intent chooser = Intent.createChooser(intent, "Share Tweet Image");
            
            // Grant URI permission to all resolved activities
            PackageManager pm = activity.getPackageManager();
            List<ResolveInfo> resInfoList = pm.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                activity.grantUriPermission(resolveInfo.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            activity.startActivity(chooser);
        } catch (Exception e) {
            Utils.logger(e);
        }
    }

    private static void cleanupOldFiles(Context context) {
        try {
            long cutoff = (System.currentTimeMillis() - (24 * 60 * 60 * 1000)) / 1000;
            String selection = MediaStore.MediaColumns.DATE_ADDED + " < ?";
            String[] args = new String[]{String.valueOf(cutoff)};
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                selection += " AND " + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
                args = new String[]{String.valueOf(cutoff), "Pictures/Piko/%"};
            }
            context.getContentResolver().delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, args);
        } catch (Exception ignored) {}
    }

    private static View searchViewTree(View view, Long targetId, int depth) {
        if (view == null || depth > 50) return null;

        Object tag = view.getTag();
        if (tag instanceof Long && tag.equals(targetId)) return view;
        if (tag instanceof String && tag.equals(targetId.toString())) return view;

        if (!(view instanceof ViewGroup)) return null;
        
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View result = searchViewTree(group.getChildAt(i), targetId, depth + 1);
            if (result != null) return result;
        }
        return null;
    }

    private static CaptureTarget resolveCaptureTarget(Activity activity, View rootView, View tweetView) {
        if (tweetView == null) return null;

        View tweetRow = findTweetRowContainer(activity, tweetView);
        if (tweetRow == null) tweetRow = tweetView;

        if (isTweetDetailScreen(activity)) {
            ViewGroup thread = findThreadContainer(tweetRow);
            Rect bounds = (thread != null) ? computeThreadBounds(activity, thread, tweetRow) : null;
            if (bounds != null) return new CaptureTarget(thread, bounds);
        }

        View expanded = expandToTweetContainer(activity, rootView, tweetRow);
        View target = (expanded != null) ? expanded : tweetRow;
        
        int adjustedBottom = getBottomWithoutDivider(activity, target);
        if (adjustedBottom >= target.getHeight()) {
            return new CaptureTarget(target, null);
        }

        return new CaptureTarget(target, new Rect(0, 0, target.getWidth(), adjustedBottom));
    }

    private static int getBottomWithoutDivider(Activity activity, View target) {
        int h = target.getHeight();

        // 1. Anchor-based cropping (Priority)
        for (String name : new String[]{ID_INLINE_ACTIONS, ID_STATS_CONTAINER}) {
            View anchor = findViewById(target, name);
            if (anchor == null || anchor.getVisibility() != View.VISIBLE) continue;
            
            int crop = getRelativeBottom(anchor, target) - dp(activity, 1);
            if (crop > 0 && crop < h) {
                Utils.logger("Cropped at #" + name);
                return crop;
            }
        }

        // 2. Linear slop removal (Iterative)
        if (!(target instanceof ViewGroup)) return h;
        
        ViewGroup g = (ViewGroup) target;
        int bot = h;
        for (int i = g.getChildCount() - 1; i >= 0; i--) {
            View v = g.getChildAt(i);
            if (v.getVisibility() != View.VISIBLE) continue;
            if (v.getHeight() <= dp(activity, 4) && v.getBottom() >= bot - dp(activity, 1)) {
                bot = v.getTop();
                Utils.logger("Removed slop: " + v.getClass().getSimpleName());
            } else break;
        }
        return bot;
    }

    private static View findViewById(View root, String name) {
        int id = app.morphe.extension.shared.Utils.getResourceIdentifier(name, "id");
        return id != 0 ? root.findViewById(id) : null;
    }

    private static int getRelativeBottom(View child, View parent) {
        int bottom = child.getBottom();
        View current = getParent(child);
        while (current != null && current != parent) {
            bottom += current.getTop();
            current = getParent(current);
        }
        return bottom;
    }

    private static boolean isTweetDetailScreen(Activity activity) {
        View root = activity.getWindow().getDecorView();
        for (String s : new String[]{"persistent_reply", ID_STATS_CONTAINER}) {
            if (hasVisible(root, s)) return true;
        }
        return false;
    }

    private static View findTweetRowContainer(Activity activity, View view) {
        int rowId = app.morphe.extension.shared.Utils.getResourceIdentifier(ID_OUTER_ROW, "id");
        View ancestor = rowId != 0 ? findAncestorById(view, rowId) : null;
        return ancestor != null ? ancestor : findAncestorByTweetView(view);
    }

    private static View findAncestorById(View view, int id) {
        for (View v = view; v != null; v = getParent(v)) {
            if (v.getId() == id) return v;
        }
        return null;
    }

    private static View findAncestorByTweetView(View view) {
        for (View v = view; v != null; v = getParent(v)) {
            if (isTweetView(v)) return v;
        }
        return null;
    }

    private static boolean isTweetView(View v) {
        String name = v.getClass().getName();
        return !name.contains("TweetViewContentHostContainer") && (name.endsWith("TweetView") || name.contains(".TweetView"));
    }

    private static ViewGroup findThreadContainer(View view) {
        for (View v = view; v != null; v = getParent(v)) {
            if (v instanceof ListView || v.getClass().getName().contains("RecyclerView")) {
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

        int topId = app.morphe.extension.shared.Utils.getResourceIdentifier("tweet_connector_top", "id");
        int botId = app.morphe.extension.shared.Utils.getResourceIdentifier("tweet_connector_bottom", "id");

        int startIdx = targetIdx;
        while (startIdx > 0 && isTweetItem(container.getChildAt(startIdx - 1)) && (hasVisible(container.getChildAt(startIdx), topId) || hasVisible(container.getChildAt(startIdx - 1), botId))) {
            startIdx--;
        }

        int endIdx = targetIdx;
        if (!hasVisible(tweetRow, "tweet_reply_context")) {
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

        View sortView = findViewById(container.getChildAt(endIdx), "reply_sorting");
        if (sortView != null && sortView.getVisibility() == View.VISIBLE) {
            bottom = Math.min(bottom, container.getChildAt(endIdx).getTop() + sortView.getTop());
        }

        return (bottom > top) ? new Rect(0, top, container.getWidth(), bottom) : null;
    }

    private static boolean hasVisible(View v, int id) {
        if (id == 0) return false;
        View child = v.findViewById(id);
        return child != null && child.getVisibility() == View.VISIBLE;
    }

    private static boolean hasVisible(View v, String name) {
        View child = findViewById(v, name);
        return child != null && child.getVisibility() == View.VISIBLE;
    }

    private static int indexOfChild(ViewGroup parent, View child) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i) == child) return i;
        }
        return -1;
    }

    private static View findItemRoot(ViewGroup container, View view) {
        View v = view;
        while (getParent(v) != null && getParent(v) != container) {
            v = getParent(v);
        }
        return getParent(v) == container ? v : null;
    }

    private static boolean isTweetItem(View v) {
        if (v == null) return false;
        if (isTweetView(v)) return true;
        if (!(v instanceof ViewGroup)) return false;
        
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) {
            if (isTweetItem(g.getChildAt(i))) return true;
        }
        return false;
    }

    private static View getParent(View v) {
        if (v == null) return null;
        Object p = v.getParent();
        return (p instanceof View) ? (View) p : null;
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
        return v instanceof android.widget.ScrollView || v instanceof android.widget.ListView || v.getClass().getName().contains("RecyclerView") || v.getClass().getName().contains("NestedScrollView");
    }

    private static int dp(Activity a, int dp) {
        return Math.round(dp * a.getResources().getDisplayMetrics().density);
    }

    private static void dumpViewTree(Activity activity, View v, int depth) {
        if (v == null || v.getVisibility() != View.VISIBLE || depth > 20) return;
        
        String idName = "";
        try {
            int id = v.getId();
            if (id != View.NO_ID && id != 0) idName = activity.getResources().getResourceEntryName(id);
        } catch (Resources.NotFoundException ignored) {}
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append("| ");
        sb.append(String.format("%s [%dx%d] @%d,%d%s", 
            v.getClass().getSimpleName(), v.getWidth(), v.getHeight(), v.getLeft(), v.getTop(), 
            idName.isEmpty() ? "" : " #" + idName));
        
        Utils.logger(sb.toString());
        
        if (v instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpViewTree(activity, group.getChildAt(i), depth + 1);
            }
        }
    }

    private static class CaptureTarget {
        final View view; final Rect clipRect;
        CaptureTarget(View v, Rect r) { view = v; clipRect = r; }
    }
}
