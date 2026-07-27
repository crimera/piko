package app.morphe.extension.xlite.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/** Reusable themed views for extension-owned X-Lite settings screens. */
public final class XLiteSettingsUi {
    public interface CheckedChangeListener {
        void onCheckedChanged(boolean checked);
    }

    private XLiteSettingsUi() {
    }

    public static int backgroundColor(Context context) {
        return isDark(context) ? Color.BLACK : Color.WHITE;
    }

    public static int primaryTextColor(Context context) {
        return isDark(context) ? Color.WHITE : Color.BLACK;
    }

    public static int secondaryTextColor(Context context) {
        return blend(backgroundColor(context), primaryTextColor(context), 0.68f);
    }

    public static int accentColor() {
        return Color.rgb(29, 155, 240);
    }

    public static int disabledColor(Context context) {
        return blend(backgroundColor(context), secondaryTextColor(context), 0.45f);
    }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        ));
    }

    public static TextView titleText(Context context) {
        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTextColor(primaryTextColor(context));
        title.setSingleLine(false);
        return title;
    }

    public static TextView summaryText(Context context) {
        TextView summary = new TextView(context);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        summary.setTextColor(secondaryTextColor(context));
        summary.setLineSpacing(dp(context, 1), 1f);
        return summary;
    }

    public static void applyRippleBackground(View view) {
        TypedValue value = new TypedValue();
        if (view.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground,
                value,
                true
        ) && value.resourceId != 0) {
            view.setBackgroundResource(value.resourceId);
            return;
        }
        view.setBackgroundColor(backgroundColor(view.getContext()));
    }

    public static SwitchRow switchRow(
            Context context,
            CharSequence title,
            @Nullable CharSequence summary,
            boolean checked
    ) {
        return new SwitchRow(context, title, summary, checked);
    }

    public static View floatingActionButton(
            Context context,
            CharSequence contentDescription,
            View.OnClickListener listener
    ) {
        AddButton button = new AddButton(context);
        button.setContentDescription(contentDescription);
        button.setOnClickListener(listener);
        return button;
    }

    private static boolean isDark(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int withAlpha(int color, float amount) {
        return Color.argb(
                Math.round(Color.alpha(color) * amount),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

    private static int blend(int from, int to, float amount) {
        return Color.argb(
                Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * amount),
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount)
        );
    }

    public static final class SwitchRow extends LinearLayout {
        private final SwitchControl control;

        private SwitchRow(
                Context context,
                CharSequence title,
                @Nullable CharSequence summary,
                boolean checked
        ) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(context, 68));
            setPadding(dp(context, 20), dp(context, 10), dp(context, 16), dp(context, 10));
            applyRippleBackground(this);

            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(VERTICAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            TextView titleView = titleText(context);
            titleView.setText(title);
            labels.addView(titleView, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            if (summary != null && summary.length() > 0) {
                TextView summaryView = summaryText(context);
                summaryView.setText(summary);
                summaryView.setPadding(0, dp(context, 6), 0, 0);
                labels.addView(summaryView, new LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }
            LayoutParams labelParams = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelParams.setMarginEnd(dp(context, 14));
            addView(labels, labelParams);

            control = new SwitchControl(context);
            control.setChecked(checked, false);
            addView(control, new LayoutParams(dp(context, 52), dp(context, 32)));
            setOnClickListener(ignored -> control.toggle(true));
        }

        public boolean isChecked() {
            return control.isChecked();
        }

        public void setChecked(boolean checked, boolean animate) {
            control.setChecked(checked, animate);
        }

        public void setOnCheckedChangeListener(@Nullable CheckedChangeListener listener) {
            control.setOnCheckedChangeListener(listener);
        }
    }

    public static final class SwitchControl extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;
        private boolean checked;
        @Nullable private CheckedChangeListener listener;
        @Nullable private ValueAnimator animator;

        public SwitchControl(Context context) {
            super(context);
        }

        public void setInteractive(boolean interactive) {
            setClickable(interactive);
            setFocusable(interactive);
            setOnClickListener(interactive ? ignored -> toggle(true) : null);
        }

        public boolean isChecked() {
            return checked;
        }

        public boolean isAnimating() {
            return animator != null && animator.isRunning();
        }

        public void setOnCheckedChangeListener(@Nullable CheckedChangeListener listener) {
            this.listener = listener;
        }

        public void toggle(boolean animate) {
            setChecked(!checked, animate);
            if (listener != null) listener.onCheckedChanged(checked);
        }

        public void setChecked(boolean checked, boolean animate) {
            this.checked = checked;
            float target = checked ? 1f : 0f;
            if (animator != null) animator.cancel();
            if (!animate) {
                progress = target;
                invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(progress, target);
            animator.setDuration(250L);
            animator.addUpdateListener(animation -> {
                progress = (Float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int background = backgroundColor(getContext());
            int accent = accentColor();
            int secondary = secondaryTextColor(getContext());
            int disabled = disabledColor(getContext());
            int offTrack = blend(background, secondary, 0.32f);
            int onTrack = isEnabled() ? accent : disabled;
            int track = blend(offTrack, onTrack, progress);
            int offThumb = blend(background, secondary, 0.72f);
            int thumb = blend(offThumb, background, progress);

            float radius = getHeight() / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(track);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, paint);

            float thumbRadius = dp(getContext(), 10) + dp(getContext(), 3) * progress;
            float startX = radius;
            float endX = getWidth() - radius;
            float centerX = startX + (endX - startX) * progress;
            float centerY = getHeight() / 2f;
            paint.setColor(thumb);
            canvas.drawCircle(centerX, centerY, thumbRadius, paint);

            if (progress <= 0f || !isEnabled()) return;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(withAlpha(accent, progress));
            float size = thumbRadius * 0.8f;
            canvas.drawLine(
                    centerX - size * 0.35f,
                    centerY,
                    centerX - size * 0.08f,
                    centerY + size * 0.25f,
                    paint
            );
            canvas.drawLine(
                    centerX - size * 0.08f,
                    centerY + size * 0.25f,
                    centerX + size * 0.4f,
                    centerY - size * 0.28f,
                    paint
            );
        }
    }

    private static final class AddButton extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        AddButton(Context context) {
            super(context);
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(accentColor());
            setBackground(background);
            setElevation(dp(context, 6));
            setClickable(true);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float radius = dp(getContext(), 9);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(dp(getContext(), 2.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, paint);
            canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, paint);
        }
    }
}
