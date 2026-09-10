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

import androidx.activity.ComponentActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.crimera.downloader.FolderPickerFragment;
import app.morphe.extension.crimera.downloader.StorageUtils;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.patches.dm.DeletedMessagesActivity;
import app.morphe.extension.instagram.settings.preference.fragments.BackupPrefFragment;
import app.morphe.extension.instagram.settings.preference.fragments.RestorePrefFragment;
import app.morphe.extension.shared.Logger;

public class ActivityHook {

    private static final String PROXY_ACTIVITY_CLASS = "com.instagram.modal.ModalActivity";
    private static final String PROXY_TRANSPARENT_ACTIVITY_CLASS = "com.instagram.modal.TransparentModalActivity";
    private static final String EXTRA_PIKO = "piko";
    private static final String EXTRA_PIKO_LAUNCH_TYPE = "piko_launch_type";

    static final String LAUNCH_TYPE_SETTINGS = "settings";
    static final String LAUNCH_TYPE_BACKUP = "backup";
    static final String LAUNCH_TYPE_RESTORE = "restore";
    static final String LAUNCH_TYPE_FOLDER_PICKER = "folder_picker";
    static final String LAUNCH_TYPE_DELETED_MESSAGES = "deleted_messages";

    static {
        StorageUtils.setStorageAccessLauncher(ctx -> launchFragment(ctx, "piko_download_set_path"));
    }

    private static void launchActivity(Context context, Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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

    public static boolean isPiko(Activity activity) {
        if (activity == null) return false;
        Intent intent = activity.getIntent();
        return intent != null && intent.getBooleanExtra(EXTRA_PIKO, false);
    }

    public static boolean create(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null || !intent.getBooleanExtra(EXTRA_PIKO, false)) return false;

        if (activity instanceof ComponentActivity) {
            try {
                ((ComponentActivity) activity).addOnNewIntentListener(newIntent -> handleNewIntent(activity, newIntent));
            } catch (Throwable ignored) {
            }
        }

        return dispatchLaunch(activity, intent);
    }

    private static void handleNewIntent(Activity activity, Intent newIntent) {
        if (newIntent == null || !newIntent.getBooleanExtra(EXTRA_PIKO, false)) return;
        activity.setIntent(newIntent);
        dispatchLaunch(activity, newIntent);
    }

    private static boolean dispatchLaunch(Activity activity, Intent intent) {
        String launchType = intent.getStringExtra(EXTRA_PIKO_LAUNCH_TYPE);
        if (launchType == null) launchType = LAUNCH_TYPE_SETTINGS;

        Bundle extras = intent.getExtras();

        switch (launchType) {
            case LAUNCH_TYPE_SETTINGS:
                SettingsActivity.setupOnActivity(activity);
                break;
            case LAUNCH_TYPE_DELETED_MESSAGES:
                DeletedMessagesActivity.setupOnActivity(activity);
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
        if (activity instanceof FragmentActivity) {
            FragmentManager fm = ((FragmentActivity) activity).getSupportFragmentManager();
            if (fm.findFragmentByTag("piko_picker") != null) {
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
    }

    public static void startPikoActivity(String fragment_name, String title) {
        Context context = PikoUtils.getContext();
        Intent intent = createProxyIntent(context);
        intent.putExtra(EXTRA_PIKO_LAUNCH_TYPE, LAUNCH_TYPE_SETTINGS);
        intent.putExtra(Constants.PIKO_FRAGMENT_NAME, fragment_name);
        intent.putExtra(Constants.PIKO_FRAGMENT_TITLE, title);
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

    public static void launchFragment(Context ctx, String bundleKey) {
        String launchType;
        if (bundleKey.equals("piko_export_dev_overrides") || bundleKey.equals("piko_export_pref")) {
            launchType = LAUNCH_TYPE_BACKUP;
        } else if (bundleKey.equals("piko_import_dev_overrides") || bundleKey.equals("piko_import_id_mapping") || bundleKey.equals("piko_import_pref")) {
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
