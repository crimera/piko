package app.revanced.extension.twitter.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.view.View;
import android.view.Window;
import androidx.appcompat.widget.Toolbar;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.twitter.settings.featureflags.FeatureFlagsFragment;
import app.revanced.extension.twitter.settings.fragments.*;
import app.revanced.extension.twitter.patches.nativeFeatures.readerMode.ReaderModeFragment;
import app.revanced.extension.twitter.patches.nativeFeatures.readerMode.ReaderModeUtils;
import static app.revanced.extension.shared.Utils.context;
@SuppressWarnings("deprecation")
public class ActivityHook {
    public static Toolbar toolbar;
    private static final String EXTRA_PIKO = "piko";
    private static final String EXTRA_PIKO_SETTINGS = EXTRA_PIKO + "_settings";

    public static boolean create(Activity act) {
        Intent intent = act.getIntent();
        if (!(intent.getBooleanExtra(EXTRA_PIKO, false))) return false;

        Bundle bundle = intent.getExtras();
        Window window = act.getWindow();

        if (Build.VERSION.SDK_INT >= 35) {
            int uiMode = act.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (uiMode == Configuration.UI_MODE_NIGHT_YES) {
                window.getDecorView().setSystemUiVisibility(window.getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            } else {
                window.getDecorView().setSystemUiVisibility(window.getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }

            window.getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
                int statusBarHeight = insets.getSystemWindowInsetTop();
                int navBarHeight = insets.getSystemWindowInsetBottom();

                window.getDecorView().setPadding(v.getPaddingLeft(), statusBarHeight, v.getPaddingRight(), navBarHeight);

                return insets.consumeSystemWindowInsets();
            });
        }

        Fragment fragment = null;
        boolean addToBackStack = false;
        String activity_name = bundle != null ? bundle.getString(Settings.ACT_NAME) : null;

        if (activity_name.equals(EXTRA_PIKO_SETTINGS)) {
            fragment = new SettingsFragment();
        } else if (activity_name .equals( Settings.FEATURE_FLAGS) ){
            fragment = new FeatureFlagsFragment();
        } else if (activity_name .equals( Settings.PATCH_INFO)) {
            fragment = new SettingsAboutFragment();
        }else if (activity_name .equals( Settings.READER_MODE_KEY) ){
            fragment = new ReaderModeFragment();
        }  else {
            fragment = new PageFragment();
        }

        if (fragment != null) {
            fragment.setArguments(bundle);
            startFragment(act,activity_name, fragment, addToBackStack);
            return true;
        }
        return false;
    }

    public static void startFragment(Activity act, String activity_name, Fragment fragment, boolean addToBackStack) {
        act.setContentView(Utils.getResourceIdentifier("preference_fragment_activity", "layout"));
        toolbar = act.findViewById(Utils.getResourceIdentifier("toolbar", "id"));
        toolbar.setNavigationIcon(Utils.getResourceIdentifier("ic_vector_arrow_left", "drawable"));
        toolbar.setTitle(getTitle(activity_name));
        toolbar.setNavigationOnClickListener(view -> act.onBackPressed());

        FragmentTransaction transaction = act.getFragmentManager().beginTransaction().replace(Utils.getResourceIdentifier("fragment_container", "id"), fragment);
        if (addToBackStack) {
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    private static String getTitle(String activity_name){
        String toolbarText = "piko_title_settings";
        if (activity_name.equals(Settings.PREMIUM_SECTION)) {
            toolbarText = "piko_title_premium";
        }else if (activity_name.equals(Settings.DOWNLOAD_SECTION)) {
            toolbarText = "piko_title_download";
        }else if (activity_name.equals(Settings.FLAGS_SECTION)) {
            toolbarText = "piko_title_feature_flags";
        }else if (activity_name.equals(Settings.ADS_SECTION)) {
            toolbarText = "piko_title_ads";
        }else if (activity_name.equals(Settings.MISC_SECTION)) {
            toolbarText = "piko_title_misc";
        }else if (activity_name.equals(Settings.CUSTOMISE_SECTION)) {
            toolbarText = "piko_title_customisation";
        }else if (activity_name.equals(Settings.FONT_SECTION)) {
            toolbarText = "piko_title_font";
        }else if (activity_name.equals(Settings.TIMELINE_SECTION)) {
            toolbarText = "piko_title_timeline";
        }else if (activity_name.equals(Settings.BACKUP_SECTION)) {
            toolbarText = "piko_title_backup";
        }else if (activity_name.equals(Settings.NATIVE_SECTION)) {
            toolbarText = "piko_title_native";
        }else if (activity_name.equals(Settings.LOGGING_SECTION)) {
            toolbarText = "piko_title_logging";
        }else if (activity_name.equals(Settings.READER_MODE_KEY)) {
            toolbarText = "piko_title_native_reader_mode";
        }
        return Utils.getResourceString(toolbarText);
    }

    public static void startActivity(String activity_name, Bundle bundle) throws Exception {
        Intent intent = new Intent(context, Class.forName("com.twitter.android.AuthorizeAppActivity"));
        bundle.putString(Settings.ACT_NAME, activity_name);
        bundle.putBoolean(EXTRA_PIKO, true);
        intent.putExtras(bundle);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void startActivity(String activity_name) throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(Settings.ACT_NAME, activity_name);
        bundle.putBoolean(EXTRA_PIKO, true);
        startActivity(activity_name, bundle);
    }

    public static void startReaderMode(String tweetId) throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(ReaderModeUtils.ARG_TWEET_ID, tweetId);
        startActivity(Settings.READER_MODE_KEY, bundle);
    }

    public static void startSettingsActivity() throws Exception {
        startActivity(EXTRA_PIKO_SETTINGS);
    }
}
