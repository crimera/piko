/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.morphe.extension.twitter.patches.logintoken;

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
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

import java.io.OutputStream;

@SuppressWarnings("deprecation")
public class ExportLoginTokenFragment extends Fragment {

    private static final int CREATE_FILE_REQUEST_CODE = 31;
    private Account accountToSaveToFile;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(Utils.getResourceIdentifier("fragment_export_token", "layout"), container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Spinner spinner = view.findViewById(Utils.getResourceIdentifier("spinner", "id"));
        AccountManager accountManager = AccountManager.get(getContext());
        Account[] accounts = accountManager.getAccountsByType("com.twitter.android.auth.login");
        AccountArrayAdapter arrayAdapter = new AccountArrayAdapter(getContext(), android.R.layout.simple_spinner_dropdown_item, accounts);
        spinner.setAdapter(arrayAdapter);

        Button copyToClipboardButton = view.findViewById(Utils.getResourceIdentifier("copy_button", "id"));
        copyToClipboardButton.setOnClickListener(v -> {
            try {
                Account account = (Account) spinner.getSelectedItem();
                String jsonString = ImportExportLoginTokenPatch.createAccountJsonText(account);
                Utils.setClipboard(jsonString);
                Utils.showToastShort(StringRef.str("copied_to_clipboard"));
            } catch (Exception e) {
                Utils.showToastLong(StringRef.str("piko_pref_export_failed", StringRef.str("accounts_title")));
                Logger.printInfo(() -> "Failed to export account to clipboard", e);
            }
        });

        Button saveToFileButton = view.findViewById(Utils.getResourceIdentifier("save_to_file_button", "id"));
        saveToFileButton.setOnClickListener(v -> {
            Account account = (Account) spinner.getSelectedItem();
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
                Utils.showToastLong(StringRef.str("piko_pref_export_no_uri"));
                return;
            }
            try (OutputStream outputStream = getContext().getContentResolver().openOutputStream(data.getData())) {
                byte[] jsonStringByteArray = ImportExportLoginTokenPatch.createAccountJsonText(accountToSaveToFile).getBytes();
                outputStream.write(jsonStringByteArray);
                Utils.showToastShort(StringRef.str("piko_pref_success"));
            } catch (Exception e) {
                Utils.showToastLong(StringRef.str("piko_pref_export_failed", StringRef.str("accounts_title")));
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
