/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.app.FragmentManager;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.crimera.downloader.FolderPickerFragment;
import app.morphe.extension.crimera.downloader.StorageUtils;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.patches.dm.DeletedMessagesActivity;
import app.morphe.extension.instagram.patches.instants.InstantsVaultActivity;
import app.morphe.extension.instagram.settings.preference.fragments.BackupPrefFragment;
import app.morphe.extension.instagram.settings.preference.fragments.RestorePrefFragment;
import app.morphe.extension.shared.Logger;

public class ActivityHook {

    private static final String PROXY_ACTIVITY_CLASS = "com.instagram.modal.ModalActivity";
    private static final String PROXY_TRANSPARENT_ACTIVITY_CLASS = "com.instagram.modal.TransparentModalActivity";
    private static final String EXTRA_PIKO = "piko";
    private static final String EXTRA_PIKO_LAUNCH_TYPE = "piko_launch_type";
    private static final String EXTRA_SHORTCUT = "shortcut";

    static final String LAUNCH_TYPE_SETTINGS = "settings";
    static final String LAUNCH_TYPE_BACKUP = "backup";
    static final String LAUNCH_TYPE_RESTORE = "restore";
    static final String LAUNCH_TYPE_FOLDER_PICKER = "folder_picker";
    static final String LAUNCH_TYPE_DELETED_MESSAGES = "deleted_messages";
    static final String LAUNCH_TYPE_INSTANTS_VAULT = "instants_vault";


    public static void launchActivity(Context context, Class<?> activityClass) {
        if (activityClass != null && activityClass.equals(InstantsVaultActivity.class)) {
            startInstantsVaultActivity(context);
            return;
        }
        if (activityClass != null && activityClass.equals(DeletedMessagesActivity.class)) {
            startDeletedMessagesActivity(context);
            return;
        }
        if (activityClass != null && activityClass.equals(SettingsActivity.class)) {
            startPikoActivity(null, null);
            return;
        }
        Intent intent = new Intent(context, activityClass);
        launchActivity(context, intent);
    }

