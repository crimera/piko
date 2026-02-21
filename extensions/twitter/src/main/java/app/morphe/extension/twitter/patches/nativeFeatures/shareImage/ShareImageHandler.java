package app.morphe.extension.twitter.patches.nativeFeatures.shareImage;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Environment;

import app.morphe.extension.twitter.Utils;
import app.morphe.extension.twitter.entity.Tweet;
import app.morphe.extension.twitter.utils.ViewUtils;

import java.io.File;

public class ShareImageHandler {

    /**
     * Main entry point called from patched code
     * Receives Context (Activity) and tweet object
     */
    public static void shareAsImage(Context context, Object tweetObj) {
        if (!(context instanceof Activity)) {
            Utils.toast("Invalid context");
            return;
        }

        Activity activity = (Activity) context;

        try {
            Tweet tweet = new Tweet(tweetObj);

            // Render tweet view to image
            Utils.toast("Capturing tweet view...");
            shareTweetAsRenderedImage(activity, tweet);

        } catch (Exception e) {
            Utils.logger(e);
            Utils.toast("Error: " + e.getMessage());
        }
    }

    /**
     * Render tweet as image and share
     * This attempts to find and capture the tweet view in the UI tree
     */
    private static void shareTweetAsRenderedImage(Activity activity, Tweet tweet) {
        try {
            // Get root view
            android.view.View rootView = activity.getWindow().getDecorView().getRootView();

            android.view.View tweetView = findTweetViewInHierarchy(rootView, tweet);
            CaptureTarget captureTarget = resolveCaptureTarget(activity, rootView, tweetView);

            if (captureTarget == null) {
                Utils.toast("Tweet view not found, capturing screen...");
                captureTarget = new CaptureTarget(rootView, null);
            }

            Bitmap bitmap = captureTarget.clipRect == null
                ? ViewUtils.viewToBitmap(captureTarget.view)
                : ViewUtils.viewToBitmap(captureTarget.view, captureTarget.clipRect);

            // Share via MediaStore
            shareImage(activity, bitmap, "tweet_" + tweet.getTweetId());

        } catch (Exception e) {
            Utils.logger(e);
            Utils.toast("Share failed: " + e.getMessage());
        }
    }

