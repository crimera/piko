package app.morphe.extension.twitter.patches;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.lang.reflect.Field;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.twitter.Pref;

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
        if (!Pref.enableNativeDownloader()) return;
        if (!Pref.enableInlineDownloadButton()) return;
        inlineActionBar.post(() -> wrapWithDownloadButton(inlineActionBar));
    }

    private static String getRenderHintsFieldName() {
        return "mRenderHints";
    }

    private static String getRenderHintsFocalFieldName() {
        return "isFocalTweet";
    }

    private static boolean isFocalTweet(ViewGroup inlineActionBar) {
        try {
            Field field = inlineActionBar.getClass().getDeclaredField(getRenderHintsFieldName());
            field.setAccessible(true);
            Object renderHints = field.get(inlineActionBar);
            if (renderHints == null) return false;

            Field focalField = renderHints.getClass().getDeclaredField(getRenderHintsFocalFieldName());
            focalField.setAccessible(true);
            Object value = focalField.get(renderHints);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static int getActionCount(ViewGroup inlineActionBar) {
        int count = 0;
        for (int i = 0; i < inlineActionBar.getChildCount(); i++) {
            View child = inlineActionBar.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private static View getLastVisibleChild(ViewGroup inlineActionBar) {
        for (int i = inlineActionBar.getChildCount() - 1; i >= 0; i--) {
            View child = inlineActionBar.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && child.getWidth() > 0) {
                return child;
            }
        }
        return null;
    }

    private static void syncButtonStyle(ViewGroup inlineActionBar, FrameLayout downloadContainer, ImageView downloadIcon) {
        View lastVisibleChild = getLastVisibleChild(inlineActionBar);
        if (!(lastVisibleChild instanceof ViewGroup)) return;

        ViewGroup referenceGroup = (ViewGroup) lastVisibleChild;
        for (int i = 0; i < referenceGroup.getChildCount(); i++) {
            View child = referenceGroup.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup container = (ViewGroup) child;
                for (int j = 0; j < container.getChildCount(); j++) {
                    View iconChild = container.getChildAt(j);
                    if (iconChild instanceof ImageView && iconChild.getVisibility() == View.VISIBLE) {
                        applyIconContainerStyle(container, (ImageView) iconChild, downloadContainer, downloadIcon);
                        return;
                    } else if (iconChild instanceof ViewGroup) {
                        ViewGroup nested = (ViewGroup) iconChild;
                        for (int k = 0; k < nested.getChildCount(); k++) {
                            View nestedIcon = nested.getChildAt(k);
                            if (nestedIcon instanceof ImageView && nestedIcon.getVisibility() == View.VISIBLE) {
                                applyIconContainerStyle(container, (ImageView) nestedIcon, downloadContainer, downloadIcon);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private static void applyIconContainerStyle(ViewGroup referenceContainer, ImageView referenceIcon,
                                                FrameLayout downloadContainer, ImageView downloadIcon) {
        downloadContainer.setPadding(
                referenceContainer.getPaddingLeft(),
                referenceContainer.getPaddingTop(),
                referenceContainer.getPaddingRight(),
                referenceContainer.getPaddingBottom());
        downloadIcon.setScaleType(referenceIcon.getScaleType());

        ViewGroup.LayoutParams iconLp = referenceIcon.getLayoutParams();
        FrameLayout.LayoutParams downloadIconLp = new FrameLayout.LayoutParams(
                iconLp != null ? iconLp.width : ViewGroup.LayoutParams.WRAP_CONTENT,
                iconLp != null ? iconLp.height : ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        downloadIcon.setLayoutParams(downloadIconLp);
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
            int index = parent.indexOfChild(inlineActionBar);
            ViewGroup.LayoutParams originalLp = inlineActionBar.getLayoutParams();

            parent.removeView(inlineActionBar);

            LinearLayout wrapper = new LinearLayout(inlineActionBar.getContext());
            wrapper.setOrientation(LinearLayout.HORIZONTAL);
            wrapper.setTag("piko_download_wrapper");
            wrapper.setGravity(Gravity.CENTER_VERTICAL);

            boolean distributeEvenly = isFocalTweet(inlineActionBar);
            int actionCount = distributeEvenly ? getActionCount(inlineActionBar) : 1;

            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, actionCount);
            wrapper.addView(inlineActionBar, barLp);

            FrameLayout downloadContainer = new FrameLayout(inlineActionBar.getContext());
            ImageView downloadIcon = new ImageView(inlineActionBar.getContext());
            int iconId = Utils.getResourceIdentifier("ic_vector_incoming", "drawable");
            if (iconId != 0) {
                downloadIcon.setImageResource(iconId);
            }
            downloadIcon.setScaleType(ImageView.ScaleType.CENTER);
            downloadContainer.addView(downloadIcon, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            downloadContainer.setClickable(true);
            downloadContainer.setFocusable(true);
            downloadContainer.setId(View.generateViewId());

            String colorFieldName = getIconColorFieldName();
            ColorStateList iconColor = null;
            for (int i = 0; i < inlineActionBar.getChildCount(); i++) {
                View child = inlineActionBar.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE && iconColor == null) {
                    try {
                        Field f = child.getClass().getDeclaredField(colorFieldName);
                        f.setAccessible(true);
                        iconColor = (ColorStateList) f.get(child);
                    } catch (Exception ignored) {
                    }
                }
            }

            syncButtonStyle(inlineActionBar, downloadContainer, downloadIcon);

            if (iconColor != null) {
                Drawable drawable = downloadIcon.getDrawable();
                if (drawable != null) {
                    drawable = drawable.mutate();
                    drawable.setTintList(iconColor);
                    downloadIcon.setImageDrawable(drawable);
                }
            }

            LinearLayout.LayoutParams downloadLp;
            if (distributeEvenly) {
                downloadLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            } else {
                downloadLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            wrapper.addView(downloadContainer, downloadLp);

            wrapper.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                try {
                    if (distributeEvenly) {
                        int targetCount = getActionCount(inlineActionBar);
                        if (barLp.weight != targetCount) {
                            barLp.weight = targetCount;
                            inlineActionBar.setLayoutParams(barLp);
                        }

                        if (downloadLp.width != 0 || downloadLp.weight != 1.0f) {
                            downloadLp.width = 0;
                            downloadLp.weight = 1.0f;
                            downloadContainer.setLayoutParams(downloadLp);
                        }
                    }

                    syncButtonStyle(inlineActionBar, downloadContainer, downloadIcon);
                } catch (Exception ignored) {
                }
            });

            downloadContainer.setOnClickListener(v -> {
                try {
                    Field tweetField = inlineActionBar.getClass()
                            .getDeclaredField(getTweetFieldName());
                    tweetField.setAccessible(true);
                    Object tweet = tweetField.get(inlineActionBar);

                    if (tweet == null) {
                        Utils.showToastShort("No tweet data");
                        return;
                    }

                    app.morphe.extension.twitter.patches.nativeFeatures.downloader.NativeDownloader.downloader(
                            inlineActionBar.getContext(), tweet);

                } catch (Exception e) {
                    Logger.printException(() -> "InlineDownloadButton: click error", e);
                }
            });

            parent.addView(wrapper, index, originalLp);

        } catch (Exception e) {
            android.util.Log.e(TAG, "wrap failed: " + e);
        }
    }
}
