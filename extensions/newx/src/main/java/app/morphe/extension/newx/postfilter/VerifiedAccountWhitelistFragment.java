package app.morphe.extension.newx.postfilter;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.newx.settings.NewXSettingsActivity;
import app.morphe.extension.newx.settings.NewXSettingsUi;
import app.morphe.extension.newx.ui.ButtonView;
import app.morphe.extension.newx.ui.DialogView;
import app.morphe.extension.newx.settings.NewXCustomScreenFragment;
import app.morphe.extension.newx.ui.Theme;

@SuppressWarnings("deprecation")
public final class VerifiedAccountWhitelistFragment extends NewXCustomScreenFragment {
    private final VerifiedAccountWhitelistStore store = VerifiedAccountWhitelistStore.shared();
    private ArrayAdapter<String> adapter;
    private TextView emptyState;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        Context context = requireContext();
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(NewXSettingsUi.backgroundColor(context));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, matchParent());

        TextView summary = NewXSettingsUi.summaryText(context);
        summary.setText(StringRef.str("piko_newx_verified_account_whitelist_summary"));
        summary.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 16f),
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 12f)
        );
        content.addView(summary, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout listContainer = new FrameLayout(context);
        content.addView(listContainer, new LinearLayout.LayoutParams(-1, 0, 1f));

        ListView list = new ListView(context);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setPadding(0, Theme.dpToPx(context, 4f), 0, Theme.dpToPx(context, 96f));
        adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1);
        list.setAdapter(adapter);
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            String account = adapter.getItem(position);
            if (account == null) return true;
            store.remove(account);
            refreshAccounts();
            return true;
        });
        listContainer.addView(list, matchParent());

        emptyState = new TextView(context);
        emptyState.setText(StringRef.str("piko_newx_verified_account_whitelist_empty"));
        emptyState.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyState.setTextColor(Theme.secondaryText(context));
        emptyState.setGravity(Gravity.CENTER);
        int emptyPadding = Theme.dpToPx(context, 32f);
        emptyState.setPadding(emptyPadding, emptyPadding, emptyPadding, emptyPadding);
        listContainer.addView(emptyState, matchParent());

        View addButton = NewXSettingsUi.floatingActionButton(
                context,
                StringRef.str("piko_newx_verified_account_whitelist_add"),
                ignored -> showAccountDialog()
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

        refreshAccounts();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if (activity instanceof NewXSettingsActivity settingsActivity) {
            settingsActivity.setPageTitle(StringRef.str("piko_newx_verified_account_whitelist_title"));
        }
        refreshAccounts();
    }

    private void showAccountDialog() {
        Context context = requireContext();
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 8f),
                Theme.dpToPx(context, 24f),
                0
        );

        EditText account = NewXSettingsUi.textInput(
                context,
                StringRef.str("piko_newx_verified_account_whitelist_account_hint"),
                InputType.TYPE_CLASS_TEXT
        );
        form.addView(account, new LinearLayout.LayoutParams(-1, -2));

        TextView validation = NewXSettingsUi.summaryText(context);
        validation.setTextColor(Color.rgb(244, 33, 46));
        validation.setPadding(0, Theme.dpToPx(context, 8f), 0, 0);
        validation.setVisibility(View.GONE);
        form.addView(validation, new LinearLayout.LayoutParams(-1, -2));

        DialogView dialog = new DialogView(context)
                .setTitle(StringRef.str("piko_newx_verified_account_whitelist_add_title"))
                .setScrollableBodyView(form);
        dialog.getDialog().setCanceledOnTouchOutside(true);

        ButtonView cancel = new ButtonView(
                context,
                ButtonView.ButtonStyle.TEXT,
                StringRef.str("piko_newx_verified_account_whitelist_cancel")
        );
        cancel.setOnClickListener(ignored -> dialog.dismiss());

        ButtonView save = new ButtonView(
                context,
                ButtonView.ButtonStyle.TEXT,
                StringRef.str("piko_newx_verified_account_whitelist_save")
        );
        save.setOnClickListener(ignored -> {
            try {
                store.add(account.getText().toString());
                refreshAccounts();
                dialog.dismiss();
            } catch (VerifiedAccountWhitelistStore.ValidationException exception) {
                validation.setText(validationMessage(exception.getError()));
                validation.setVisibility(View.VISIBLE);
            }
        });

        dialog.addButton(cancel).addButton(save).show();
    }

    private CharSequence validationMessage(VerifiedAccountWhitelistStore.ValidationError error) {
        return switch (error) {
            case BLANK_ACCOUNT -> StringRef.str("piko_newx_verified_account_whitelist_error_blank");
            case DUPLICATE_ACCOUNT -> StringRef.str("piko_newx_verified_account_whitelist_error_duplicate");
        };
    }

    private void refreshAccounts() {
        if (adapter == null || emptyState == null) return;
        List<String> accounts = new ArrayList<>(store.snapshot());
        Collections.sort(accounts);
        adapter.clear();
        adapter.addAll(accounts);
        adapter.notifyDataSetChanged();
        emptyState.setVisibility(accounts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private Context requireContext() {
        Context context = getActivity();
        if (context == null) throw new IllegalStateException("Verified whitelist activity is missing");
        return context;
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(-1, -1);
    }
}
