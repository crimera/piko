package app.morphe.extension.xlite.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.preference.EditTextPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.SwitchPreference;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

final class XLitePreferenceStyle {
    private enum TrailingAccessory {
        NONE,
        SWITCH,
        CHEVRON,
    }

    private static final String TAG_TITLE = "piko_xlite_pref_title";
    private static final String TAG_SUMMARY = "piko_xlite_pref_summary";
    private static final String TAG_SWITCH = "piko_xlite_pref_switch";
    private static final String TAG_TRAILING = "piko_xlite_pref_trailing";

    private XLitePreferenceStyle() {
    }

    static int backgroundColor(Context context) {
        return isDark(context) ? Color.BLACK : Color.WHITE;
    }

    private static int primaryTextColor(Context context) {
        return isDark(context) ? Color.WHITE : Color.BLACK;
    }

    private static int secondaryTextColor(Context context) {
        return blend(backgroundColor(context), primaryTextColor(context), 0.68f);
    }

    private static int accentColor(Context context) {
        return Color.rgb(29, 155, 240);
    }

    private static boolean isDark(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int disabledColor(Context context) {
        return blend(backgroundColor(context), secondaryTextColor(context), 0.45f);
    }

    private static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        ));
    }

    private static View createRow(Context context, TrailingAccessory trailingAccessory) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(context, 68));
        row.setPadding(dp(context, 20), dp(context, 10), dp(context, 16), dp(context, 10));
        applyRippleBackground(row);
        row.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        textParams.setMarginEnd(dp(context, 14));
        row.addView(textContainer, textParams);
        addTitle(context, textContainer, 0f);

        TextView summary = new TextView(context);
        summary.setTag(TAG_SUMMARY);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        summary.setTextColor(secondaryTextColor(context));
        summary.setLineSpacing(dp(context, 1), 1f);
        summary.setPadding(0, dp(context, 6), 0, 0);
        textContainer.addView(summary, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (trailingAccessory == TrailingAccessory.SWITCH) {
            SwitchView switchView = new SwitchView(context);
            switchView.setTag(TAG_SWITCH);
            row.addView(switchView, new LinearLayout.LayoutParams(
                    dp(context, 52),
                    dp(context, 32)
            ));
        }
        if (trailingAccessory == TrailingAccessory.CHEVRON) {
            ChevronView chevron = new ChevronView(context);
            chevron.setTag(TAG_TRAILING);
            row.addView(chevron, new LinearLayout.LayoutParams(
                    dp(context, 20),
                    dp(context, 32)
            ));
        }
        return row;
    }

    private static void applyRippleBackground(View view) {
        TypedValue value = new TypedValue();
        if (!view.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground,
                value,
                true
        ) || value.resourceId == 0) {
            view.setBackgroundColor(backgroundColor(view.getContext()));
            return;
        }
        view.setBackgroundResource(value.resourceId);
    }

    private static void addTitle(Context context, LinearLayout parent, float weight) {
        TextView title = new TextView(context);
        title.setTag(TAG_TITLE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTextColor(primaryTextColor(context));
        title.setSingleLine(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                weight == 0f ? LinearLayout.LayoutParams.MATCH_PARENT : 0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                weight
        );
        if (weight != 0f) params.setMarginEnd(dp(context, 14));
        parent.addView(title, params);
    }

    private static void bind(Preference preference, View view) {
        boolean enabled = preference.isEnabled();
        TextView title = view.findViewWithTag(TAG_TITLE);
        TextView summary = view.findViewWithTag(TAG_SUMMARY);
        View trailing = view.findViewWithTag(TAG_TRAILING);
        int disabled = disabledColor(view.getContext());

        if (title != null) {
            title.setText(preference.getTitle());
            title.setTextColor(enabled ? primaryTextColor(view.getContext()) : disabled);
        }
        if (summary != null) {
            CharSequence text = preference.getSummary();
            boolean visible = !TextUtils.isEmpty(text);
            summary.setText(visible ? text : "");
            summary.setVisibility(visible ? View.VISIBLE : View.GONE);
            summary.setTextColor(enabled ? secondaryTextColor(view.getContext()) : disabled);
        }
        if (trailing != null) {
            trailing.setEnabled(enabled);
            trailing.invalidate();
        }
        view.setEnabled(enabled);
    }

    static final class Category extends PreferenceCategory {
        Category(Context context) {
            super(context);
        }

        @Override
        protected View onCreateView(ViewGroup parent) {
            TextView title = new TextView(getContext());
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            title.setTextColor(primaryTextColor(getContext()));
            title.setPadding(
                    dp(getContext(), 20),
                    dp(getContext(), 22),
                    dp(getContext(), 16),
                    dp(getContext(), 8)
            );
            title.setBackgroundColor(backgroundColor(getContext()));
            title.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            return title;
        }

        @Override
        protected void onBindView(View view) {
            TextView title = (TextView) view;
            title.setText(getTitle());
            title.setEnabled(isEnabled());
        }
    }

    static final class Toggle extends SwitchPreference {
        private SwitchView switchView;
        private boolean animateChange;
        private boolean previousChecked;

        Toggle(Context context) {
            super(context);
        }

        @Override
        protected View onCreateView(ViewGroup parent) {
            return createRow(getContext(), TrailingAccessory.SWITCH);
        }

        @Override
        protected void onBindView(View view) {
            bind(this, view);
            switchView = view.findViewWithTag(TAG_SWITCH);
            if (switchView == null) return;
            switchView.setEnabled(isEnabled());
            if (animateChange) {
                switchView.setChecked(previousChecked, false);
                switchView.setChecked(isChecked(), true);
                animateChange = false;
                return;
            }
            if (!switchView.isAnimating()) switchView.setChecked(isChecked(), false);
        }

        @Override
        protected void onClick() {
            boolean checkedBeforeClick = isChecked();
            super.onClick();
            if (checkedBeforeClick == isChecked()) return;
            previousChecked = checkedBeforeClick;
            animateChange = true;
            notifyChanged();
        }
    }

    static final class TextInput extends EditTextPreference {
        TextInput(Context context) {
            super(context);
        }

        @Override
        protected View onCreateView(ViewGroup parent) {
            return createRow(getContext(), TrailingAccessory.NONE);
        }

        @Override
        protected void onBindView(View view) {
            bind(this, view);
        }
    }

    static final class MultiChoice extends MultiSelectListPreference {
        MultiChoice(Context context) {
            super(context);
        }

        @Override
        protected View onCreateView(ViewGroup parent) {
            return createRow(getContext(), TrailingAccessory.NONE);
        }

        @Override
        protected void onBindView(View view) {
            bind(this, view);
        }
    }

    static final class Action extends Preference {
        Action(Context context) {
            super(context);
        }

        @Override
        protected View onCreateView(ViewGroup parent) {
            return createRow(getContext(), TrailingAccessory.NONE);
        }

        @Override
        protected void onBindView(View view) {
            bind(this, view);
        }
    }

    static final class Navigation extends Preference {
        Navigation(Context context) {
            super(context);
        }

        @Override
        protected View onCreateView(ViewGroup parent) {
            return createRow(getContext(), TrailingAccessory.CHEVRON);
        }

        @Override
        protected void onBindView(View view) {
            bind(this, view);
        }
    }

    private static final class ChevronView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ChevronView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float centerY = getHeight() / 2f;
            float tipX = getWidth() - dp(getContext(), 2);
            float armX = tipX - dp(getContext(), 6);
            float offset = dp(getContext(), 6);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.8f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(isEnabled()
                    ? secondaryTextColor(getContext())
                    : disabledColor(getContext()));
            canvas.drawLine(armX, centerY - offset, tipX, centerY, paint);
            canvas.drawLine(tipX, centerY, armX, centerY + offset, paint);
        }
    }

    private static final class SwitchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;
        private ValueAnimator animator;

        SwitchView(Context context) {
            super(context);
            setClickable(false);
            setFocusable(false);
        }

        boolean isAnimating() {
            return animator != null && animator.isRunning();
        }

        void setChecked(boolean checked, boolean animate) {
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
            int accent = accentColor(getContext());
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
}
