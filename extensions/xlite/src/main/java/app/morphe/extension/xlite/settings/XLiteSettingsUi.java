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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import app.morphe.extension.xlite.misc.UpdateFont;
import app.morphe.extension.xlite.ui.Theme;

/** Reusable themed views for extension-owned X-Lite settings screens. */
public final class XLiteSettingsUi {
    public interface CheckedChangeListener {
        void onCheckedChanged(boolean checked);
    }

    private XLiteSettingsUi() {
    }

    public static boolean isDark(Context context) {
        return Theme.isDark(context);
    }

    public static int backgroundColor(Context context) {
        return Theme.surfaceContainer(context);
    }

    public static TextView titleText(Context context) {
        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTextColor(Theme.primaryText(context));
        title.setTypeface(UpdateFont.customTypefaceOr(title.getTypeface()));
        title.setSingleLine(false);
        return title;
    }

    public static TextView summaryText(Context context) {
        TextView summary = new TextView(context);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        summary.setTextColor(Theme.secondaryText(context));
        summary.setTypeface(UpdateFont.customTypefaceOr(summary.getTypeface()));
        summary.setLineSpacing(Theme.dpToPx(context, 1f), 1f);
        return summary;
    }

    public static EditText textInput(
            Context context,
            CharSequence hint,
            int inputType
    ) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setTextColor(Theme.primaryText(context));
        input.setHintTextColor(Theme.secondaryText(context));
        input.setPadding(
                Theme.dpToPx(context, 16f),
                Theme.dpToPx(context, 12f),
                Theme.dpToPx(context, 16f),
                Theme.dpToPx(context, 12f)
        );
        input.setMinHeight(Theme.dpToPx(context, 56f));
        input.setInputType(inputType);
        input.setTypeface(UpdateFont.customTypefaceOr(input.getTypeface()));
        return input;
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

    public static ChoiceRow choiceRow(
            Context context,
            CharSequence title,
            boolean checked,
            boolean multiple
    ) {
        return new ChoiceRow(context, title, checked, multiple);
    }

