/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.logintoken;

import static app.morphe.extension.shared.StringRef.str;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

import java.io.OutputStream;

@SuppressWarnings("deprecation")
public class ExportLoginTokenFragment extends Fragment {

    private static final int CREATE_FILE_REQUEST_CODE = 31;
    private Account accountToSaveToFile;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(ResourceUtils.getIdentifier(ResourceType.LAYOUT,
                "fragment_export_token"), container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Spinner spinner = view.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "spinner"));
        AccountManager accountManager = AccountManager.get(getContext());
        Account[] accounts = accountManager.getAccountsByType("com.twitter.android.auth.login");
        AccountArrayAdapter arrayAdapter = new AccountArrayAdapter(getContext(), android.R.layout.simple_spinner_dropdown_item, accounts);
        spinner.setAdapter(arrayAdapter);

        Button copyToClipboardButton = view.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "copy_button"));
        copyToClipboardButton.setOnClickListener(v -> {
            try {
                Account account = (Account) spinner.getSelectedItem();
                String jsonString = ImportExportLoginTokenPatch.createAccountJsonText(account);
                Utils.setClipboard(jsonString);
                Utils.showToastShort(str("copied_to_clipboard"));
            } catch (Exception e) {
                Utils.showToastLong(str("piko_pref_export_failed", str("accounts_title")));
                Logger.printInfo(() -> "Failed to export account to clipboard", e);
            }
        });

        Button saveToFileButton = view.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "save_to_file_button"));
        saveToFileButton.setOnClickListener(v -> {
            Account account = (Account) spinner.getSelectedItem();
            if (account == null) return;
            accountToSaveToFile = account;

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "piko_account_" + account.name);
            startActivityForResult(intent, CREATE_FILE_REQUEST_CODE);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        /*
         * Save to a file
         */
        if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data == null || data.getData() == null) {
                Utils.showToastLong(str("piko_pref_export_no_uri"));
                return;
            }
            try (OutputStream outputStream = getContext().getContentResolver().openOutputStream(data.getData())) {
                byte[] jsonStringByteArray = ImportExportLoginTokenPatch.createAccountJsonText(accountToSaveToFile).getBytes();
                outputStream.write(jsonStringByteArray);
                Utils.showToastShort(str("piko_pref_export_success"));
            } catch (Exception e) {
                Utils.showToastLong(str("piko_pref_export_failed", str("accounts_title")));
                Logger.printInfo(() -> "Failed to export account to a file", e);
            }
        }
        accountToSaveToFile = null;
    }

    /**
     * To show account names in spinner
     */
    private static class AccountArrayAdapter extends ArrayAdapter<Account> {
        public AccountArrayAdapter(@NonNull Context context, int resource, @NonNull Account[] accounts) {
            super(context, resource, accounts);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            Account account = getItem(position);
            view.setText(account.name);
            return view;
        }

        @Override
        public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
            TextView view = (TextView) super.getDropDownView(position, convertView, parent);
            Account account = getItem(position);
            view.setText(account.name);
            return view;
        }
    }
}
