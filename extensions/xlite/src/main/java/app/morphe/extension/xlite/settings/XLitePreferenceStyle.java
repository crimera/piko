package app.morphe.extension.xlite.settings;

import android.content.Context;
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

import app.morphe.extension.xlite.ui.Theme;

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
        return Theme.surfaceContainer(context);
    }

    private static int primaryTextColor(Context context) {
        return Theme.primaryText(context);
    }

    private static int secondaryTextColor(Context context) {
        return Theme.secondaryText(context);
    }

    private static int disabledColor(Context context) {
        return Theme.blend(backgroundColor(context), secondaryTextColor(context), 0.45f);
    }

    private static int dp(Context context, float value) {
        return Theme.dpToPx(context, value);
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
            XLiteSettingsUi.SwitchControl switchView =
                    new XLiteSettingsUi.SwitchControl(context);
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
        XLiteSettingsUi.applyRippleBackground(view);
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
        private XLiteSettingsUi.SwitchControl switchView;
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

}
