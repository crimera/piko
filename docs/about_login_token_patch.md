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

**This patch does NOT fix the login attestation problem. To get a token, you must first log in to your account using any login method.**

## How to export

Piko settings > Backup and restore > Export login token

## How to import

This varies depending on the version.

### • New login screen

This is for versions 11.90 and above. (But this is rolled out to some users even on v11.81.0 or earlier.)  
It's called `onboarding_new`.

<img alt="new_login_screen" width="360" src="https://github.com/user-attachments/assets/22d9c2b4-8947-41bb-90a2-c305b02a8833"/>

The "Login through token json" button is not displayed in this version.  
There are two workarounds to import tokens.

<details>

<summary>Method A: Using deep link</summary>

You can open the import menu by clicking the deep link.  
First, set the Piko Twitter app to open x.com links. (App Info > Open by default)  
Then click the deep link below.

[**\<\<\<CLICK HERE\>\>\>**](https://x.com/i/piko/pref)

</details>

<details>

<summary>Method B: Using very legacy login screen</summary>

The X app still contains a very legacy login screen that is no longer in use. Coincidentally, it serves as a loophole for importing tokens.

1. Long press the app icon and select "Search".
<img width="240" src="https://github.com/user-attachments/assets/72dfb2fd-d948-41f0-a28b-2206332f80fc" />

2. Tap "Sign up" in the upper right corner.
<img width="240" src="https://github.com/user-attachments/assets/1f81b83a-e7a4-49de-b448-e47dbe02cfd0" />

3. The "Import Token" button is there.
<img width="240" src="https://github.com/user-attachments/assets/36589f61-acdb-43fb-9601-20c493938e0b" />


</details>

### • Old login screen

This is for most users of versions prior to 11.81.0.  
Tap the text "Login through token json".

<img width="240" src="https://github.com/user-attachments/assets/75f5094d-6a56-47af-9768-e1fde3b77207" />


### • Add second account

If you want to import a second or subsequent account, you can import it simply from Piko settings > Backup and restore > Import login token

## WARNING

Exported tokens are highly confidential information. Never share it with anyone!  
If a third party obtains this token, they can freely access your account until you log out of the session.

Please also be careful of your clipboard or exported files.

## How it works

X for Android stores the session token into the Android system's AccountManager.  
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

## Troubleshooting

**Q: After importing a token, a notification "You were logged out of @username due to an error." appears immediately.**

A: The session has already been logged out. That token is invalid.

**Q: After importing a token, it's not logged in even after restarting the app, but there is no notification.**

A: Allow notifications for the X app and try again.

**Q: Can I use tokens from the web version of Twitter?**

A: No. Because it is completely different from the Android tokens.

## Appendix

### The structure of piko's token JSON

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

If you are using the official X app (not piko) on a rooted device, you can extract your tokens without installing piko using the following method.

Note that if you don't mind installing piko, you don't need to use this method. Just install piko over the official app using an Xposed module (e.g. Core Patch) to allow app updates across different signatures.

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
