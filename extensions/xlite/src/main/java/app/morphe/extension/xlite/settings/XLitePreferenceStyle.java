package app.morphe.extension.xlite.settings;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.xlite.ui.Theme;

final class XLitePreferenceStyle {
    private enum TrailingAccessory {
        NONE,
        SWITCH,
    }

    private static final String TAG_LEADING = "piko_xlite_pref_leading";
    private static final String TAG_TITLE = "piko_xlite_pref_title";
    private static final String TAG_SUMMARY = "piko_xlite_pref_summary";
    private static final String TAG_SWITCH = "piko_xlite_pref_switch";
    private static final int[] TITLE_TEXT_SIZES = {13, 14, 15, 16, 17, 18};
    private static final int[] SUMMARY_TEXT_SIZES = {12, 13, 14, 15, 16, 17};

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

    private static View createRow(
            Context context,
            TrailingAccessory trailingAccessory,
            boolean navigation
    ) {
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

        if (navigation) {
            ImageView leading = new ImageView(context);
            leading.setTag(TAG_LEADING);
            leading.setScaleType(ImageView.ScaleType.FIT_CENTER);
            leading.setVisibility(View.GONE);
            LinearLayout.LayoutParams leadingParams = new LinearLayout.LayoutParams(
                    dp(context, 20),
                    dp(context, 20)
            );
            leadingParams.setMarginEnd(dp(context, 32));
            row.addView(leading, leadingParams);
        }

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
        summary.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                SUMMARY_TEXT_SIZES[typographyScale(context)]
        );
        summary.setTypeface(font(context, "chirp_regular_400", Typeface.DEFAULT));
        summary.setTextColor(secondaryTextColor(context));
        summary.setLineSpacing(dp(context, 1), 1f);
        summary.setPadding(0, dp(context, 2), 0, 0);
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
        return row;
    }

    private static void applyRippleBackground(View view) {
        XLiteSettingsUi.applyRippleBackground(view);
    }

    private static void addTitle(
            Context context,
            LinearLayout parent,
            float weight
    ) {
        TextView title = new TextView(context);
        title.setTag(TAG_TITLE);
        title.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                TITLE_TEXT_SIZES[typographyScale(context)]
        );
        title.setTypeface(font(
                context,
                "chirp_medium_500",
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
        ));
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

    private static int typographyScale(Context context) {
        int width = context.getResources().getConfiguration().smallestScreenWidthDp;
        if (width >= 720) return 5;
        if (width >= 600) return 4;
        if (width >= 480) return 3;
        if (width >= 400) return 2;
        if (width >= 320) return 1;
        return 0;
    }

    private static Typeface font(Context context, String resourceName, Typeface fallback) {
        int resourceId = context.getResources().getIdentifier(
                resourceName,
                "font",
                context.getPackageName()
        );
        if (resourceId == 0) return fallback;
        try {
            return context.getResources().getFont(resourceId);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static void bind(Preference preference, View view) {
        boolean enabled = preference.isEnabled();
        ImageView leading = view.findViewWithTag(TAG_LEADING);
        TextView title = view.findViewWithTag(TAG_TITLE);
        TextView summary = view.findViewWithTag(TAG_SUMMARY);
        int disabled = disabledColor(view.getContext());

        if (leading != null) {
            Drawable icon = preference.getIcon();
            leading.setImageDrawable(icon);
            leading.setVisibility(icon == null ? View.GONE : View.VISIBLE);
            leading.setColorFilter(
                    enabled ? secondaryTextColor(view.getContext()) : disabled,
                    PorterDuff.Mode.SRC_IN
            );
        }
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
        view.setEnabled(enabled);
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
            return createRow(getContext(), TrailingAccessory.SWITCH, false);
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
            return createRow(getContext(), TrailingAccessory.NONE, false);
        }

        @Override
        protected void onBindView(View view) {
            bind(this, view);
        }
    }

    static final class SingleChoice extends ListPreference {
        SingleChoice(Context context) {
            super(context);
        }

        @Override
        protected View onCreateView(ViewGroup parent) {
            return createRow(getContext(), TrailingAccessory.NONE, false);
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
            return createRow(getContext(), TrailingAccessory.NONE, false);
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
            return createRow(getContext(), TrailingAccessory.NONE, false);
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
            return createRow(getContext(), TrailingAccessory.NONE, true);
        }

        @Override
        protected void onBindView(View view) {
            bind(this, view);
        }
    }

}
