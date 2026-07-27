package app.morphe.extension.xlite.postfilter;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

import app.morphe.extension.shared.StringRef;

@SuppressWarnings("deprecation")
final class PostFilterRuleAdapter extends BaseAdapter {
    interface Listener {
        void edit(PostFilterRule rule);
        void setEnabled(PostFilterRule rule, boolean enabled);
    }

    private final Context context;
    private final Listener listener;
    private List<PostFilterRule> rules = Collections.emptyList();

    PostFilterRuleAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    void submit(List<PostFilterRule> updatedRules) {
        rules = updatedRules;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return rules.size();
    }

    @Override
    public PostFilterRule getItem(int position) {
        return rules.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId().hashCode();
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

        PostFilterRule rule = getItem(position);
        row.phrase.setText(rule.getPhrase());
        row.scope.setText(scopeSummary(rule));
        row.enabled.setOnCheckedChangeListener(null);
        row.enabled.setChecked(rule.isEnabled());
        row.enabled.setOnCheckedChangeListener((ignored, checked) ->
                listener.setEnabled(rule, checked));
        reusableView.setOnClickListener(ignored -> listener.edit(rule));
        return reusableView;
    }

    private Row createRow() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setMinimumHeight(dp(72));
        root.setPadding(dp(20), dp(10), dp(16), dp(10));
        root.setBackground(selectableBackground());

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView phrase = text(17, primaryTextColor());
        TextView scope = text(14, secondaryTextColor());
        scope.setPadding(0, dp(5), 0, 0);
        labels.addView(phrase);
        labels.addView(scope);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.setMarginEnd(dp(12));
        root.addView(labels, labelParams);

        Switch enabled = new Switch(context);
        enabled.setContentDescription(StringRef.str("piko_xlite_post_filtering_rule_enabled"));
        root.addView(enabled, new LinearLayout.LayoutParams(-2, -2));
        return new Row(root, phrase, scope, enabled);
    }

    private CharSequence scopeSummary(PostFilterRule rule) {
        if (rule.matchesContent() && rule.matchesUsernames()) {
            return StringRef.str("piko_xlite_post_filtering_scope_both");
        }
        if (rule.matchesContent()) return StringRef.str("piko_xlite_post_filtering_scope_content");
        return StringRef.str("piko_xlite_post_filtering_scope_usernames");
    }

    private TextView text(float size, int color) {
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        text.setTextColor(color);
        return text;
    }

    private android.graphics.drawable.Drawable selectableBackground() {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                && value.resourceId != 0) {
            return context.getDrawable(value.resourceId);
        }
        return new ColorDrawable(backgroundColor());
    }

    private int primaryTextColor() {
        return isDark() ? Color.WHITE : Color.BLACK;
    }

    private int secondaryTextColor() {
        return isDark() ? Color.LTGRAY : Color.DKGRAY;
    }

    private int backgroundColor() {
        return isDark() ? Color.BLACK : Color.WHITE;
    }

    private boolean isDark() {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        ));
    }

    private static final class Row {
        final LinearLayout root;
        final TextView phrase;
        final TextView scope;
        final Switch enabled;

        Row(LinearLayout root, TextView phrase, TextView scope, Switch enabled) {
            this.root = root;
            this.phrase = phrase;
            this.scope = scope;
            this.enabled = enabled;
        }
    }
}
