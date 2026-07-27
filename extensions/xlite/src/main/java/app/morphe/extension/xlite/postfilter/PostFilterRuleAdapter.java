package app.morphe.extension.xlite.postfilter;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.xlite.settings.XLiteSettingsUi;

@SuppressWarnings("deprecation")
final class PostFilterRuleAdapter extends BaseAdapter {
    interface Listener {
        void edit(PostFilterRule rule);
        void setEnabled(PostFilterRule rule, boolean enabled);
    }

    private final Context context;
    private final Listener listener;
    private List<PostFilterRule> rules = Collections.emptyList();
    private boolean interactionsEnabled = true;

    PostFilterRuleAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    void submit(List<PostFilterRule> updatedRules) {
        rules = updatedRules;
        notifyDataSetChanged();
    }

    void setInteractionsEnabled(boolean enabled) {
        if (interactionsEnabled == enabled) return;
        interactionsEnabled = enabled;
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
        row.enabled.setChecked(rule.isEnabled(), false);
        row.enabled.setEnabled(interactionsEnabled);
        row.enabled.setOnCheckedChangeListener(checked -> {
            if (interactionsEnabled) listener.setEnabled(rule, checked);
        });
        reusableView.setEnabled(interactionsEnabled);
        reusableView.setOnClickListener(interactionsEnabled ? ignored -> listener.edit(rule) : null);
        return reusableView;
    }

    private Row createRow() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setMinimumHeight(XLiteSettingsUi.dp(context, 72));
        root.setPadding(
                XLiteSettingsUi.dp(context, 20),
                XLiteSettingsUi.dp(context, 10),
                XLiteSettingsUi.dp(context, 16),
                XLiteSettingsUi.dp(context, 10)
        );
        XLiteSettingsUi.applyRippleBackground(root);

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView phrase = XLiteSettingsUi.titleText(context);
        TextView scope = XLiteSettingsUi.summaryText(context);
        scope.setPadding(0, XLiteSettingsUi.dp(context, 5), 0, 0);
        labels.addView(phrase);
        labels.addView(scope);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.setMarginEnd(XLiteSettingsUi.dp(context, 12));
        root.addView(labels, labelParams);

        XLiteSettingsUi.SwitchControl enabled = new XLiteSettingsUi.SwitchControl(context);
        enabled.setInteractive(true);
        enabled.setContentDescription(StringRef.str("piko_xlite_post_filtering_rule_enabled"));
        root.addView(enabled, new LinearLayout.LayoutParams(
                XLiteSettingsUi.dp(context, 52),
                XLiteSettingsUi.dp(context, 32)
        ));
        return new Row(root, phrase, scope, enabled);
    }

    private CharSequence scopeSummary(PostFilterRule rule) {
        if (rule.matchesContent() && rule.matchesUsernames()) {
            return StringRef.str("piko_xlite_post_filtering_scope_both");
        }
        if (rule.matchesContent()) return StringRef.str("piko_xlite_post_filtering_scope_content");
        return StringRef.str("piko_xlite_post_filtering_scope_usernames");
    }

    private static final class Row {
        final LinearLayout root;
        final TextView phrase;
        final TextView scope;
        final XLiteSettingsUi.SwitchControl enabled;

        Row(
                LinearLayout root,
                TextView phrase,
                TextView scope,
                XLiteSettingsUi.SwitchControl enabled
        ) {
            this.root = root;
            this.phrase = phrase;
            this.scope = scope;
            this.enabled = enabled;
        }
    }
}
