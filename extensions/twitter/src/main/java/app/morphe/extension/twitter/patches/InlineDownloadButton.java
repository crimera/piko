package app.morphe.extension.twitter.patches;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.lang.reflect.Field;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.twitter.Pref;
import app.morphe.extension.twitter.patches.nativeFeatures.downloader.NativeDownloader;

@SuppressWarnings("unused")
public class InlineDownloadButton {

    private static final String WRAPPER_TAG = "piko_download_wrapper";

    // Placeholders rewritten at patch time.
    private static String getTweetFieldName() {
        return "mTweet";
    }

    private static String getIconColorFieldName() {
        return "mIconDrawableColorStateList";
    }

    private static String getRenderHintsFieldName() {
        return "mRenderHints";
    }

    private static String getRenderHintsFocalFieldName() {
        return "isFocalTweet";
    }

    public static void onFinishInflate(ViewGroup inlineActionBar) {
        if (!Pref.enableNativeDownloader()) return;
        if (!Pref.enableInlineDownloadButton()) return;
        inlineActionBar.post(() -> wrapWithDownloadButton(inlineActionBar));
    }

    private static Object readField(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static boolean isFocalTweet(ViewGroup inlineActionBar) {
        try {
            Object renderHints = readField(inlineActionBar, getRenderHintsFieldName());
            if (renderHints == null) return false;
            return Boolean.TRUE.equals(readField(renderHints, getRenderHintsFocalFieldName()));
        } catch (Exception ignored) {
            return false;
        }
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
            if (!(child instanceof ViewGroup)) continue;

            ViewGroup container = (ViewGroup) child;
            ImageView referenceIcon = Utils.getChildView(
                    container,
                    true,
                    view -> view instanceof ImageView && view.getVisibility() == View.VISIBLE
            );
            if (referenceIcon == null) continue;

            applyIconContainerStyle(container, referenceIcon, downloadContainer, downloadIcon);
            return;
        }
    }

    private static void applyIconContainerStyle(
            ViewGroup referenceContainer,
            ImageView referenceIcon,
            FrameLayout downloadContainer,
            ImageView downloadIcon
    ) {
        downloadContainer.setPadding(
                referenceContainer.getPaddingLeft(),
                referenceContainer.getPaddingTop(),
                referenceContainer.getPaddingRight(),
                referenceContainer.getPaddingBottom());
        downloadIcon.setScaleType(referenceIcon.getScaleType());

        ViewGroup.LayoutParams iconLayoutParams = referenceIcon.getLayoutParams();
        FrameLayout.LayoutParams downloadIconLayoutParams = new FrameLayout.LayoutParams(
                iconLayoutParams != null ? iconLayoutParams.width : ViewGroup.LayoutParams.WRAP_CONTENT,
                iconLayoutParams != null ? iconLayoutParams.height : ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        downloadIcon.setLayoutParams(downloadIconLayoutParams);
    }

    private static ColorStateList resolveIconColor(ViewGroup inlineActionBar) {
        String colorFieldName = getIconColorFieldName();
        for (int i = 0; i < inlineActionBar.getChildCount(); i++) {
            View child = inlineActionBar.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;

            try {
                Object value = readField(child, colorFieldName);
                if (value instanceof ColorStateList) {
                    return (ColorStateList) value;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void wrapWithDownloadButton(ViewGroup inlineActionBar) {
        ViewParent currentParent = inlineActionBar.getParent();
        if (currentParent instanceof LinearLayout) {
            LinearLayout existingWrapper = (LinearLayout) currentParent;
            if (WRAPPER_TAG.equals(existingWrapper.getTag())) {
                return;
            }
        }
        if (!(currentParent instanceof ViewGroup)) return;

        ViewGroup parent = (ViewGroup) currentParent;

        try {
            int index = parent.indexOfChild(inlineActionBar);
            ViewGroup.LayoutParams originalLayoutParams = inlineActionBar.getLayoutParams();
            Context context = inlineActionBar.getContext();

            parent.removeView(inlineActionBar);

            LinearLayout wrapper = new LinearLayout(context);
            wrapper.setOrientation(LinearLayout.HORIZONTAL);
            wrapper.setTag(WRAPPER_TAG);
            wrapper.setGravity(Gravity.CENTER_VERTICAL);

            boolean distributeEvenly = isFocalTweet(inlineActionBar);
            int actionCount = distributeEvenly ? getActionCount(inlineActionBar) : 1;

            LinearLayout.LayoutParams barLayoutParams = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    actionCount);
            wrapper.addView(inlineActionBar, barLayoutParams);

            FrameLayout downloadContainer = new FrameLayout(context);
            downloadContainer.setClickable(true);
            downloadContainer.setFocusable(true);
            downloadContainer.setContentDescription(NativeDownloader.downloadString());

            ImageView downloadIcon = new ImageView(context);
            int iconId = Utils.getResourceIdentifier("ic_vector_incoming", "drawable");
            if (iconId != 0) {
                downloadIcon.setImageResource(iconId);
            }
            downloadIcon.setScaleType(ImageView.ScaleType.CENTER);
            downloadContainer.addView(downloadIcon, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));

            ColorStateList iconColor = resolveIconColor(inlineActionBar);
            syncButtonStyle(inlineActionBar, downloadContainer, downloadIcon);

            if (iconColor != null) {
                Drawable drawable = downloadIcon.getDrawable();
                if (drawable != null) {
                    Drawable tintedDrawable = drawable.mutate();
                    tintedDrawable.setTintList(iconColor);
                    downloadIcon.setImageDrawable(tintedDrawable);
                }
            }

            LinearLayout.LayoutParams downloadLayoutParams = distributeEvenly
                    ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f)
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            wrapper.addView(downloadContainer, downloadLayoutParams);

            wrapper.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (distributeEvenly) {
                    int targetCount = getActionCount(inlineActionBar);
                    if (barLayoutParams.weight != targetCount) {
                        barLayoutParams.weight = targetCount;
                        inlineActionBar.setLayoutParams(barLayoutParams);
                    }

                    if (downloadLayoutParams.width != 0 || downloadLayoutParams.weight != 1.0f) {
                        downloadLayoutParams.width = 0;
                        downloadLayoutParams.weight = 1.0f;
                        downloadContainer.setLayoutParams(downloadLayoutParams);
                    }
                }

                syncButtonStyle(inlineActionBar, downloadContainer, downloadIcon);
            });

            downloadContainer.setOnClickListener(v -> {
                try {
                    Object tweet = readField(inlineActionBar, getTweetFieldName());
                    if (tweet == null) {
                        Utils.showToastShort("No tweet data");
                        return;
                    }

                    NativeDownloader.downloader(context, tweet);
                } catch (Exception e) {
                    Logger.printException(() -> "click error", e);
                }
            });

            parent.addView(wrapper, index, originalLayoutParams);
        } catch (Exception e) {
            Logger.printException(() -> "wrap failed", e);
        }
    }
}
