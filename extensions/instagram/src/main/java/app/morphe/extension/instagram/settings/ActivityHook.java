/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;
import android.app.Fragment;
import android.app.FragmentTransaction;

import app.morphe.extension.instagram.settings.SettingsFragment;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.preference.fragments.BackupPrefFragment;
import app.morphe.extension.instagram.settings.preference.fragments.RestorePrefFragment;

@SuppressWarnings("deprecation")
public class ActivityHook {

    private static void startFragment(Activity activity, Fragment fragm, boolean addToBackStack) {
        int fragmentId = View.generateViewId();
        FrameLayout fragment = new FrameLayout(activity);
        fragment.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        fragment.setId(fragmentId);

        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setFitsSystemWindows(true);
        linearLayout.setTransitionGroup(true);
        linearLayout.addView(fragment);
        linearLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        activity.setContentView(linearLayout);

        FragmentTransaction transaction = activity.getFragmentManager()
                .beginTransaction()
                .replace(fragmentId, fragm);
        if (addToBackStack) {
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    public static boolean hook(Activity activity) {
        Bundle bundle = activity.getIntent().getExtras();
        if(bundle.getBoolean(Strings.PIKO, false)){
            startFragment(activity,new SettingsFragment(),true);
            return true;
        }

        return false;
    }

    // --------------------------------
    private static void startActivity(String bundleKey) throws Exception {
        Bundle bundle = new Bundle();
        Context context = Utils.getContext();
        Intent intent = new Intent(context, Class.forName("com.instagram.mainactivity.LauncherActivity"));
        bundle.putBoolean(bundleKey, true);
        intent.putExtras(bundle);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void startPikoActivity() throws Exception {
        startActivity(Strings.PIKO);
    }

    public static void callFragment(Activity activity, String bundleKey){
        Fragment fragment = null;
        boolean addToBackStack = false;
        if (bundleKey.equals(Strings.EXPORT_DEV_OVERRIDES)) {
            fragment = new BackupPrefFragment();
            addToBackStack = true;
        } else if (bundleKey.equals(Strings.IMPORT_DEV_OVERRIDES)) {
            fragment = new RestorePrefFragment();
            addToBackStack = true;
        }
        if(fragment!=null){
            Bundle bundle = new Bundle();
            bundle.putBoolean(bundleKey, true);
            fragment.setArguments(bundle);
            startFragment(activity,fragment, addToBackStack);
        }

    }

}
