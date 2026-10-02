package app.morphe.extension.newx.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import app.morphe.extension.newx.misc.UpdateFont;

/**
 * Reusable single/multi choice option row.
 *
 * <p>This is the same row the settings DSL renders inside single-choice and multi-choice
 * dialogs, extracted to {@code ui} so custom screens can reuse it instead of hand-rolling
 * their own option buttons.
 */
public final class ChoiceRow extends LinearLayout {
    public interface CheckedChangeListener {
        void onCheckedChanged(boolean checked);
    }

    private final ChoiceIndicator indicator;
    @Nullable private final ImageView iconView;
    private final boolean multiple;
    private boolean checked;
    @Nullable private CheckedChangeListener listener;

    public ChoiceRow(
            Context context,
            CharSequence title,
            boolean checked,
            boolean multiple
    ) {
        this(context, title, 0, checked, multiple);
    }

    public ChoiceRow(
            Context context,
            CharSequence title,
            int iconResource,
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
        setBackground(themedRipple(context));
        setClickable(true);
        setFocusable(true);

        if (iconResource != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(iconResource);
            LayoutParams iconParams = new LayoutParams(
                    Theme.dpToPx(context, 24f),
                    Theme.dpToPx(context, 24f)
            );
            iconParams.setMarginEnd(Theme.dpToPx(context, 16f));
            addView(icon, iconParams);
            iconView = icon;
        } else {
            iconView = null;
        }

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTextColor(Theme.primaryText(context));
        titleView.setTypeface(UpdateFont.customTypefaceOr(titleView.getTypeface()));
        titleView.setText(title);
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
        if (iconView == null) return;
        iconView.setImageTintList(
                ColorStateList.valueOf(
                        checked
                                ? Theme.primaryAccent(getContext())
                                : Theme.primaryText(getContext())
                )
        );
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

    private static RippleDrawable themedRipple(Context context) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.BLACK);
        return new RippleDrawable(
                ColorStateList.valueOf(Theme.rippleColor(context)),
                null,
                mask
        );
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

            float radius = Theme.dpToPx(context, 9f);
            if (checked) {
                int checkBg = Theme.checkboxChecked(context);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(checkBg);
                canvas.drawCircle(centerX, centerY, radius, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(isColorLight(checkBg) ? Color.BLACK : Color.WHITE);
                paint.setStrokeWidth(Theme.dpToPx(context, 2f));
                paint.setStrokeCap(Paint.Cap.BUTT);
                paint.setStrokeJoin(Paint.Join.MITER);
                paint.setStrokeMiter(4f);

                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(centerX - radius * 0.48f, centerY - radius * 0.02f);
                path.lineTo(centerX - radius * 0.15f, centerY + radius * 0.38f);
                path.lineTo(centerX + radius * 0.50f, centerY - radius * 0.35f);
                canvas.drawPath(path, paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(strokeWidth);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(secondary);
                canvas.drawCircle(centerX, centerY, radius, paint);
            }
        }

        private static boolean isColorLight(int color) {
            double luminance = (0.299 * Color.red(color)
                    + 0.587 * Color.green(color)
                    + 0.114 * Color.blue(color)) / 255.0;
            return luminance > 0.5;
        }
    }
}