    public static View divider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(Theme.dividerColor(context));
        divider.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Theme.dpToPx(context, 1f)
        ));
        return divider;
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

    private static int withAlpha(int color, float amount) {
        return Color.argb(
                Math.round(Color.alpha(color) * amount),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

    public static final class ChoiceRow extends LinearLayout {
        private final ChoiceIndicator indicator;
        private final boolean multiple;
        private boolean checked;
        @Nullable private CheckedChangeListener listener;

        private ChoiceRow(
                Context context,
                CharSequence title,
                boolean checked,
                boolean multiple
        ) {
            super(context);
            this.multiple = multiple;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(Theme.dpToPx(context, 56f));
            setPadding(
                    Theme.dpToPx(context, 24f),
                    Theme.dpToPx(context, 4f),
                    Theme.dpToPx(context, 24f),
                    Theme.dpToPx(context, 4f)
            );
            applyRippleBackground(this);
            setClickable(true);
            setFocusable(true);

            TextView titleView = titleText(context);
            titleView.setText(title);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            LayoutParams titleParams = new LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            );
            titleParams.setMarginEnd(Theme.dpToPx(context, 16f));
            addView(titleView, titleParams);

            indicator = new ChoiceIndicator(context, multiple);
            addView(indicator, new LayoutParams(
                    Theme.dpToPx(context, 24f),
                    Theme.dpToPx(context, 24f)
            ));
            setChecked(checked);

            setOnClickListener(ignored -> toggle());
        }

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
            indicator.setChecked(checked);
        }

        public void setOnCheckedChangeListener(@Nullable CheckedChangeListener listener) {
            this.listener = listener;
        }

        private void toggle() {
            if (!multiple && checked) {
                if (listener != null) listener.onCheckedChanged(true);
                return;
            }
            boolean nextChecked = multiple ? !checked : true;
            setChecked(nextChecked);
            if (listener != null) listener.onCheckedChanged(nextChecked);
        }
    }

    private static final class ChoiceIndicator extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean multiple;
        private boolean checked;

        private ChoiceIndicator(Context context, boolean multiple) {
            super(context);
            this.multiple = multiple;
        }

        private void setChecked(boolean checked) {
            this.checked = checked;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Context context = getContext();
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float strokeWidth = Theme.dpToPx(context, 2f);
            int accent = Theme.primaryAccent(context);
            int secondary = Theme.secondaryText(context);

            if (!multiple) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(strokeWidth);
                paint.setColor(checked ? accent : secondary);
                canvas.drawCircle(centerX, centerY, Theme.dpToPx(context, 9f), paint);
                if (!checked) return;
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(centerX, centerY, Theme.dpToPx(context, 5f), paint);
                return;
            }

            float inset = Theme.dpToPx(context, 3f);
            float radius = Theme.dpToPx(context, 4f);
            paint.setStyle(checked ? Paint.Style.FILL : Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setColor(checked ? accent : secondary);
            canvas.drawRoundRect(
                    inset,
                    inset,
                    getWidth() - inset,
                    getHeight() - inset,
                    radius,
                    radius,
                    paint
            );
            if (!checked) return;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Theme.onPrimaryAccent(context));
            float checkSize = Theme.dpToPx(context, 5f);
            canvas.drawLine(
                    centerX - checkSize,
                    centerY,
                    centerX - checkSize / 3f,
                    centerY + checkSize,
                    paint
            );
            canvas.drawLine(
                    centerX - checkSize / 3f,
                    centerY + checkSize,
                    centerX + checkSize,
                    centerY - checkSize,
                    paint
            );
        }
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
            setMinimumHeight(Theme.dpToPx(context, 68f));
            setPadding(
                    Theme.dpToPx(context, 20f),
                    Theme.dpToPx(context, 10f),
                    Theme.dpToPx(context, 16f),
                    Theme.dpToPx(context, 10f)
            );
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
                summaryView.setPadding(0, Theme.dpToPx(context, 6f), 0, 0);
                labels.addView(summaryView, new LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }
            LayoutParams labelParams = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelParams.setMarginEnd(Theme.dpToPx(context, 14f));
            addView(labels, labelParams);

            control = new SwitchControl(context);
            control.setChecked(checked, false);
            addView(control, new LayoutParams(
                    Theme.dpToPx(context, 52f),
                    Theme.dpToPx(context, 32f)
            ));
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
            Context context = getContext();
            int background = backgroundColor(context);
            int accent = Theme.primaryAccent(context);
            int secondary = Theme.secondaryText(context);
            int disabled = Theme.blend(background, secondary, 0.45f);
            int offTrack = Theme.blend(background, secondary, 0.32f);
            int onTrack = isEnabled() ? accent : disabled;
            int track = Theme.blend(offTrack, onTrack, progress);
            int offThumb = Theme.blend(background, secondary, 0.72f);
            int thumb = Theme.blend(offThumb, background, progress);

            float radius = getHeight() / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(track);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, paint);

            float thumbRadius = Theme.dpToPx(context, 10f) + Theme.dpToPx(context, 3f) * progress;
            float startX = radius;
            float endX = getWidth() - radius;
            float centerX = startX + (endX - startX) * progress;
            float centerY = getHeight() / 2f;
            paint.setColor(thumb);
            canvas.drawCircle(centerX, centerY, thumbRadius, paint);

            if (progress <= 0f || !isEnabled()) return;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Theme.dpToPx(context, 2f));
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
            background.setColor(Theme.primaryAccent(context));
            setBackground(background);
            setElevation(Theme.dpToPx(context, 6f));
            setClickable(true);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Context context = getContext();
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float radius = Theme.dpToPx(context, 9f);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(Theme.dpToPx(context, 2.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, paint);
            canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, paint);
        }
    }
}
