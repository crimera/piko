package app.morphe.extension.xlite.postfilter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.xlite.settings.XLiteSettingsActivity;
import app.morphe.extension.xlite.settings.XLiteSettingsUi;
import app.morphe.extension.xlite.ui.Theme;

@SuppressWarnings("deprecation")
public final class PostFilterFragment extends Fragment implements PostFilterRuleAdapter.Listener {
    private final PostFilterRuleStore store = PostFilterRuleStore.shared();
    private PostFilterRuleAdapter adapter;
    private TextView emptyState;
    private FrameLayout rulesContainer;
    private View addButton;
    private XLiteSettingsUi.SwitchRow masterSwitch;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        Context context = requireContext();
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(XLiteSettingsUi.backgroundColor(context));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, matchParent());

        masterSwitch = XLiteSettingsUi.switchRow(
                context,
                StringRef.str("piko_xlite_post_filtering_enabled_title"),
                null,
                store.isEnabled()
        );
        masterSwitch.setOnCheckedChangeListener(this::setFilteringEnabled);
        content.addView(masterSwitch, new LinearLayout.LayoutParams(-1, -2));
        content.addView(XLiteSettingsUi.divider(context));

        rulesContainer = new FrameLayout(context);
        content.addView(rulesContainer, new LinearLayout.LayoutParams(-1, 0, 1f));

        ListView list = new ListView(context);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setPadding(
                0,
                Theme.dpToPx(context, 4f),
                0,
                Theme.dpToPx(context, 96f)
        );
        adapter = new PostFilterRuleAdapter(context, this);
        list.setAdapter(adapter);
        rulesContainer.addView(list, matchParent());

        emptyState = new TextView(context);
        emptyState.setText(StringRef.str("piko_xlite_post_filtering_empty"));
        emptyState.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyState.setTextColor(Theme.secondaryText(context));
        emptyState.setGravity(Gravity.CENTER);
        int emptyPadding = Theme.dpToPx(context, 32f);
        emptyState.setPadding(emptyPadding, emptyPadding, emptyPadding, emptyPadding);
        rulesContainer.addView(emptyState, matchParent());

        addButton = XLiteSettingsUi.floatingActionButton(
                context,
                StringRef.str("piko_xlite_post_filtering_add"),
                ignored -> showRuleDialog(null)
        );
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(
                Theme.dpToPx(context, 56f),
                Theme.dpToPx(context, 56f)
        );
        addParams.gravity = Gravity.BOTTOM | Gravity.END;
        addParams.setMargins(
                0,
                0,
                Theme.dpToPx(context, 20f),
                Theme.dpToPx(context, 20f)
        );
        root.addView(addButton, addParams);

        refreshRules();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if (activity instanceof XLiteSettingsActivity settingsActivity) {
            settingsActivity.setPageTitle(StringRef.str("piko_xlite_post_filtering_title"));
        }
        refreshRules();
    }

    @Override
    public void edit(PostFilterRule rule) {
        showRuleDialog(rule);
    }

    @Override
    public void setEnabled(PostFilterRule rule, boolean enabled) {
        store.setRuleEnabled(rule.getId(), enabled);
        refreshRules();
    }

    private void showRuleDialog(@Nullable PostFilterRule editingRule) {
        Context context = requireContext();
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 8f),
                Theme.dpToPx(context, 24f),
                0
        );

        EditText phrase = XLiteSettingsUi.textInput(
                context,
                StringRef.str("piko_xlite_post_filtering_phrase_hint"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        if (editingRule != null) phrase.setText(editingRule.getPhrase());
        form.addView(phrase, new LinearLayout.LayoutParams(-1, -2));

        XLiteSettingsUi.SwitchRow matchContent = scopeSwitch(
                context,
                "piko_xlite_post_filtering_match_content",
                editingRule == null || editingRule.matchesContent()
        );
        form.addView(matchContent, new LinearLayout.LayoutParams(-1, -2));

        XLiteSettingsUi.SwitchRow matchUsernames = scopeSwitch(
                context,
                "piko_xlite_post_filtering_match_usernames",
                editingRule != null && editingRule.matchesUsernames()
        );
        form.addView(matchUsernames, new LinearLayout.LayoutParams(-1, -2));

        TextView validation = new TextView(context);
        validation.setTextColor(Color.rgb(244, 33, 46));
        validation.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        validation.setPadding(0, Theme.dpToPx(context, 8f), 0, 0);
        validation.setVisibility(View.GONE);
        form.addView(validation, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(StringRef.str(editingRule == null
                        ? "piko_xlite_post_filtering_add_title"
                        : "piko_xlite_post_filtering_edit_title"))
                .setView(form)
                .setPositiveButton(StringRef.str("piko_xlite_post_filtering_save"), null)
                .setNegativeButton(StringRef.str("piko_xlite_post_filtering_cancel"), null);
        if (editingRule != null) {
            builder.setNeutralButton(StringRef.str("piko_xlite_post_filtering_remove"), null);
        }

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            GradientDrawable dialogBg = new GradientDrawable();
            dialogBg.setColor(Theme.surfaceContainerHigh(context));
            dialogBg.setCornerRadius(Theme.dpToPx(context, 28f));
            dialog.getWindow().setBackgroundDrawable(dialogBg);
        }

        dialog.setOnShowListener(ignored -> configureDialogButtons(
                dialog,
                editingRule,
                phrase,
                matchContent,
                matchUsernames,
                validation
        ));
        dialog.show();
    }

    private void configureDialogButtons(
            AlertDialog dialog,
            @Nullable PostFilterRule editingRule,
            EditText phrase,
            XLiteSettingsUi.SwitchRow matchContent,
            XLiteSettingsUi.SwitchRow matchUsernames,
            TextView validation
    ) {
        Context context = dialog.getContext();
        Button posBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (posBtn != null) {
            posBtn.setTextColor(Theme.primaryAccent(context));
            posBtn.setOnClickListener(ignored -> {
                try {
                    if (editingRule == null) {
                        store.add(
                                phrase.getText().toString(),
                                matchContent.isChecked(),
                                matchUsernames.isChecked()
                        );
                    } else {
                        store.update(
                                editingRule.getId(),
                                phrase.getText().toString(),
                                matchContent.isChecked(),
                                matchUsernames.isChecked()
                        );
                    }
                    refreshRules();
                    dialog.dismiss();
                } catch (PostFilterRuleStore.ValidationException exception) {
                    validation.setText(validationMessage(exception.getError()));
                    validation.setVisibility(View.VISIBLE);
                }
            });
        }

        Button negBtn = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (negBtn != null) {
            negBtn.setTextColor(Theme.secondaryText(context));
        }

        if (editingRule != null) {
            Button neuBtn = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            if (neuBtn != null) {
                neuBtn.setTextColor(Color.rgb(244, 33, 46));
                neuBtn.setOnClickListener(ignored -> {
                    store.remove(editingRule.getId());
                    refreshRules();
                    dialog.dismiss();
                });
            }
        }
    }

    private CharSequence validationMessage(PostFilterRuleStore.ValidationError error) {
        return switch (error) {
            case BLANK_PHRASE -> StringRef.str("piko_xlite_post_filtering_error_blank");
            case NO_SCOPE -> StringRef.str("piko_xlite_post_filtering_error_scope");
            case DUPLICATE_PHRASE -> StringRef.str("piko_xlite_post_filtering_error_duplicate");
        };
    }

    private XLiteSettingsUi.SwitchRow scopeSwitch(
            Context context,
            String textResource,
            boolean checked
    ) {
        return XLiteSettingsUi.switchRow(context, StringRef.str(textResource), null, checked);
    }

    private void refreshRules() {
        if (adapter == null || emptyState == null) return;
        java.util.List<PostFilterRule> rules = store.snapshot().getRules();
        adapter.submit(rules);
        emptyState.setVisibility(rules.isEmpty() ? View.VISIBLE : View.GONE);
        if (masterSwitch != null && masterSwitch.isChecked() != store.isEnabled()) {
            masterSwitch.setOnCheckedChangeListener(null);
            masterSwitch.setChecked(store.isEnabled(), false);
            masterSwitch.setOnCheckedChangeListener(this::setFilteringEnabled);
        }
        applyFilteringEnabled(store.isEnabled());
    }

    private void setFilteringEnabled(boolean enabled) {
        store.setEnabled(enabled);
        applyFilteringEnabled(enabled);
    }

    private void applyFilteringEnabled(boolean enabled) {
        if (rulesContainer == null || addButton == null || adapter == null) return;
        adapter.setInteractionsEnabled(enabled);
        rulesContainer.setEnabled(enabled);
        rulesContainer.setAlpha(enabled ? 1f : 0.45f);
        addButton.setEnabled(enabled);
        addButton.setAlpha(enabled ? 1f : 0.45f);
    }

    private Context requireContext() {
        Context context = getActivity();
        if (context == null) throw new IllegalStateException("Post-filter activity is missing");
        return context;
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

}
