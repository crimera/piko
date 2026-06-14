/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.preference.Preference;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class InstagramPreferenceStyle {
    private static final String TAG_TITLE = "piko_instagram_pref_title";
    private static final String TAG_SUMMARY = "piko_instagram_pref_summary";
    private static final String TAG_SWITCH = "piko_instagram_pref_switch";
    private static final String TAG_TRAILING = "piko_instagram_pref_trailing";

    public static final int TRAILING_SWITCH = 1;
    public static final int TRAILING_CHEVRON = 2;

    private InstagramPreferenceStyle() {
    }

    public static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    public static boolean isDark(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int backgroundColor(Context context) {
        return isDark(context) ? Color.rgb(12, 15, 20) : Color.WHITE;
    }

    public static int pressedBackgroundColor(Context context) {
        return isDark(context) ? Color.rgb(35, 38, 43) : Color.rgb(239, 239, 240);
    }

    public static int primaryTextColor(Context context) {
        return isDark(context) ? Color.rgb(245, 245, 245) : Color.rgb(9, 12, 16);
    }

    public static int secondaryTextColor(Context context) {
        return isDark(context) ? Color.rgb(166, 169, 176) : Color.rgb(104, 107, 115);
    }

    public static int disabledTextColor(Context context) {
        return isDark(context) ? Color.rgb(53, 57, 64) : Color.rgb(198, 199, 204);
    }

    public static View createPreferenceView(Context context, int trailingType) {
        PreferenceRow row = new PreferenceRow(context, trailingType);
        row.setOrientation(trailingType == TRAILING_SWITCH ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(context, 78));
        row.setPadding(dp(context, 17), dp(context, 10), dp(context, 17), dp(context, 10));
        row.setBackgroundColor(backgroundColor(context));
        row.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        if (trailingType == TRAILING_SWITCH) {
            LinearLayout titleRow = new LinearLayout(context);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(titleRow, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            row.setHighlightView(titleRow);

            TextView title = new TextView(context);
            title.setTag(TAG_TITLE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            title.setTextColor(primaryTextColor(context));
            title.setIncludeFontPadding(true);
            title.setSingleLine(false);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            titleParams.rightMargin = dp(context, 14);
            titleRow.addView(title, titleParams);

            SwitchView switchView = new SwitchView(context);
            switchView.setTag(TAG_SWITCH);
            titleRow.addView(switchView, new LinearLayout.LayoutParams(dp(context, 52), dp(context, 32)));

            TextView summary = new TextView(context);
            summary.setTag(TAG_SUMMARY);
            summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            summary.setTextColor(secondaryTextColor(context));
            summary.setLineSpacing(dp(context, 1), 1.0f);
            summary.setPadding(0, dp(context, 10), 0, 0);
            row.addView(summary, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            return row;
        }

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        textParams.rightMargin = dp(context, 14);
        row.addView(textColumn, textParams);

        TextView title = new TextView(context);
        title.setTag(TAG_TITLE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTextColor(primaryTextColor(context));
        title.setIncludeFontPadding(true);
        title.setSingleLine(false);
        textColumn.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView summary = new TextView(context);
        summary.setTag(TAG_SUMMARY);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        summary.setTextColor(secondaryTextColor(context));
        summary.setLineSpacing(dp(context, 1), 1.0f);
        summary.setPadding(0, dp(context, 10), 0, 0);
        textColumn.addView(summary, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (trailingType == TRAILING_CHEVRON) {
            ChevronView trailing = new ChevronView(context);
            trailing.setTag(TAG_TRAILING);
            row.addView(trailing, new LinearLayout.LayoutParams(dp(context, 22), dp(context, 34)));
        }

        return row;
    }

    public static void bindText(Preference preference, View view) {
        Context context = view.getContext();
        boolean enabled = preference.isEnabled();

        TextView title = view.findViewWithTag(TAG_TITLE);
        TextView summary = view.findViewWithTag(TAG_SUMMARY);
        View trailing = view.findViewWithTag(TAG_TRAILING);

        int titleColor = enabled ? primaryTextColor(context) : disabledTextColor(context);
        int summaryColor = enabled ? secondaryTextColor(context) : disabledTextColor(context);

        if (title != null) {
            title.setText(preference.getTitle());
            title.setTextColor(titleColor);
            title.setEnabled(enabled);
        }

        if (summary != null) {
            CharSequence summaryText = preference.getSummary();
            boolean hasSummary = !TextUtils.isEmpty(summaryText);
            summary.setText(hasSummary ? summaryText : "");
            summary.setVisibility(hasSummary ? View.VISIBLE : View.GONE);
            summary.setTextColor(summaryColor);
            summary.setEnabled(enabled);
            if (view instanceof PreferenceRow) {
                ((PreferenceRow) view).setHasSummary(hasSummary);
            }
        }

        if (trailing != null) {
            trailing.setEnabled(enabled);
            trailing.invalidate();
        }

        view.setEnabled(enabled);
    }

    public static void setTrailingVisible(View view, boolean visible) {
        View trailing = view.findViewWithTag(TAG_TRAILING);
        if (trailing != null) {
            trailing.setVisibility(visible ? View.VISIBLE : View.GONE);
            trailing.invalidate();
        }
    }

    public static SwitchView findSwitch(View view) {
        return view.findViewWithTag(TAG_SWITCH);
    }

    public static void bindSwitchAccessibility(View view, boolean checked) {
        if (view instanceof PreferenceRow) {
            ((PreferenceRow) view).setSwitchAccessibilityChecked(checked);
        }
    }

    public static boolean consumeSwitchClickAllowed(View view) {
        if (view instanceof PreferenceRow) {
            return ((PreferenceRow) view).consumeSwitchClickAllowed();
        }
        return true;
    }

    private static class PreferenceRow extends LinearLayout {
        private final Paint pressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect switchHitRect = new Rect();
        private final int trailingType;
        private View highlightView;
        private boolean pressedHighlightAllowed;
        private boolean switchClickAllowed = true;
        private boolean drawPressedHighlight;
        private boolean switchAccessibilityChecked;

        PreferenceRow(Context context, int trailingType) {
            super(context);
            this.trailingType = trailingType;
            setWillNotDraw(false);
        }

        void setHighlightView(View highlightView) {
            this.highlightView = highlightView;
        }

        void setSwitchAccessibilityChecked(boolean checked) {
            switchAccessibilityChecked = checked;
        }

        void setHasSummary(boolean hasSummary) {
            int topPadding = dp(getContext(), 10);
            int bottomPadding = dp(getContext(), 20);
            setMinimumHeight(dp(getContext(), hasSummary ? 80 : 62));
            setPadding(getPaddingLeft(), topPadding, getPaddingRight(), bottomPadding);
        }

        @Override
        public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(event);
            if (trailingType == TRAILING_SWITCH) {
                event.setClassName("android.widget.Switch");
                event.setChecked(switchAccessibilityChecked);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (trailingType == TRAILING_SWITCH) {
                info.setClassName("android.widget.Switch");
                info.setCheckable(true);
                info.setChecked(switchAccessibilityChecked);
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                pressedHighlightAllowed = shouldDrawPressedHighlight(event.getX(), event.getY());
                switchClickAllowed = shouldHandleSwitchClick(event.getY());
            }
            return super.dispatchTouchEvent(event);
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            setDrawPressedHighlight(pressed && pressedHighlightAllowed);
            if (!pressed) {
                pressedHighlightAllowed = false;
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (drawPressedHighlight && highlightView != null) {
                pressedPaint.setStyle(Paint.Style.FILL);
                pressedPaint.setColor(pressedBackgroundColor(getContext()));
                int top = highlightTop();
                int bottom = highlightBottom();
                canvas.drawRect(0, top, getWidth(), bottom, pressedPaint);
            }
            super.dispatchDraw(canvas);
        }

        private boolean shouldDrawPressedHighlight(float x, float y) {
            if (trailingType != TRAILING_SWITCH || !isEnabled() || highlightView == null) {
                return false;
            }

            if (y < highlightTop() || y > highlightBottom()) {
                return false;
            }

            View switchView = findViewWithTag(TAG_SWITCH);
            if (switchView == null) {
                return true;
            }

            int horizontalSlop = dp(getContext(), 10);
            int verticalSlop = dp(getContext(), 3);
            switchHitRect.set(0, 0, switchView.getWidth(), switchView.getHeight());
            offsetDescendantRectToMyCoords(switchView, switchHitRect);
            switchHitRect.inset(-horizontalSlop, -verticalSlop);
            return !switchHitRect.contains(Math.round(x), Math.round(y));
        }

        private boolean shouldHandleSwitchClick(float y) {
            return trailingType != TRAILING_SWITCH || highlightView == null || (y >= highlightTop() && y <= highlightBottom());
        }

        private boolean consumeSwitchClickAllowed() {
            boolean allowed = switchClickAllowed;
            switchClickAllowed = true;
            return allowed;
        }

        private int highlightTop() {
            if (highlightView == null) {
                return 0;
            }
            return Math.max(0, highlightView.getTop() - dp(getContext(), 10));
        }

        private int highlightBottom() {
            if (highlightView == null) {
                return 0;
            }
            return Math.min(getHeight(), highlightView.getBottom() + dp(getContext(), 10));
        }

        private void setDrawPressedHighlight(boolean drawPressedHighlight) {
            if (this.drawPressedHighlight == drawPressedHighlight) {
                return;
            }
            this.drawPressedHighlight = drawPressedHighlight;
            invalidate();
        }
    }

    private static class ChevronView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ChevronView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float centerY = getHeight() / 2f;
            float tipX = getWidth() - dp(getContext(), 1.25f);
            float armX = getWidth() - dp(getContext(), 6.75f);
            float armOffset = dp(getContext(), 6.25f);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.9f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(isEnabled() ? secondaryTextColor(getContext()) : disabledTextColor(getContext()));

            canvas.drawLine(armX, centerY - armOffset, tipX, centerY, paint);
            canvas.drawLine(tipX, centerY, armX, centerY + armOffset, paint);
        }
    }

    public static class SwitchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;
        private float animationProgress;
        private boolean checked;
        private boolean animatingToChecked;
        private ValueAnimator animator;

        public SwitchView(Context context) {
            super(context);
            setClickable(false);
            setFocusable(false);
        }

        public boolean isAnimating() {
            return animator != null && animator.isRunning();
        }

        public void setChecked(boolean checked, boolean animate) {
            float target = checked ? 1f : 0f;
            if (this.checked == checked && progress == target) {
                return;
            }

            this.checked = checked;
            if (animator != null) {
                animator.cancel();
            }

            if (!animate) {
                progress = target;
                animationProgress = 1f;
                invalidate();
                return;
            }

            animatingToChecked = checked;
            animationProgress = 0f;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(700L);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    animationProgress = (Float) animation.getAnimatedValue();
                    progress = animatingToChecked ? animationProgress : 1f - animationProgress;
                    invalidate();
                }
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            boolean dark = isDark(getContext());
            boolean enabled = isEnabled();

            int offTrack = dark ? Color.rgb(95, 99, 110) : Color.rgb(220, 222, 230);
            int onTrack = dark ? Color.rgb(220, 222, 230) : Color.BLACK;
            int offThumb = dark ? Color.rgb(145, 150, 162) : Color.rgb(108, 114, 123);
            int onThumb = dark ? Color.BLACK : Color.WHITE;
            int stroke = dark ? Color.rgb(145, 150, 162) : Color.rgb(108, 114, 123);

            if (!enabled) {
                offTrack = dark ? Color.rgb(40, 44, 50) : Color.rgb(238, 239, 242);
                onTrack = offTrack;
                offThumb = disabledTextColor(getContext());
                onThumb = offThumb;
                stroke = disabledTextColor(getContext());
            }

            boolean animating = isAnimating();
            float elapsedProgress = animating
                    ? animationProgress
                    : progress;

            float pressEndProgress = 0.10f;
            float edgeContactProgress = animatingToChecked ? 0.10f : 0.20f;
            float movementStartProgress = 0.30f;
            float movementDuration = animatingToChecked ? 0.30f : 0.40f;
            float movementEndProgress = movementStartProgress + movementDuration;
            float colorStartProgress = animatingToChecked ? 0.20f : 0.15f;
            float colorDuration = animatingToChecked ? 0.35f : 0.40f;
            float movementProgress = easeOutCubic(clamp01((elapsedProgress - movementStartProgress) / movementDuration));
            float colorStep = smoothStep(clamp01((elapsedProgress - colorStartProgress) / colorDuration));
            float positionProgress = animating
                    ? (animatingToChecked ? movementProgress : 1f - movementProgress)
                    : progress;
            float colorProgress = animating
                    ? (animatingToChecked ? colorStep : 1f - colorStep)
                    : progress;

            int trackColor = blend(offTrack, onTrack, colorProgress);
            int thumbColor = blend(offThumb, onThumb, colorProgress);

            float strokeWidth = dp(getContext(), 2);
            float radius = getHeight() / 2f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(trackColor);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, paint);

            float strokeProgress;
            if (dark) {
                strokeProgress = 1f - colorProgress;
            } else if (animating) {
                strokeProgress = smoothStep(clamp01((0.62f - colorProgress) / 0.42f));
            } else {
                strokeProgress = 1f - progress;
            }
            float strokeOpacity = dark ? strokeProgress * strokeProgress : strokeProgress;
            int strokeAlpha = Math.round(Color.alpha(stroke) * strokeOpacity);
            if (strokeAlpha > 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(strokeWidth);
                paint.setColor(Color.argb(strokeAlpha, Color.red(stroke), Color.green(stroke), Color.blue(stroke)));
                canvas.drawRoundRect(strokeWidth / 2f, strokeWidth / 2f, getWidth() - strokeWidth / 2f, getHeight() - strokeWidth / 2f, radius, radius, paint);
            }

            float offThumbRadius = (getHeight() - dp(getContext(), 16)) / 2f;
            float onThumbRadius = (getHeight() - dp(getContext(), 8)) / 2f;
            float contactRadius = (getHeight() / 2f) - (strokeWidth / 2f);
            float startThumbRadius = animatingToChecked ? offThumbRadius : onThumbRadius;
            float endThumbRadius = animatingToChecked ? onThumbRadius : offThumbRadius;
            float pressedThumbRadius = Math.max(dp(getContext(), 8.5f), Math.min(offThumbRadius, onThumbRadius) - dp(getContext(), 3.5f));
            float preMoveThumbRadius = contactRadius - dp(getContext(), 1.0f);
            float thumbRadius;
            if (!animating) {
                thumbRadius = offThumbRadius + ((onThumbRadius - offThumbRadius) * progress);
            } else if (animatingToChecked) {
                if (elapsedProgress < edgeContactProgress) {
                    thumbRadius = lerp(startThumbRadius, preMoveThumbRadius, smoothStep(clamp01(elapsedProgress / edgeContactProgress)));
                } else if (elapsedProgress < movementEndProgress) {
                    thumbRadius = preMoveThumbRadius;
                } else {
                    float settleDuration = 0.15f;
                    thumbRadius = lerp(preMoveThumbRadius, endThumbRadius, smoothStep(clamp01((elapsedProgress - movementEndProgress) / settleDuration)));
                }
            } else if (elapsedProgress < pressEndProgress) {
                thumbRadius = lerp(startThumbRadius, pressedThumbRadius, smoothStep(clamp01(elapsedProgress / pressEndProgress)));
            } else if (elapsedProgress < edgeContactProgress) {
                thumbRadius = lerp(pressedThumbRadius, preMoveThumbRadius, smoothStep(clamp01((elapsedProgress - pressEndProgress) / (edgeContactProgress - pressEndProgress))));
            } else if (elapsedProgress < 0.55f) {
                thumbRadius = preMoveThumbRadius;
            } else {
                float settleStart = 0.55f;
                float settleDuration = 0.20f;
                thumbRadius = lerp(preMoveThumbRadius, endThumbRadius, smoothStep(clamp01((elapsedProgress - settleStart) / settleDuration)));
            }
            float left = getHeight() / 2f;
            float right = getWidth() - (getHeight() / 2f);
            float cx = left + ((right - left) * positionProgress);
            float cy = getHeight() / 2f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(thumbColor);
            canvas.drawCircle(cx, cy, thumbRadius, paint);
        }

        private float lerp(float from, float to, float amount) {
            return from + ((to - from) * amount);
        }

        private float easeOutCubic(float value) {
            float inverse = 1f - value;
            return 1f - (inverse * inverse * inverse);
        }

        private int blend(int from, int to, float amount) {
            int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * amount);
            int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * amount);
            int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * amount);
            int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount);
            return Color.argb(a, r, g, b);
        }

        private float clamp01(float value) {
            if (value < 0f) {
                return 0f;
            }
            if (value > 1f) {
                return 1f;
            }
            return value;
        }

        private float smoothStep(float value) {
            return value * value * (3f - (2f * value));
        }
    }
}
