# About "Import/Export login token" patch

## About this feature

It exports the session token for the currently logged-in account.

This is useful in the following cases:

- If you already have a device logged in and want to log in on another device
- If you want to reinstall Piko
- If you want to log into a cloned APK
- If you have another rooted device and can log in normally

Tokens and other necessary data will be exported in JSON format.  
You can import it from the login screen or piko settings.

**Important: This patch does NOT fix the login attestation problem.**

## WARNING

Exported tokens are highly confidential information. Never share it with anyone!  
If a third party obtains this token, they can freely access your account until you log out of the session.

Please also be careful of your clipboard or exported files.

## How it works

X for Android stores the session token and some cached information into the Android system's AccountManager.  
This information continues to work even if you clear the app data.  
(This is also why X still be logged in even after clearing the app data.)

Therefore, you can restore this information and log in after re-installation.

## A note when removing account from the app

Please be careful if you want to remove an account from one device after importing the token into another device.

Logging out from settings will log out of the session associated with that token.  
Therefore, if you simply press the logout button in the settings, you will also be logged out of other devices using the same token.
This also applies when you press "Remove account" in the account manager in the device settings app.

There are two ways to remove an account from the app without logging out.

- Press the Logout button in airplane mode. (no internet connections)
- If the app has only one account currently logged in, just uninstall and reinstall the app.

In other words, you just need to prevent the logout request from being sent to the server.

Also, when you factory reset your device, you will need to uninstall the Twitter app beforehand.
It has been reported that the token is logged out when performing a factory reset.

## Regarding the new login screen

Recently, Twitter has been testing a new redesigned login screen for some users.  
This patch does not yet support this new login screen, and the "Login through token json" button will not be displayed.

<img alt="new_login_screen" width="360" src="https://github.com/user-attachments/assets/22d9c2b4-8947-41bb-90a2-c305b02a8833"/>

As a workaround, you can open the import menu by clicking the deep link.  
First, set the Piko Twitter app to open x.com links. (App Info > Open by default)  
Then click the deep link below.

[**\<\<\<CLICK HERE\>\>\>**](https://x.com/i/piko/pref)

## Troubleshooting

**Q: After importing a token, a notification "You were logged out of @username due to an error." appears immediately.**

A: The session has already been logged out. That token cannot be used.

**Q: After importing a token, it's not logged in even after restarting the app, but there is no notification.**

A: Allow notifications for the X app and try again.

**Q: Can I use tokens from the web version of Twitter?**

A: No. Because it is completely different from the Android token.

## Appendix

### The format of piko's token file

```json
{
  "username": "",
  "token": "",
  "secret": "",
  "userdata": {
    "account_user_id": "",
    "account_state": "",
    "account_field_version": "",
    "account_user_type": "",
    "account_settings": "",
    "account_teams_contributor": "",
    "account_teams_contributees": "",
    "account_user_info": "",
    "account_can_access_x_payments": "",
    "account_is_x_payments_enrolled": "",
    "com.twitter.android.oauth.token.teamsContributeeUserId": ""
  }
}
```

- "token" is the value of an auth token `com.twitter.android.oauth.token` from AccountManager
- "secret" is the value of an auth token `com.twitter.android.oauth.token.secret` from AccountManager
- "userdata" is an array of the user data stored in AccountManager in key-value format

Not all keys in "userdata" necessarily exist.  
However, some keys are required. If any required keys are missing when importing an account, the X app will remove that account immediately after it is imported.

### Get token from rooted device without piko

If you are using official X instead of piko on a rooted device (e.g., using an Xposed module) and want to export tokens to use with piko, you can extract your tokens and user data without piko using the following method.

1. Use any file manager app that supports root access
2. Get `/data/system_ce/0/accounts_ce.db`
3. Open it with any SQLite database browser/editor app
4. Open `accounts` table
5. Remember the number of `_id` of the account you want to export
6. Open `authtokens` table
7. Find records where `accounts_id` field is the number you have remembered above
8. Copy the values of `com.twitter.android.oauth.token` and `com.twitter.android.oauth.token.secret` into the corresponding properties in the JSON format shown above
9. Open `extras` table
10. Find records where `accounts_id` field is the number you have remembered above
11. Copy all the keys and values into the `userdata` property of the JSON format above. Note that `account_user_info` is a JSON text in itself, so you must escape all double quotes.
