/*
 * Reply/Like ratio extension for X/Twitter (community addition to piko).
 * See the included NOTICE file for GPLv3 terms.
 */

package app.morphe.extension.twitter.patches;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.twitter.Pref;

/**
 * Injected from {@code InlineActionBar.f(...)}. Renders a replies-to-likes ratio at the end of the
 * action-bar row, as a glyph, a number, or both, per the in-app "Reply/Like ratio" setting.
 */
@SuppressWarnings("unused")
public final class ReplyLikeRatio {

    private static final String LABEL_TAG = "piko_reply_like_ratio";
    private static final String MY_WRAPPER_TAG = "piko_rlr_wrapper";
    private static final String DOWNLOAD_WRAPPER_TAG = "piko_download_wrapper";

    // --- Obfuscated member names, specific to X 12.19.1-release.0 ---
    private static final String TWEET_DATA_FIELD = "a";   // e.a : com.twitter.model.core.d
    private static final String LIKE_COUNT_FIELD = "b";   // d.b : int favorite_count
    private static final String REPLY_COUNT_FIELD = "f";  // d.f : int reply_count

    // grey default, amber when replies >= 50% of likes, red ("ratioed") when replies >= likes.
    private static final int COLOR_DEFAULT = 0xFF71767B;
    private static final int COLOR_WARN = 0xFFFFAD1F;
    private static final int COLOR_HOT = 0xFFF4212E;

    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static int iconResId = -1; // -1 = unresolved, 0 = not found

    public static void onBind(ViewGroup actionBar, Object tweet) {
        if (actionBar == null || tweet == null) return;
        actionBar.post(() -> update(actionBar, tweet));
    }

    private static void update(ViewGroup actionBar, Object tweet) {
        try {
            ViewParent parent = actionBar.getParent();
            if (!(parent instanceof ViewGroup)) return;
            ViewGroup parentGroup = (ViewGroup) parent;

            // Display mode: off | numbers | icon | both (Piko Settings -> Timeline).
            String mode = Pref.replyLikeRatioMode();
            if (mode == null || mode.isEmpty() || "off".equals(mode)) {
                View existing = parentGroup.findViewWithTag(LABEL_TAG);
                if (existing != null) parentGroup.removeView(existing);
                return;
            }

            long replies = readCount(tweet, REPLY_COUNT_FIELD);
            long likes = readCount(tweet, LIKE_COUNT_FIELD);
            if (replies < 0 || likes < 0) return;

            ViewGroup container = resolveOrCreateWrapper(actionBar);
            if (container == null) return;

            TextView label = container.findViewWithTag(LABEL_TAG);
            if (label == null) {
                label = createLabel(actionBar.getContext());
                int index = container.indexOfChild(actionBar) + 1;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
                lp.gravity = Gravity.CENTER_VERTICAL;
                container.addView(label, Math.min(index, container.getChildCount()), lp);
            }

            double ratio = likes == 0
                    ? (replies == 0 ? 0d : Double.POSITIVE_INFINITY)
                    : (double) replies / (double) likes;
            int color = ratio >= 1.0 ? COLOR_HOT : ratio >= 0.5 ? COLOR_WARN : COLOR_DEFAULT;

            boolean showIcon = "icon".equals(mode) || "both".equals(mode);
            boolean showText = "numbers".equals(mode) || "both".equals(mode);

            label.setText(showText ? formatRatio(replies, likes) : "");
            label.setTextColor(color);
            applyIcon(label, showIcon, color);
            label.setContentDescription(replies + " replies / " + likes + " likes");
        } catch (Throwable t) {
            Logger.printException(() -> "ReplyLikeRatio update failed", t);
        }
    }

    private static void applyIcon(TextView label, boolean showIcon, int color) {
        Context context = label.getContext();
        Drawable icon = null;
        if (showIcon) {
            if (iconResId == -1) {
                iconResId = ResourceUtils.getIdentifier(ResourceType.DRAWABLE, "piko_reply_like_ratio");
            }
            if (iconResId != 0) {
                Drawable base = context.getDrawable(iconResId);
                if (base != null) {
                    icon = base.mutate();
                    int size = dp(context, 15f);
                    icon.setBounds(0, 0, size, size);
                    icon.setTint(color);
                }
            }
        }
        label.setCompoundDrawablesRelative(icon, null, null, null);
        label.setCompoundDrawablePadding(icon != null && label.getText().length() > 0 ? dp(context, 2f) : 0);
    }

    /**
     * Returns a horizontal {@code LinearLayout} that holds the action bar, into which the label can
     * be inserted — piko's download-button wrapper or our own if present, else a new one.
     */
    private static ViewGroup resolveOrCreateWrapper(ViewGroup actionBar) {
        ViewParent parent = actionBar.getParent();
        if (!(parent instanceof ViewGroup)) return null;
        ViewGroup parentGroup = (ViewGroup) parent;

        if (parentGroup instanceof LinearLayout) {
            Object tag = parentGroup.getTag();
            if (MY_WRAPPER_TAG.equals(tag) || DOWNLOAD_WRAPPER_TAG.equals(tag)) {
                return parentGroup;
            }
        }

        int index = parentGroup.indexOfChild(actionBar);
        if (index < 0) return null;
        ViewGroup.LayoutParams originalParams = actionBar.getLayoutParams();
        Context context = actionBar.getContext();

        parentGroup.removeView(actionBar);

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setTag(MY_WRAPPER_TAG);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        wrapper.addView(actionBar, barParams);

        parentGroup.addView(wrapper, index, originalParams);
        return wrapper;
    }

    private static long readCount(Object tweet, String dataField) throws Exception {
        Object data = getField(tweet.getClass(), TWEET_DATA_FIELD).get(tweet); // com.twitter.model.core.d
        if (data == null) return -1;
        Object value = getField(data.getClass(), dataField).get(data);
        return (value instanceof Number) ? ((Number) value).longValue() : -1;
    }

    private static Field getField(Class<?> cls, String name) throws NoSuchFieldException {
        Map<String, Field> fields = FIELD_CACHE.computeIfAbsent(cls, ignored -> new ConcurrentHashMap<>());
        Field field = fields.get(name);
        if (field == null) {
            field = cls.getDeclaredField(name);
            field.setAccessible(true);
            fields.put(name, field);
        }
        return field;
    }

    private static TextView createLabel(Context context) {
        TextView tv = new TextView(context);
        tv.setTag(LABEL_TAG);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tv.setIncludeFontPadding(false);
        tv.setMaxLines(1);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(context, 4f);
        tv.setPadding(pad, 0, pad, 0);
        return tv;
    }

    private static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    /**
     * Compact ratio: a pointer vs. 1:1 (">" ratioed, "<" below, bare "1" when equal) + the value
     * to 1 decimal (2 for small values, integer for big). "∞" when likes==0, "–" when no data.
     */
    private static String formatRatio(long replies, long likes) {
        if (likes == 0) return replies == 0 ? "–" : "∞"; // – / ∞
        if (replies == likes) return "1";
        String sign = replies > likes ? ">" : "<";
        double r = (double) replies / (double) likes;
        String val;
        if (r >= 10) val = String.format(Locale.US, "%.0f", r);
        else if (r >= 0.1) val = String.format(Locale.US, "%.1f", r);
        else val = String.format(Locale.US, "%.2f", r);
        if ("0.00".equals(val)) val = "0.01"; // floor so tiny ratios don't read as zero
        return sign + val;
    }
}
