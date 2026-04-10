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
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class ImportExportLoginTokenPatch {
    private static final String[] USER_DATA_KEYS = {
            "account_user_id",
            "account_state",
            "account_field_version",
            "account_user_type",
            "account_settings",
            "account_teams_contributor",
            "account_teams_contributees",
            "account_user_info",
            "account_can_access_x_payments",
            "account_is_x_payments_enrolled",
            "com.twitter.android.oauth.token.teamsContributeeUserId"
    };

    /**
     * Create a JSON text for export
     */
    public static String createAccountJsonText(Account account) throws JSONException {
        AccountManager accountManager = AccountManager.get(Utils.getContext());

        JSONObject json = new JSONObject();
        json.put("username", account.name);
        json.put("token", accountManager.peekAuthToken(account, "com.twitter.android.oauth.token"));
        json.put("secret", accountManager.peekAuthToken(account, "com.twitter.android.oauth.token.secret"));

        JSONObject userData = new JSONObject();
        for (String key : USER_DATA_KEYS) {
            String value = accountManager.getUserData(account, key);
            if (value != null) {
                userData.put(key, value);
            }
        }

        json.put("userdata", userData);
        return json.toString(2);
    }

    /**
     * Add an account from imported JSON text
     */
    public static void addAccount(Context context, String jsonText) {
        try {
            JSONObject accountJson = new JSONObject(jsonText);

            String userName = accountJson.optString("username");
            String token = accountJson.optString("token");
            String secret = accountJson.optString("secret");
            if (userName.isEmpty() || token.isEmpty() || secret.isEmpty()) {
                Utils.showToastLong(StringRef.str("piko_login_token_import_failed_missing_info"));
                return;
            }

            Account account = new Account(userName, "com.twitter.android.auth.login");
            Bundle newUserData = new Bundle();
            JSONObject userData = accountJson.getJSONObject("userdata");
            Iterator<String> itr = userData.keys();
            while (itr.hasNext()) {
                String key = itr.next();
                newUserData.putString(key, userData.getString(key));
            }

            AccountManager accountManager = AccountManager.get(context);
            boolean succeeded = accountManager.addAccountExplicitly(account, null, newUserData);
            if (!succeeded) {
                Utils.showToastLong(StringRef.str("piko_login_token_import_failed_already_exist"));
                return;
            }

            accountManager.setAuthToken(account, "com.twitter.android.oauth.token", token);
            accountManager.setAuthToken(account, "com.twitter.android.oauth.token.secret", secret);

            // Show a dialog to prompt user to reopen the app.
            // Closing the activity is enough to reflect the added account.
            // Since the app begins some process immediately after an account is added,
            // we do not use Utils.restartApp() which calls System.exit(0) for safety.
            new AlertDialog.Builder(context)
                    .setTitle(StringRef.str("piko_pref_success"))
                    .setMessage(StringRef.str("piko_login_token_import_success_reopen_required"))
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        if (context instanceof Activity activity)
                            activity.finish();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setCancelable(false)
                    .show();
        } catch (JSONException e) {
            Utils.showToastLong("Failed to parse JSON: " + e.getMessage());
        } catch (Exception e) {
            Logger.printException(() -> "addAccount failure", e);
        }
    }

    /*
     * Injection point.
     */
    @SuppressWarnings({"deprecation", "unused"})
    public static void initImportButton(View view) {
        try {
            TextView pikoTextView = view.findViewById(Utils.getResourceIdentifier(view.getContext(), "import_token_text", "id"));
            pikoTextView.setTextColor(Utils.getResourceColor("twitter_blue"));
            pikoTextView.setOnClickListener(v -> {
                if (v.getContext() instanceof Activity activity) {
                    new ImportLoginTokenDialogFragment().show(activity.getFragmentManager(), null);
                } else {
                    Logger.printException(() -> "Failed to open import dialog");
                }
            });
        } catch (Exception e) {
            Logger.printException(() -> "Failed to insert import token button", e);
        }
    }

}
