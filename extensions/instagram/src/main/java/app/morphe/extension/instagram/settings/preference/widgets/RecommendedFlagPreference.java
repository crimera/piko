/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.preference.Preference;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import app.morphe.extension.instagram.patches.devFlags.Flag;
import app.morphe.extension.instagram.patches.devFlags.FlagState;
import app.morphe.extension.instagram.settings.preference.Helper;
import app.morphe.extension.shared.Utils;

import static app.morphe.extension.instagram.utils.IgStr.str;

/** Keeps flag values separate from their JSON descriptions while using the shared dialogs. */
public final class RecommendedFlagPreference {
    private RecommendedFlagPreference() {}

    public static Preference create(Context context, Flag flag, boolean showCategory) {
        Preference preference = flag.isLongType()
                ? new NumberPreference(context, flag, showCategory)
                : new BooleanPreference(context, flag, showCategory);
        preference.setTitle(flag.getName());
        preference.setKey(flag.getCode().key);
        preference.setDefaultValue(flag.getCode().defaultValue);
        return preference;
    }

    private static CharSequence summary(Flag flag, String value, boolean showCategory) {
        boolean modified = value != null && !value.isEmpty()
                && !FlagState.DEFAULT.toString().equals(value);
        String label = str("piko_array_default");
        if (modified) {
            label = flag.isLongType() ? value : str(FlagState.ENABLE.toString().equals(value)
                    ? "piko_array_recmd_flag_state_enable" : "piko_array_recmd_flag_state_disable");
        }
        SpannableStringBuilder text = new SpannableStringBuilder(label);
        if (modified) {
            text.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new ForegroundColorSpan(InstagramPreferenceStyle.primaryTextColor()),
                    0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (!flag.getDesc().isEmpty()) text.append('\n').append(flag.getDesc());
        if (showCategory) text.append('\n').append(str(flag.getCategory().titleKey()));
        return text;
    }

    private static final class BooleanPreference extends ListPref {
        private final Flag flag;
        private final boolean showCategory;

        BooleanPreference(Context context, Flag flag, boolean showCategory) {
            super(context);
            this.flag = flag;
            this.showCategory = showCategory;
            setDialogTitle(flag.getName());
            setOnPreferenceChangeListener((preference, value) -> new Helper(context).setValue(preference, value));
        }

        @Override
        public CharSequence getSummary() {
            return summary(flag, getValue(), showCategory);
        }
    }

    private static final class NumberPreference extends EditTextPref {
        private final Flag flag;
        private final boolean showCategory;

        NumberPreference(Context context, Flag flag, boolean showCategory) {
            super(context);
            this.flag = flag;
            this.showCategory = showCategory;
            setDialogTitle(flag.getName());
            setNumericOnly(true);
            getEditText().setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            setOnPreferenceChangeListener((preference, value) -> {
                save(value.toString());
                // save() also normalizes an empty field to the existing Default sentinel.
                return false;
            });
        }

        private void save(String input) {
            String value = input.trim();
            if (value.isEmpty()) value = FlagState.DEFAULT.toString();
            if (!FlagState.DEFAULT.toString().equals(value)) {
                try {
                    Long.parseLong(value);
                } catch (NumberFormatException exception) {
                    Utils.showToastShort(str("piko_rec_flags_invalid_number"));
                    return;
                }
            }
            if (new Helper(getContext()).setValue(this, value)) setText(value);
        }

        @Override
        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);
            builder.setNeutralButton(str("piko_array_default"),
                    (dialog, which) -> save(FlagState.DEFAULT.toString()));
        }

        @Override
        public CharSequence getSummary() {
            return summary(flag, getText(), showCategory);
        }
    }
}
