package app.morphe.extension.xlite.featureswitches;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
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

    private static final int VIEW_TYPE_ENTRY = 0;
    private static final int VIEW_TYPE_SECTION = 1;
    private static final String OVERRIDDEN_SECTION =
            "piko_xlite_feature_switch_overridden_section";
    private static final String NOT_OVERRIDDEN_SECTION =
            "piko_xlite_feature_switch_not_overridden_section";

    private final Context context;
    private final Listener listener;
    private List<FeatureSwitchStore.Entry> entries = Collections.emptyList();
    private List<DisplayItem> items = Collections.emptyList();
    private boolean overriddenCollapsed;
    private boolean defaultCollapsed;
    private String query = "";

    FeatureSwitchAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    void submit(List<FeatureSwitchStore.Entry> updatedEntries, String updatedQuery) {
        entries = Collections.unmodifiableList(new ArrayList<>(updatedEntries));
        query = updatedQuery == null ? "" : updatedQuery.trim();
        items = displayItems(entries);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        DisplayItem item = items.get(position);
        if (!(item instanceof EntryItem entryItem)) return position;
        return entryItem.entry.getKey().hashCode();
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof SectionItem
                ? VIEW_TYPE_SECTION
                : VIEW_TYPE_ENTRY;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @Override
    public View getView(int position, View reusableView, ViewGroup parent) {
        DisplayItem item = items.get(position);
        if (item instanceof SectionItem sectionItem) {
            SectionHeader header = reusableView instanceof SectionHeader
                    ? (SectionHeader) reusableView
                    : createSectionHeader();
            header.setLayoutParams(new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            header.bind(StringRef.str(sectionItem.titleResourceName), sectionItem.collapsed);
            header.setOnClickListener(ignored -> toggleSection(sectionItem.overridden));
            return header;
        }

        Row row;
        if (reusableView instanceof LinearLayout && reusableView.getTag() instanceof Row existing) {
            row = existing;
        } else {
            row = createRow();
            reusableView = row.root;
            reusableView.setTag(row);
        }

        FeatureSwitchStore.Entry entry = ((EntryItem) item).entry;
        boolean overridden = entry.isOverridden();
        row.key.setTypeface(row.keyTypeface);
        row.value.setTypeface(
                Typeface.create(row.valueTypeface, overridden ? Typeface.BOLD : Typeface.NORMAL)
        );
        row.key.setText(highlightedKey(entry.getKey()));
        row.value.setText(valueText(entry.getEffectiveValue()));
        reusableView.setOnClickListener(ignored -> listener.edit(entry));
        return reusableView;
    }

    private List<DisplayItem> displayItems(List<FeatureSwitchStore.Entry> entries) {
        List<DisplayItem> result = new ArrayList<>(entries.size() + 2);
        appendSection(result, entries, true, OVERRIDDEN_SECTION);
        appendSection(result, entries, false, NOT_OVERRIDDEN_SECTION);
        return Collections.unmodifiableList(result);
    }

    private void appendSection(
            List<DisplayItem> result,
            List<FeatureSwitchStore.Entry> entries,
            boolean overridden,
            String titleResourceName
    ) {
        boolean collapsed = isSectionCollapsed(overridden);
        boolean hasEntries = false;
        for (FeatureSwitchStore.Entry entry : entries) {
            if (entry.isOverridden() != overridden) continue;
            if (!hasEntries) {
                result.add(new SectionItem(titleResourceName, overridden, collapsed));
                hasEntries = true;
            }
            if (!collapsed) result.add(new EntryItem(entry));
        }
    }

    private boolean isSectionCollapsed(boolean overridden) {
        return overridden ? overriddenCollapsed : defaultCollapsed;
    }

    private void toggleSection(boolean overridden) {
        if (overridden) {
            overriddenCollapsed = !overriddenCollapsed;
        } else {
            defaultCollapsed = !defaultCollapsed;
        }
        items = displayItems(entries);
        notifyDataSetChanged();
    }

    private SectionHeader createSectionHeader() {
        return new SectionHeader(context);
    }

    private Row createRow() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
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
        key.setSingleLine(true);
        key.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        keyParams.setMarginEnd(Theme.dpToPx(context, 12f));
        root.addView(key, keyParams);

        TextView value = XLiteSettingsUi.summaryText(context);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setMaxWidth(Theme.dpToPx(context, 180f));
        root.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
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

    private String valueText(Object value) {
        if (value == null) return StringRef.str("piko_xlite_feature_switch_null").toString();
        if (value instanceof List<?> list) {
            if (list.isEmpty()) return "[]";
            return "[" + String.join(", ", list.stream()
                    .map(String::valueOf)
                    .toList()) + "]";
        }
        if (value instanceof String string && string.isEmpty()) return "\"\"";
        return String.valueOf(value);
    }

    private interface DisplayItem {
    }

    private static final class SectionItem implements DisplayItem {
        final String titleResourceName;
        final boolean overridden;
        final boolean collapsed;

        SectionItem(String titleResourceName, boolean overridden, boolean collapsed) {
            this.titleResourceName = titleResourceName;
            this.overridden = overridden;
            this.collapsed = collapsed;
        }
    }

    private static final class SectionHeader extends LinearLayout {
        private final TextView title;
        private final ChevronView chevron;

        SectionHeader(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(
                    Theme.dpToPx(context, 20f),
                    Theme.dpToPx(context, 16f),
                    Theme.dpToPx(context, 20f),
                    Theme.dpToPx(context, 6f)
            );
            setClickable(true);
            setFocusable(true);
            XLiteSettingsUi.applyRippleBackground(this);

            title = XLiteSettingsUi.summaryText(context);
            title.setTextSize(14);
            title.setTextColor(Theme.secondaryText(context));
            title.setTypeface(Typeface.create(title.getTypeface(), Typeface.BOLD));
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setSingleLine(true);
            LayoutParams titleParams = new LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            );
            titleParams.setMarginEnd(Theme.dpToPx(context, 12f));
            addView(title, titleParams);

            chevron = new ChevronView(context);
            addView(chevron, new LayoutParams(
                    Theme.dpToPx(context, 24f),
                    Theme.dpToPx(context, 24f)
            ));
        }

        void bind(CharSequence text, boolean collapsed) {
            title.setText(text);
            chevron.setCollapsed(collapsed);
        }
    }

    private static final class ChevronView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean collapsed;

        ChevronView(Context context) {
            super(context);
        }

        void setCollapsed(boolean collapsed) {
            if (this.collapsed == collapsed) return;
            this.collapsed = collapsed;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Context context = getContext();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Theme.dpToPx(context, 2f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Theme.secondaryText(context));

            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float halfWidth = Theme.dpToPx(context, 5f);
            float halfHeight = Theme.dpToPx(context, 3f);
            Path path = new Path();
            if (collapsed) {
                path.moveTo(centerX - halfWidth, centerY - halfHeight);
                path.lineTo(centerX, centerY + halfHeight);
                path.lineTo(centerX + halfWidth, centerY - halfHeight);
            } else {
                path.moveTo(centerX - halfWidth, centerY + halfHeight);
                path.lineTo(centerX, centerY - halfHeight);
                path.lineTo(centerX + halfWidth, centerY + halfHeight);
            }
            canvas.drawPath(path, paint);
        }
    }

    private static final class EntryItem implements DisplayItem {
        final FeatureSwitchStore.Entry entry;

        EntryItem(FeatureSwitchStore.Entry entry) {
            this.entry = entry;
        }
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
