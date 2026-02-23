package app.morphe.extension.twitter.patches;

import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.lang.reflect.Field;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

@SuppressWarnings("unused")
public class InlineDownloadButton {

    private static final String TAG = "InlineDownloadButton";

    /**
     * Returns the obfuscated field name for mTweet.
     * "mTweet" is replaced at patch time via changeFirstString().
     */
    private static String getTweetFieldName() {
        return "mTweet";
    }

    /**
     * Called from patched onFinishInflate() of InlineActionBar.
     * Defers the actual work to post() so the view is fully attached.
     */
    public static void onFinishInflate(ViewGroup inlineActionBar) {
        inlineActionBar.post(() -> wrapWithDownloadButton(inlineActionBar));
    }

    private static void wrapWithDownloadButton(ViewGroup inlineActionBar) {
        // Don't wrap twice (RecyclerView recycling)
        if (inlineActionBar.getParent() instanceof LinearLayout) {
            LinearLayout existingWrapper = (LinearLayout) inlineActionBar.getParent();
            if ("piko_download_wrapper".equals(existingWrapper.getTag())) {
                return;
            }
        }

        ViewGroup parent = (ViewGroup) inlineActionBar.getParent();
        if (parent == null) return;

        try {
            // 1. Remember position and layout params
            int index = parent.indexOfChild(inlineActionBar);
            ViewGroup.LayoutParams originalLp = inlineActionBar.getLayoutParams();

            // 2. Remove InlineActionBar from parent
            parent.removeView(inlineActionBar);

            // 3. Create horizontal wrapper
            LinearLayout wrapper = new LinearLayout(inlineActionBar.getContext());
            wrapper.setOrientation(LinearLayout.HORIZONTAL);
            wrapper.setTag("piko_download_wrapper");
            wrapper.setGravity(android.view.Gravity.CENTER_VERTICAL);

            // 4. Add InlineActionBar back — it keeps its original behavior
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            wrapper.addView(inlineActionBar, barLp);

            // 5. Create download button — match the native icon style
            ImageView downloadBtn = new ImageView(inlineActionBar.getContext());
            int iconId = Utils.getResourceIdentifier("ic_vector_incoming", "drawable");
            if (iconId != 0) {
                downloadBtn.setImageResource(iconId);
                android.util.Log.e(TAG, "icon loaded: " + iconId);
            } else {
                android.util.Log.e(TAG, "icon NOT found!");
            }
            downloadBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            downloadBtn.setClickable(true);
            downloadBtn.setFocusable(true);
            downloadBtn.setId(View.generateViewId());

            // Match icon tint from existing action button icons
            ColorStateList tint = null;
            for (int i = 0; i < inlineActionBar.getChildCount(); i++) {
                View child = inlineActionBar.getChildAt(i);
                android.util.Log.e(TAG, "child[" + i + "]: " + child.getClass().getSimpleName()
                        + " visible=" + (child.getVisibility() == View.VISIBLE)
                        + " w=" + child.getWidth() + " h=" + child.getHeight());
                try {
                    ImageView existingIcon = (ImageView) child.getClass()
                            .getMethod("getIconView").invoke(child);
                    if (existingIcon != null) {
                        ColorStateList childTint = existingIcon.getImageTintList();
                        int currentColor = existingIcon.getImageTintList() != null
                                ? existingIcon.getImageTintList().getDefaultColor() : -1;
                        android.util.Log.e(TAG, "  iconView tintList=" + childTint
                                + " defaultColor=0x" + Integer.toHexString(currentColor)
                                + " drawable=" + existingIcon.getDrawable());
                        if (childTint != null && tint == null) {
                            tint = childTint;
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e(TAG, "  getIconView failed: " + e.getMessage());
                }
            }

            if (tint != null) {
                downloadBtn.setImageTintList(tint);
                android.util.Log.e(TAG, "applied tint: defaultColor=0x"
                        + Integer.toHexString(tint.getDefaultColor()));
            } else {
                // Fallback: resolve colorControlNormal from theme
                android.util.Log.e(TAG, "no tint from children, trying theme attr");
                TypedValue tv = new TypedValue();
                boolean resolved = inlineActionBar.getContext().getTheme()
                        .resolveAttribute(android.R.attr.colorControlNormal, tv, true);
                if (resolved) {
                    int color = inlineActionBar.getContext().getColor(tv.resourceId);
                    downloadBtn.setImageTintList(ColorStateList.valueOf(color));
                    android.util.Log.e(TAG, "fallback tint from colorControlNormal: 0x"
                            + Integer.toHexString(color));
                } else {
                    android.util.Log.e(TAG, "colorControlNormal not resolved!");
                }
            }

            // Size: match inline action button dimensions
            float density = inlineActionBar.getResources().getDisplayMetrics().density;
            int touchTarget = (int) (36 * density);
            int padding = (int) (8 * density);
            downloadBtn.setPadding(padding, padding, padding, padding);

            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    touchTarget, ViewGroup.LayoutParams.MATCH_PARENT);
            btnLp.gravity = android.view.Gravity.CENTER_VERTICAL;
            wrapper.addView(downloadBtn, btnLp);

            // 6. Click handler
            downloadBtn.setOnClickListener(v -> {
                try {
                    Field tweetField = inlineActionBar.getClass()
                            .getDeclaredField(getTweetFieldName());
                    tweetField.setAccessible(true);
                    Object tweet = tweetField.get(inlineActionBar);

                    if (tweet == null) {
                        Utils.showToastShort("No tweet data");
                        return;
                    }

                    String info = tweet.toString();
                    Utils.showToastShort("Download: " +
                            info.substring(0, Math.min(80, info.length())));

                } catch (Exception e) {
                    Logger.printException(() -> "InlineDownloadButton: click error", e);
                }
            });

            // 7. Insert wrapper at same position with original layout params
            parent.addView(wrapper, index, originalLp);

            android.util.Log.e(TAG, "done! wrapper added at index=" + index);

        } catch (Exception e) {
            android.util.Log.e(TAG, "wrap failed: " + e);
            e.printStackTrace();
        }
    }
}
