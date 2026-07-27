package app.morphe.extension.xlite.postfilter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.xlite.settings.XLiteSettingsActivity;

@SuppressWarnings("deprecation")
public final class PostFilterFragment extends Fragment implements PostFilterRuleAdapter.Listener {
    private final PostFilterRuleStore store = PostFilterRuleStore.shared();
    private PostFilterRuleAdapter adapter;
    private TextView emptyState;
    private Switch masterSwitch;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        Context context = requireContext();
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(backgroundColor(context));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, matchParent());

        masterSwitch = new Switch(context);
        masterSwitch.setText(StringRef.str("piko_xlite_post_filtering_enabled_title"));
        masterSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        masterSwitch.setTextColor(primaryTextColor(context));
        masterSwitch.setGravity(Gravity.CENTER_VERTICAL);
        masterSwitch.setPadding(dp(context, 20), dp(context, 12), dp(context, 16), dp(context, 12));
        masterSwitch.setMinHeight(dp(context, 64));
        masterSwitch.setChecked(store.isEnabled());
        masterSwitch.setOnCheckedChangeListener((ignored, enabled) -> store.setEnabled(enabled));
        content.addView(masterSwitch, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout listContainer = new FrameLayout(context);
        content.addView(listContainer, new LinearLayout.LayoutParams(-1, 0, 1f));

        ListView list = new ListView(context);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setPadding(0, dp(context, 4), 0, dp(context, 96));
        adapter = new PostFilterRuleAdapter(context, this);
        list.setAdapter(adapter);
        listContainer.addView(list, matchParent());

        emptyState = new TextView(context);
        emptyState.setText(StringRef.str("piko_xlite_post_filtering_empty"));
        emptyState.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyState.setTextColor(secondaryTextColor(context));
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(dp(context, 32), dp(context, 32), dp(context, 32), dp(context, 32));
        listContainer.addView(emptyState, matchParent());

        AddButton addButton = new AddButton(context);
        addButton.setContentDescription(StringRef.str("piko_xlite_post_filtering_add"));
        addButton.setOnClickListener(ignored -> showRuleDialog(null));
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(dp(context, 56), dp(context, 56));
        addParams.gravity = Gravity.BOTTOM | Gravity.END;
        addParams.setMargins(0, 0, dp(context, 20), dp(context, 20));
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
        form.setPadding(dp(context, 24), dp(context, 8), dp(context, 24), 0);

        EditText phrase = new EditText(context);
        phrase.setHint(StringRef.str("piko_xlite_post_filtering_phrase_hint"));
        phrase.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (editingRule != null) phrase.setText(editingRule.getPhrase());
        form.addView(phrase, new LinearLayout.LayoutParams(-1, -2));

        Switch matchContent = scopeSwitch(
                context,
                "piko_xlite_post_filtering_match_content",
                editingRule == null || editingRule.matchesContent()
        );
        form.addView(matchContent, new LinearLayout.LayoutParams(-1, -2));

        Switch matchUsernames = scopeSwitch(
                context,
                "piko_xlite_post_filtering_match_usernames",
                editingRule != null && editingRule.matchesUsernames()
        );
        form.addView(matchUsernames, new LinearLayout.LayoutParams(-1, -2));

        TextView validation = new TextView(context);
        validation.setTextColor(Color.rgb(244, 33, 46));
        validation.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        validation.setPadding(0, dp(context, 8), 0, 0);
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
            Switch matchContent,
            Switch matchUsernames,
            TextView validation
    ) {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(ignored -> {
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
        if (editingRule == null) return;
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(ignored -> {
            store.remove(editingRule.getId());
            refreshRules();
            dialog.dismiss();
        });
    }

    private CharSequence validationMessage(PostFilterRuleStore.ValidationError error) {
        return switch (error) {
            case BLANK_PHRASE -> StringRef.str("piko_xlite_post_filtering_error_blank");
            case NO_SCOPE -> StringRef.str("piko_xlite_post_filtering_error_scope");
            case DUPLICATE_PHRASE -> StringRef.str("piko_xlite_post_filtering_error_duplicate");
        };
    }

    private Switch scopeSwitch(Context context, String textResource, boolean checked) {
        Switch scope = new Switch(context);
        scope.setText(StringRef.str(textResource));
        scope.setTextColor(primaryTextColor(context));
        scope.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        scope.setGravity(Gravity.CENTER_VERTICAL);
        scope.setMinHeight(dp(context, 52));
        scope.setChecked(checked);
        return scope;
    }

    private void refreshRules() {
        if (adapter == null || emptyState == null) return;
        java.util.List<PostFilterRule> rules = store.snapshot().getRules();
        adapter.submit(rules);
        emptyState.setVisibility(rules.isEmpty() ? View.VISIBLE : View.GONE);
        if (masterSwitch != null && masterSwitch.isChecked() != store.isEnabled()) {
            masterSwitch.setOnCheckedChangeListener(null);
            masterSwitch.setChecked(store.isEnabled());
            masterSwitch.setOnCheckedChangeListener((ignored, enabled) -> store.setEnabled(enabled));
        }
    }

    private Context requireContext() {
        Context context = getActivity();
        if (context == null) throw new IllegalStateException("Post-filter activity is missing");
        return context;
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private static int backgroundColor(Context context) {
        return isDark(context) ? Color.BLACK : Color.WHITE;
    }

    private static int primaryTextColor(Context context) {
        return isDark(context) ? Color.WHITE : Color.BLACK;
    }

    private static int secondaryTextColor(Context context) {
        return isDark(context) ? Color.LTGRAY : Color.DKGRAY;
    }

    private static boolean isDark(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        ));
    }

    private static final class AddButton extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        AddButton(Context context) {
            super(context);
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(Color.rgb(29, 155, 240));
            setBackground(background);
            setElevation(dp(context, 6));
            setClickable(true);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float radius = dp(getContext(), 9);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(dp(getContext(), 2.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, paint);
            canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, paint);
        }
    }
}
