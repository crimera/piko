package app.morphe.extension.twitter.patches.nativeFeatures.shareImage;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;

import app.morphe.extension.twitter.Utils;
import app.morphe.extension.twitter.entity.Tweet;
import app.morphe.extension.twitter.utils.ViewUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;

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

            // Attempt to find tweet view container
            android.view.View tweetView = findTweetViewInHierarchy(rootView, tweet);
            tweetView = expandToTweetContainer(activity, rootView, tweetView);

            if (tweetView == null) {
                // Fallback: capture entire screen
                Utils.toast("Tweet view not found, capturing screen...");
                tweetView = rootView;
            }

            // Convert to bitmap
            Bitmap bitmap = ViewUtils.viewToBitmap(tweetView);

            // Save to file
            File outputFile = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Piko/" + "tweet_" + tweet.getTweetId() + ".png"
            );

            ViewUtils.saveBitmap(bitmap, outputFile);
            Utils.toast("Saved to Downloads/Piko");

        } catch (Exception e) {
            Utils.logger(e);
            Utils.toast("Share failed: " + e.getMessage());
        }
    }

    /**
     * Find tweet view in view hierarchy by traversing tree
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

    private static android.view.View expandToTweetContainer(
        Activity activity,
        android.view.View rootView,
        android.view.View tweetView
    ) {
        if (tweetView == null) return null;

        int minWidth = dpToPx(activity, 240);
        int minHeight = dpToPx(activity, 200);
        android.view.View candidate = tweetView;

        while (candidate != null && candidate != rootView) {
            if (candidate.getWidth() >= minWidth && candidate.getHeight() >= minHeight) {
                return candidate;
            }
            if (!(candidate.getParent() instanceof android.view.View)) {
                break;
            }
            candidate = (android.view.View) candidate.getParent();
        }

        if (candidate != null && candidate.getWidth() >= minWidth && candidate.getHeight() >= minHeight) {
            return candidate;
        }

        return null;
    }

    private static int dpToPx(Activity activity, int dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
    }

}
