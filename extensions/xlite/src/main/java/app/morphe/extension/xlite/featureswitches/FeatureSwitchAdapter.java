package app.morphe.extension.xlite.featureswitches;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.xlite.settings.XLiteSettingsUi;
import app.morphe.extension.xlite.ui.Theme;

final class FeatureSwitchAdapter extends BaseAdapter {
    interface Listener {
        void edit(FeatureSwitchStore.Entry entry);
    }

    private final Context context;
    private final Listener listener;
    private List<FeatureSwitchStore.Entry> entries = Collections.emptyList();
    private String query = "";

    FeatureSwitchAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    void submit(List<FeatureSwitchStore.Entry> updatedEntries, String updatedQuery) {
        entries = updatedEntries;
        query = updatedQuery == null ? "" : updatedQuery.trim();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return entries.size();
    }

    @Override
    public FeatureSwitchStore.Entry getItem(int position) {
        return entries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getKey().hashCode();
    }

    @Override
    public View getView(int position, View reusableView, ViewGroup parent) {
        Row row;
        if (reusableView instanceof LinearLayout && reusableView.getTag() instanceof Row existing) {
            row = existing;
        } else {
            row = createRow();
            reusableView = row.root;
            reusableView.setTag(row);
        }

        FeatureSwitchStore.Entry entry = getItem(position);
        row.key.setTypeface(
                Typeface.create(row.keyTypeface, entry.isOverridden() ? Typeface.BOLD : Typeface.NORMAL)
        );
        row.value.setTypeface(
                Typeface.create(row.valueTypeface, entry.isOverridden() ? Typeface.BOLD : Typeface.NORMAL)
        );
        row.key.setText(highlightedKey(entry.getKey()));
        row.value.setText(summary(entry));
        reusableView.setOnClickListener(ignored -> listener.edit(entry));
        return reusableView;
    }

    private Row createRow() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setMinimumHeight(Theme.dpToPx(context, 68f));
        root.setPadding(
                Theme.dpToPx(context, 20f),
                Theme.dpToPx(context, 10f),
                Theme.dpToPx(context, 20f),
                Theme.dpToPx(context, 10f)
        );
        XLiteSettingsUi.applyRippleBackground(root);

        TextView key = XLiteSettingsUi.titleText(context);
        TextView value = XLiteSettingsUi.summaryText(context);
        value.setPadding(0, Theme.dpToPx(context, 4f), 0, 0);
        value.setSingleLine(true);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(key, new LinearLayout.LayoutParams(-1, -2));
        root.addView(value, new LinearLayout.LayoutParams(-1, -2));
        return new Row(root, key, value, key.getTypeface(), value.getTypeface());
    }

    private CharSequence highlightedKey(String key) {
        if (query.isEmpty()) return key;
        int start = key.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
        if (start < 0) return key;
        SpannableString highlighted = new SpannableString(key);
        highlighted.setSpan(
                new ForegroundColorSpan(Theme.primaryAccent(context)),
                start,
                start + query.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return highlighted;
    }

    private CharSequence summary(FeatureSwitchStore.Entry entry) {
        String type = StringRef.str(typeResource(entry.getType())).toString();
        String value = valueText(entry.getEffectiveValue());
        if (!entry.isOverridden()) return type + " · " + value;
        return StringRef.str(
                "piko_xlite_feature_switch_override_summary",
                type,
                value
        );
    }

    private String typeResource(FeatureSwitchStore.ValueType type) {
        return switch (type) {
            case BOOLEAN -> "piko_xlite_feature_switch_type_boolean";
            case INT -> "piko_xlite_feature_switch_type_int";
            case LONG -> "piko_xlite_feature_switch_type_long";
            case FLOAT -> "piko_xlite_feature_switch_type_float";
            case DOUBLE -> "piko_xlite_feature_switch_type_double";
            case STRING -> "piko_xlite_feature_switch_type_string";
            case STRING_LIST -> "piko_xlite_feature_switch_type_list";
        };
    }

    private String valueText(Object value) {
        if (value == null) return StringRef.str("piko_xlite_feature_switch_null").toString();
        if (value instanceof List<?> list) return String.join(", ", list.stream()
                .map(String::valueOf)
                .toList());
        return String.valueOf(value);
    }

    private static final class Row {
        final LinearLayout root;
        final TextView key;
        final TextView value;
        final Typeface keyTypeface;
        final Typeface valueTypeface;

        Row(
                LinearLayout root,
                TextView key,
                TextView value,
                Typeface keyTypeface,
                Typeface valueTypeface
        ) {
            this.root = root;
            this.key = key;
            this.value = value;
            this.keyTypeface = keyTypeface;
            this.valueTypeface = valueTypeface;
        }
    }
}