    private static void shareImage(Activity activity, Bitmap bitmap, String filename) {
        try {
            cleanupOldFiles(activity);

            android.content.ContentResolver resolver = activity.getContentResolver();
            String displayName = filename + ".png";

            // Delete existing file if it exists to "overwrite"
            try {
                String selection = android.provider.MediaStore.MediaColumns.DISPLAY_NAME + " = ? AND " +
                                   android.provider.MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
                String[] selectionArgs = new String[]{displayName, "Pictures/Piko/%"};
                resolver.delete(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs);
            } catch (Exception e) {
                // Ignore if not found
            }

            android.content.ContentValues contentValues = new android.content.ContentValues();
            contentValues.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName);
            contentValues.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png");

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                contentValues.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Piko");
                contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1);
            }

            android.net.Uri uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

            if (uri == null) {
                Utils.toast("Failed to create MediaStore entry");
                return;
            }

            try (java.io.OutputStream os = resolver.openOutputStream(uri)) {
                if (os != null) {
                    bitmap.compress(Bitmap.Config.ARGB_8888.equals(bitmap.getConfig()) ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 100, os);
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                contentValues.clear();
                contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(uri, contentValues, null, null);
            }

            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);

            activity.startActivity(android.content.Intent.createChooser(intent, "Share Tweet Image"));

        } catch (Exception e) {
            Utils.logger(e);
            Utils.toast("Failed to share: " + e.getMessage());
        }
    }

    /**
     * Delete files in Piko folder older than 24 hours
     */
    private static void cleanupOldFiles(android.content.Context context) {
        try {
            long cutoff = (System.currentTimeMillis() - (24 * 60 * 60 * 1000)) / 1000;
            android.content.ContentResolver resolver = context.getContentResolver();

            String selection = android.provider.MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND " +
                               android.provider.MediaStore.MediaColumns.DATE_ADDED + " < ?";
            String[] selectionArgs = new String[]{"Pictures/Piko/%", String.valueOf(cutoff)};

            int deleted = resolver.delete(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs);
            if (deleted > 0) {
                Utils.logger("Cleaned up " + deleted + " old share files");
            }
        } catch (Exception e) {
            // Fail silently
        }
    }

    /**
     * Find tweet view in hierarchy
     * Returns null if not found
     */
    private static android.view.View findTweetViewInHierarchy(
            android.view.View root,
            Tweet tweet) {

        try {
            Long tweetId = tweet.getTweetId();
            return searchViewTree(root, tweetId);
        } catch (Exception e) {
            Utils.logger(e);
            return null;
        }
    }

    /**
     * Recursive search through ViewGroup hierarchy
     */
    private static android.view.View searchViewTree(android.view.View view, Long targetId) {
        try {
            // Check if this view has tweet ID tag
            Object tag = view.getTag();
            if (tag instanceof Long && ((Long) tag).equals(targetId)) {
                return view;
            }
            if (tag instanceof String && tag.toString().contains(targetId.toString())) {
                return view;
            }

            // Recursively search children
            if (view instanceof android.view.ViewGroup) {
                android.view.ViewGroup group = (android.view.ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    android.view.View result = searchViewTree(group.getChildAt(i), targetId);
                    if (result != null) return result;
                }
            }
        } catch (Exception e) {
            // Silently continue
        }

        return null;
    }

    private static final class CaptureTarget {
        private final android.view.View view;
        private final Rect clipRect;

        private CaptureTarget(android.view.View view, Rect clipRect) {
            this.view = view;
            this.clipRect = clipRect;
        }
    }

    private static CaptureTarget resolveCaptureTarget(
        Activity activity,
        android.view.View rootView,
        android.view.View tweetView
    ) {
        if (tweetView == null) return null;

        android.view.View tweetRow = findTweetRowContainer(activity, tweetView);
        if (tweetRow == null) tweetRow = tweetView;

        if (isTweetDetailScreen(activity)) {
            android.view.ViewGroup threadContainer = findThreadContainer(tweetRow);
            if (threadContainer != null) {
                Rect threadBounds = computeThreadBounds(activity, threadContainer, tweetRow);
                if (threadBounds != null) {
                    return new CaptureTarget(threadContainer, threadBounds);
                }
            }
        }

        android.view.View expanded = expandToTweetContainer(activity, rootView, tweetRow);
        if (expanded != null) return new CaptureTarget(expanded, null);

        return new CaptureTarget(tweetRow, null);
    }

    private static boolean isTweetDetailScreen(Activity activity) {
        return findViewById(activity, "root_coordinator_layout") != null
            || findViewById(activity, "fragment_container") != null
            || findViewById(activity, "persistent_reply") != null;
    }

    private static android.view.View findViewById(Activity activity, String name) {
        int id = resolveId(activity, name);
        if (id == 0) return null;
        return activity.findViewById(id);
    }

    private static int resolveId(Activity activity, String name) {
        return activity.getResources().getIdentifier(name, "id", activity.getPackageName());
    }

    private static android.view.View findTweetRowContainer(Activity activity, android.view.View view) {
        int rowId = resolveId(activity, "outer_layout_row_view_tweet");
        if (rowId != 0) {
            android.view.View byId = findAncestorById(view, rowId);
            if (byId != null) return byId;
        }

        return findAncestorByTweetView(view);
    }

    private static android.view.View findAncestorById(android.view.View view, int id) {
        android.view.View current = view;
        while (current != null) {
            if (current.getId() == id) return current;
            current = getParentView(current);
        }
        return null;
    }

    private static android.view.View findAncestorByTweetView(android.view.View view) {
        android.view.View current = view;
        while (current != null) {
            if (isTweetView(current)) return current;
            current = getParentView(current);
        }
        return null;
    }

    private static boolean isTweetView(android.view.View view) {
        String name = view.getClass().getName();
        if (name.contains("TweetViewContentHostContainer")) return false;
        return name.endsWith("TweetView") || name.endsWith("LinearLayoutTweetView") || name.contains(".TweetView");
    }

    private static android.view.ViewGroup findThreadContainer(android.view.View view) {
        android.view.View current = view;
        while (current != null) {
            if (current instanceof android.view.ViewGroup && isThreadContainer(current)) {
                return (android.view.ViewGroup) current;
            }
            current = getParentView(current);
        }
        return null;
    }

    private static boolean isThreadContainer(android.view.View view) {
        if (view instanceof android.widget.ListView) return true;
        String name = view.getClass().getName();
        return name.contains("RecyclerView");
    }

    private static Rect computeThreadBounds(
        Activity activity,
        android.view.ViewGroup container,
        android.view.View tweetRow
    ) {
        android.view.View itemRoot = findItemRoot(container, tweetRow);
        if (itemRoot == null) return null;

        int targetIndex = indexOfChild(container, itemRoot);
        if (targetIndex < 0) return null;

        int topConnectorId = resolveId(activity, "tweet_connector_top");
        int bottomConnectorId = resolveId(activity, "tweet_connector_bottom");

        int startIndex = targetIndex;
        while (startIndex > 0) {
            android.view.View current = container.getChildAt(startIndex);
            android.view.View previous = container.getChildAt(startIndex - 1);
            if (!isTweetItem(previous)) break;

            boolean connected =
                hasVisibleConnector(current, topConnectorId)
                    || hasVisibleConnector(previous, bottomConnectorId);

            if (!connected) break;
            startIndex--;
        }

        int endIndex = targetIndex;
        if (!hasVisibleReplyContext(activity, tweetRow)) {
            while (endIndex < container.getChildCount() - 1) {
                android.view.View current = container.getChildAt(endIndex);
                android.view.View next = container.getChildAt(endIndex + 1);
                if (!isTweetItem(next)) break;

                boolean connected =
                    hasVisibleConnector(next, topConnectorId)
                        || hasVisibleConnector(current, bottomConnectorId);

                if (!connected) break;
                endIndex++;
            }
        }

        android.view.View startView = container.getChildAt(startIndex);
        android.view.View endView = container.getChildAt(endIndex);

        int top = startView.getTop();
        int bottom = endView.getBottom();

        int replySortingId = resolveId(activity, "reply_sorting");
        if (replySortingId != 0) {
            android.view.View replySorting = endView.findViewById(replySortingId);
            if (replySorting != null && replySorting.getVisibility() == android.view.View.VISIBLE) {
                int candidateBottom = endView.getTop() + replySorting.getTop();
                if (candidateBottom > top) {
                    bottom = Math.min(bottom, candidateBottom);
                }
            }
        }

        if (bottom <= top) return null;

        return new Rect(0, top, container.getWidth(), bottom);
    }

    private static boolean hasVisibleConnector(android.view.View view, int id) {
        if (id == 0) return false;
        android.view.View connector = view.findViewById(id);
        return connector != null && connector.getVisibility() == android.view.View.VISIBLE;
    }

    private static boolean hasVisibleReplyContext(Activity activity, android.view.View view) {
        int replyContextId = resolveId(activity, "tweet_reply_context");
        if (replyContextId == 0) return false;
        android.view.View replyContext = view.findViewById(replyContextId);
        return replyContext != null && replyContext.getVisibility() == android.view.View.VISIBLE;
    }

    private static int indexOfChild(android.view.ViewGroup container, android.view.View child) {
        for (int i = 0; i < container.getChildCount(); i++) {
            if (container.getChildAt(i) == child) return i;
        }
        return -1;
    }

    private static android.view.View findItemRoot(android.view.ViewGroup container, android.view.View view) {
        android.view.View current = view;
        android.view.View parent = getParentView(current);
        while (parent != null && parent != container) {
            current = parent;
            parent = getParentView(current);
        }
        return parent == container ? current : null;
    }

    private static boolean isTweetItem(android.view.View view) {
        if (view == null) return false;
        if (isTweetView(view)) return true;
        return containsTweetView(view);
    }

    private static boolean containsTweetView(android.view.View view) {
        if (isTweetView(view)) return true;
        if (!(view instanceof android.view.ViewGroup)) return false;

        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsTweetView(group.getChildAt(i))) return true;
        }
        return false;
    }

    private static android.view.View getParentView(android.view.View view) {
        if (view.getParent() instanceof android.view.View) {
            return (android.view.View) view.getParent();
        }
        return null;
    }

    private static android.view.View expandToTweetContainer(
        Activity activity,
        android.view.View rootView,
        android.view.View tweetView
    ) {
        if (tweetView == null) return null;

        int minWidth = dpToPx(activity, 240);
        int minHeight = dpToPx(activity, 200);
        int maxHeight = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.9f);

        android.view.View candidate = tweetView;
        android.view.View best = null;
        int bestArea = 0;

        while (candidate != null && candidate != rootView) {
            int width = candidate.getWidth();
            int height = candidate.getHeight();

            if (!isScrollContainer(candidate) && width >= minWidth && height >= minHeight && height <= maxHeight) {
                int area = width * height;
                if (area > bestArea) {
                    best = candidate;
                    bestArea = area;
                }
            }

            android.view.View parent = null;
            if (candidate.getParent() instanceof android.view.View) {
                parent = (android.view.View) candidate.getParent();
            }

            if (parent == null) break;
            if (isScrollContainer(parent)) break;

            candidate = parent;
        }

        if (best != null) return best;

        if (candidate != null && !isScrollContainer(candidate)) {
            int width = candidate.getWidth();
            int height = candidate.getHeight();
            if (width >= minWidth && height >= minHeight && height <= maxHeight) {
                return candidate;
            }
        }

        return null;
    }

    private static boolean isScrollContainer(android.view.View view) {
        if (view instanceof android.widget.ScrollView) return true;
        if (view instanceof android.widget.ListView) return true;

        String name = view.getClass().getName();
        return name.contains("RecyclerView") || name.contains("NestedScrollView");
    }

    private static int dpToPx(Activity activity, int dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
    }

}
