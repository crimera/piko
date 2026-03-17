package app.morphe.extension.twitter.patches.logintoken;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
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
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

import java.io.OutputStream;

/**
 * A screen in piko settings to export or remove accounts from the app.
 * This fragment takes an argument to select either the export or remove screen.
 */
@SuppressWarnings("deprecation")
public class ExportLoginTokenFragment extends Fragment {

    private static final int CREATE_FILE_REQUEST_CODE = 31;
    private Account accountToSaveToFile;
    private boolean isRemovingAccount = false;

    @Override
    public void onCreate(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isRemovingAccount = getArguments().getBoolean("isRemoveAccount");
    }

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

        TextView descriptionView = view.findViewById(Utils.getResourceIdentifier("textview_description", "id"));
        Button button1 = view.findViewById(Utils.getResourceIdentifier("button1", "id"));
        Button button2 = view.findViewById(Utils.getResourceIdentifier("button2", "id"));

        if (!isRemovingAccount) {
            // Set by resource identifier to preserve formatting tags
            descriptionView.setText(Utils.getResourceIdentifier("piko_login_token_export_screen_description", "string"));

            // Copy to clipboard button
            button1.setText(StringRef.str("piko_login_token_export_copy_to_clipboard"));
            button1.setOnClickListener(v -> {
                try {
                    Account account = (Account) spinner.getSelectedItem();
                    String jsonString = ImportExportLoginTokenPatch.createAccountJsonText(account);
                    Utils.setClipboard(jsonString);
                    Utils.showToastShort(StringRef.str("copied_to_clipboard"));
                } catch (Exception e) {
                    Utils.showToastLong(StringRef.str("piko_pref_export_failed", StringRef.str("accounts_title")));
                    app.morphe.extension.twitter.Utils.logger(e);
                }
            });

            // Save to file button
            button2.setText(StringRef.str("piko_login_token_export_save_to_file"));
            button2.setOnClickListener(v -> {
                Account account = (Account) spinner.getSelectedItem();
                accountToSaveToFile = account;

                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "piko_account_" + account.name);
                startActivityForResult(intent, CREATE_FILE_REQUEST_CODE);
            });
        } else {
            // Set by resource identifier to preserve formatting tags
            descriptionView.setText(Utils.getResourceIdentifier("piko_login_token_remove_screen_description", "string"));

            /*
             * Show a button to remove account from the app.
             *
             * To remove the account, set a dummy auth token and intentionally trigger the automatic logout logic due to an error.
             * removeAccount() cannot be used here as it sends a logout request to the server.
             * removeAccountExplicitly() also cannot be used because it leaves unnecessary data in the app data.
             *
             * We cannot set an empty string as the token. This will result in an incomplete state without triggering automatic logout.
             * If this happens, you can simply log out from the settings because the logout request will fail because the token is invalid.
             */
            button1.setText(StringRef.str("piko_login_token_remove_account_button_text"));
            button1.setOnClickListener(v -> {
                // Show a confirmation dialog first
                new AlertDialog.Builder(getContext())
                        .setMessage(StringRef.str("piko_login_token_remove_account_confirm_text"))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            // Set dummy tokens
                            Account account = (Account) spinner.getSelectedItem();
                            accountManager.setAuthToken(account, "com.twitter.android.oauth.token", "a");
                            accountManager.setAuthToken(account, "com.twitter.android.oauth.token.secret", "a");
                            // Show restart dialog
                            new AlertDialog.Builder(getContext())
                                    .setTitle(StringRef.str("piko_pref_success"))
                                    .setMessage(StringRef.str("piko_login_token_import_success_restart_required"))
                                    .setPositiveButton(android.R.string.ok, (dialog1, which1) -> Utils.restartApp(getContext()))
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .setCancelable(false)
                                    .show();
                        })
                        .show();
            });
            button2.setVisibility(View.GONE);

            // Show hint for single account
            if (spinner.getAdapter().getCount() == 1) {
                view.findViewById(Utils.getResourceIdentifier("textview_hintbox", "id"))
                        .setVisibility(View.VISIBLE);
            }
        }
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
                app.morphe.extension.twitter.Utils.logger(e);
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
