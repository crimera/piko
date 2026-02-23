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

            // 7. Sync layout dynamically with the InlineActionBar's last icon
            // InlineActionBar does custom measuring and distributes width evenly.
            // To be pixel-perfect, we need to match the size and spacing of its last child
            // exactly whenever it lays out.

            downloadBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            wrapper.addView(downloadBtn, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

            wrapper.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                try {
                    View lastVisibleChild = null;
                    for (int i = inlineActionBar.getChildCount() - 1; i >= 0; i--) {
                        View child = inlineActionBar.getChildAt(i);
                        if (child.getVisibility() == View.VISIBLE && child.getWidth() > 0) {
                            lastVisibleChild = child;
                            break;
                        }
                    }

                    if (lastVisibleChild != null) {
                        ViewGroup.LayoutParams btnLp = downloadBtn.getLayoutParams();
                        boolean changed = false;

                        // Match width of the native button exactly
                        if (btnLp.width != lastVisibleChild.getWidth()) {
                            btnLp.width = lastVisibleChild.getWidth();
                            changed = true;
                        }

                        // Extract internal ImageView to match padding/scaling exactly
                        if (lastVisibleChild instanceof ViewGroup) {
                            ViewGroup vg = (ViewGroup) lastVisibleChild;
                            for (int j = 0; j < vg.getChildCount(); j++) {
                                View c2 = vg.getChildAt(j);
                                if (c2 instanceof ImageView && c2.getVisibility() == View.VISIBLE) {
                                    if (downloadBtn.getPaddingLeft() != c2.getPaddingLeft()) {
                                        int p = c2.getPaddingLeft();
                                        downloadBtn.setPadding(p, p, p, p);
                                        downloadBtn.setScaleType(((ImageView) c2).getScaleType());
                                    }
                                    break;
                                } else if (c2 instanceof ViewGroup) {
                                    ViewGroup vgg = (ViewGroup) c2;
                                    for (int k = 0; k < vgg.getChildCount(); k++) {
                                        View c3 = vgg.getChildAt(k);
                                        if (c3 instanceof ImageView && c3.getVisibility() == View.VISIBLE) {
                                            if (downloadBtn.getPaddingLeft() != c3.getPaddingLeft()) {
                                                int p = c3.getPaddingLeft();
                                                downloadBtn.setPadding(p, p, p, p);
                                                downloadBtn.setScaleType(((ImageView) c3).getScaleType());
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        if (changed) {
                            downloadBtn.setLayoutParams(btnLp);
                        }
                    }
                } catch (Exception ignored) {}
            });

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