    private static void launchActivity(Context context, Intent intent) {
        try {
            Activity currentActivity = app.morphe.extension.shared.Utils.getActivity();
            if (currentActivity != null && !currentActivity.isFinishing() && !currentActivity.isDestroyed()) {
                currentActivity.startActivity(intent);
                return;
            }
            if (context instanceof Activity && !((Activity) context).isFinishing() && !((Activity) context).isDestroyed()) {
                ((Activity) context).startActivity(intent);
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Logger.printException(() -> "launchActivity failure", e);
            PikoUtils.logger(e);
        }
    }

    private static Intent createProxyIntent(Context context, String activityClass) {
        Intent intent = new Intent();
        intent.setClassName(context.getPackageName(), activityClass);
        intent.putExtra(EXTRA_PIKO, true);
        intent.putExtra("fragment_arguments", new Bundle());
        return intent;
    }

    private static Intent createProxyIntent(Context context) {
        return createProxyIntent(context, PROXY_ACTIVITY_CLASS);
    }

    public static boolean isPikoIntent(Intent intent) {
        if (intent == null) return false;
        try {
            if (intent.getBooleanExtra(EXTRA_PIKO, false)) return true;
        } catch (Throwable ignored) {}
        try {
            if ("true".equalsIgnoreCase(intent.getStringExtra(EXTRA_PIKO))) return true;
        } catch (Throwable ignored) {}
        try {
            if (LAUNCH_TYPE_SETTINGS.equals(intent.getStringExtra(EXTRA_SHORTCUT))) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isPiko(Activity activity) {
        if (activity == null) return false;
        return isPikoIntent(activity.getIntent());
    }

    public static boolean create(Activity activity) {
        Intent intent = activity == null ? null : activity.getIntent();
        if (!isPikoIntent(intent)) return false;

        if (activity instanceof androidx.activity.ComponentActivity) {
            try {
                ((androidx.activity.ComponentActivity) activity).addOnNewIntentListener(newIntent -> handleNewIntent(activity, newIntent));
            } catch (Throwable ignored) {
            }
        }

        return dispatchLaunch(activity, intent);
    }

    public static boolean onBackPressed(Activity activity) {
        if (!isPiko(activity)) return false;
        Intent intent = activity.getIntent();
        String launchType = intent != null ? intent.getStringExtra(EXTRA_PIKO_LAUNCH_TYPE) : null;
        if (launchType == null && intent != null && LAUNCH_TYPE_SETTINGS.equals(intent.getStringExtra(EXTRA_SHORTCUT))) {
            launchType = LAUNCH_TYPE_SETTINGS;
        }
        if (launchType == null || LAUNCH_TYPE_SETTINGS.equals(launchType)) {
            return SettingsActivity.handleBackPressed(activity);
        } else if (LAUNCH_TYPE_DELETED_MESSAGES.equals(launchType)) {
            return DeletedMessagesActivity.handleBackPressed(activity);
        }
        return false;
    }

    private static void handleNewIntent(Activity activity, Intent newIntent) {
        if (!isPikoIntent(newIntent)) return;
        activity.setIntent(newIntent);
        dispatchLaunch(activity, newIntent);
    }

    private static boolean dispatchLaunch(Activity activity, Intent intent) {
        String launchType = intent.getStringExtra(EXTRA_PIKO_LAUNCH_TYPE);
        if (launchType == null && LAUNCH_TYPE_SETTINGS.equals(intent.getStringExtra(EXTRA_SHORTCUT))) {
            launchType = LAUNCH_TYPE_SETTINGS;
        }
        if (launchType == null) launchType = LAUNCH_TYPE_SETTINGS;

        Bundle extras = intent.getExtras();

        switch (launchType) {
            case LAUNCH_TYPE_SETTINGS:
                SettingsActivity.setupOnActivity(activity);
                break;
            case LAUNCH_TYPE_DELETED_MESSAGES:
                DeletedMessagesActivity.setupOnActivity(activity);
                break;
            case LAUNCH_TYPE_INSTANTS_VAULT:
                InstantsVaultActivity.setupOnActivity(activity);
                break;
            case LAUNCH_TYPE_BACKUP:
            case LAUNCH_TYPE_RESTORE:
            case LAUNCH_TYPE_FOLDER_PICKER:
                if (activity.getWindow() != null) {
                    activity.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }
                attachPickerFragment(activity, launchType, extras);
                break;
            default:
                return false;
        }
        return true;
    }

    private static void attachPickerFragment(Activity activity, String launchType, Bundle extras) {
        FragmentManager fm = activity.getFragmentManager();
        if (fm == null || fm.findFragmentByTag("piko_picker") != null) {
            return;
        }
        Fragment fragment;
        switch (launchType) {
            case LAUNCH_TYPE_BACKUP:
                fragment = new BackupPrefFragment();
                break;
            case LAUNCH_TYPE_RESTORE:
                fragment = new RestorePrefFragment();
                break;
            case LAUNCH_TYPE_FOLDER_PICKER:
                fragment = new FolderPickerFragment();
                break;
            default:
                return;
        }
        if (extras != null) {
            fragment.setArguments(extras);
        }
        fm.beginTransaction()
                .add(fragment, "piko_picker")
                .commitAllowingStateLoss();
    }

    public static void startPikoActivity(String fragment_name, String title) {
        startPikoActivity(fragment_name, title, null);
    }

    public static void startPikoActivity(String fragment_name, String title, String targetPreferenceKey) {
        Context context = PikoUtils.getContext();
        Intent intent = createProxyIntent(context);
        intent.putExtra(EXTRA_PIKO_LAUNCH_TYPE, LAUNCH_TYPE_SETTINGS);
        intent.putExtra(Constants.PIKO_FRAGMENT_NAME, fragment_name);
        intent.putExtra(Constants.PIKO_FRAGMENT_TITLE, title);
        if (targetPreferenceKey != null && !targetPreferenceKey.isEmpty()) {
            intent.putExtra(Constants.PIKO_SEARCH_TARGET_PREFERENCE_KEY, targetPreferenceKey);
        }
        launchActivity(context, intent);
    }

    public static Intent createDeletedMessagesIntent(Context context) {
        Intent intent = createProxyIntent(context);
        intent.putExtra(EXTRA_PIKO_LAUNCH_TYPE, LAUNCH_TYPE_DELETED_MESSAGES);
        return intent;
    }

    public static void startDeletedMessagesActivity(Context context) {
        launchActivity(context, createDeletedMessagesIntent(context));
    }

    public static void startInstantsVaultActivity(Context context) {
        Intent intent = createProxyIntent(context);
        intent.putExtra(EXTRA_PIKO_LAUNCH_TYPE, LAUNCH_TYPE_INSTANTS_VAULT);
        launchActivity(context, intent);
    }

    public static void launchFragment(Context ctx, String bundleKey) {
        String launchType;
        if (bundleKey.equals("piko_export_dev_overrides") || bundleKey.equals("piko_export_pref")) {
            launchType = LAUNCH_TYPE_BACKUP;
        } else if (bundleKey.equals("piko_import_dev_overrides") || bundleKey.equals("piko_import_id_mapping")
                || bundleKey.equals("piko_import_pref") || bundleKey.equals("piko_pref_add_font")) {
            launchType = LAUNCH_TYPE_RESTORE;
        } else if (bundleKey.equals("piko_download_set_path")) {
            launchType = LAUNCH_TYPE_FOLDER_PICKER;
        } else {
            return;
        }

        Intent intent = createProxyIntent(ctx, PROXY_TRANSPARENT_ACTIVITY_CLASS);
        intent.putExtra(EXTRA_PIKO_LAUNCH_TYPE, launchType);
        intent.putExtra(bundleKey, true);
        launchActivity(ctx, intent);
    }

    public static void handleUrlIntent(Boolean isVideo, String mediaUrl) {
        String dataType = "image/*";
        String exportHeaderString = str("piko_open_image_with");
        if (isVideo) {
            dataType = "video/*";
            exportHeaderString = str("piko_open_video_with");
        }

        Uri uri = Uri.parse(mediaUrl);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, dataType);
        Intent chooserIntent = Intent.createChooser(intent, exportHeaderString);
        PikoUtils.launchIntent(chooserIntent);
    }
}
