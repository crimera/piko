package app.morphe.extension.twitter.patches;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
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
     * Returns the obfuscated field name for mTweet in InlineActionBar.
     * "mTweet" is replaced at patch time via changeFirstString().
     */
    private static String getTweetFieldName() {
        return "mTweet";
    }

    /**
     * Returns the obfuscated field name for mIconDrawableColorStateList in InlineActionView.
     * "mIconDrawableColorStateList" is replaced at patch time via changeFirstString().
     */
    private static String getIconColorFieldName() {
        return "mIconDrawableColorStateList";
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

            // 5. Create download button
            ImageView downloadBtn = new ImageView(inlineActionBar.getContext());
            int iconId = Utils.getResourceIdentifier("ic_vector_incoming", "drawable");
            if (iconId != 0) {
                downloadBtn.setImageResource(iconId);
            }
            downloadBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            downloadBtn.setClickable(true);
            downloadBtn.setFocusable(true);
            downloadBtn.setId(View.generateViewId());

            // 6. Match icon tint, size, and layout from an existing InlineActionView
            String colorFieldName = getIconColorFieldName();
            ColorStateList iconColor = null;
            int childWidth = 0;
            int childHeight = 0;
            int iconPadding = 0;
            ImageView.ScaleType scaleType = ImageView.ScaleType.CENTER_INSIDE;

            for (int i = 0; i < inlineActionBar.getChildCount(); i++) {
                View child = inlineActionBar.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE && child.getWidth() > 0) {
                    if (childWidth == 0) {
                        childWidth = child.getWidth();
                        childHeight = child.getHeight();
                        // Try to find the internal ImageView to copy its padding/scale
                        if (child instanceof ViewGroup) {
                            ViewGroup vg = (ViewGroup) child;
                            for (int j = 0; j < vg.getChildCount(); j++) {
                                View c2 = vg.getChildAt(j);
                                if (c2 instanceof ImageView && c2.getVisibility() == View.VISIBLE) {
                                    iconPadding = c2.getPaddingLeft(); // Assume symmetric
                                    scaleType = ((ImageView) c2).getScaleType();
                                    break;
                                } else if (c2 instanceof ViewGroup) {
                                    ViewGroup vgg = (ViewGroup) c2;
                                    for (int k = 0; k < vgg.getChildCount(); k++) {
                                        View c3 = vgg.getChildAt(k);
                                        if (c3 instanceof ImageView && c3.getVisibility() == View.VISIBLE) {
                                            iconPadding = c3.getPaddingLeft();
                                            scaleType = ((ImageView) c3).getScaleType();
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (iconColor == null) {
                        try {
                            Field f = child.getClass().getDeclaredField(colorFieldName);
                            f.setAccessible(true);
                            iconColor = (ColorStateList) f.get(child);
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (iconColor != null) {
                Drawable drawable = downloadBtn.getDrawable();
                if (drawable != null) {
                    drawable = drawable.mutate();
                    drawable.setTintList(iconColor);
                    downloadBtn.setImageDrawable(drawable);
                }
            }

            // 7. Dynamic sizing based on the measured sibling
            float density = inlineActionBar.getResources().getDisplayMetrics().density;
            int touchTargetW = childWidth > 0 ? childWidth : (int) (48 * density);
            int touchTargetH = childHeight > 0 ? childHeight : ViewGroup.LayoutParams.MATCH_PARENT;
            int padding = iconPadding > 0 ? iconPadding : (int) (12 * density); // Fallback to 12dp

            downloadBtn.setScaleType(scaleType);
            downloadBtn.setPadding(padding, padding, padding, padding);

            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(touchTargetW, touchTargetH);
            btnLp.gravity = android.view.Gravity.CENTER_VERTICAL;
            wrapper.addView(downloadBtn, btnLp);

            // 8. Click handler
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

            // 9. Insert wrapper at same position with original layout params
            parent.addView(wrapper, index, originalLp);

        } catch (Exception e) {
            android.util.Log.e(TAG, "wrap failed: " + e);
        }
    }
}
