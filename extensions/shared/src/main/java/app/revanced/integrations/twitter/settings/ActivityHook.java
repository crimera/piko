package app.revanced.integrations.twitter.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.app.Fragment;
import android.app.FragmentTransaction;
import androidx.appcompat.widget.Toolbar;
import app.revanced.integrations.shared.Utils;
import android.app.Fragment;
import app.revanced.integrations.twitter.settings.featureflags.FeatureFlagsFragment;
import app.revanced.integrations.twitter.settings.fragments.*;

import static app.revanced.integrations.shared.Utils.context;

@SuppressWarnings("deprecation")
public class ActivityHook {
    public static Toolbar toolbar;
    private static final String EXTRA_PIKO = "piko";
    private static final String EXTRA_PIKO_SETTINGS = EXTRA_PIKO+"_settings";

    public static boolean create(Activity act) {
        Intent intent = act.getIntent();
        if (!(intent.getBooleanExtra(EXTRA_PIKO, false))) return false;


        Fragment fragment = null;
        boolean addToBackStack = false;

        if (intent.getBooleanExtra(EXTRA_PIKO_SETTINGS, false)) {
            fragment = new SettingsFragment();
        }else if (intent.getBooleanExtra(Settings.FEATURE_FLAGS.key, false)) {
            fragment = new FeatureFlagsFragment();
            fragment.setArguments(intent.getExtras());
        }else if (intent.getBooleanExtra(Settings.PATCH_INFO.key, false)) {
            fragment = new SettingsAboutFragment();
        }else{
            fragment = new PageFragment();
            fragment.setArguments(intent.getExtras());
        }

        if(fragment!=null) {
            startFragment(act,fragment,addToBackStack);
            return true;
        }
        return false;
    }

    public static void startFragment(Activity act, Fragment fragment,boolean addToBackStack){
        act.setContentView(Utils.getResourceIdentifier("preference_fragment_activity", "layout"));
        toolbar = act.findViewById(Utils.getResourceIdentifier("toolbar", "id"));
        toolbar.setNavigationIcon(Utils.getResourceIdentifier("ic_vector_arrow_left", "drawable"));
        toolbar.setTitle(Utils.getResourceString("piko_title_settings"));
        toolbar.setNavigationOnClickListener(view -> act.onBackPressed());

        FragmentTransaction transaction = act.getFragmentManager().beginTransaction().replace(Utils.getResourceIdentifier("fragment_container", "id"), fragment);
        if(addToBackStack){
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    public static void startActivity(String activity_name, Bundle bundle)throws Exception{
        Intent intent = new Intent(context, Class.forName("com.twitter.android.AuthorizeAppActivity"));
        bundle.putBoolean(activity_name, true);
        bundle.putBoolean(EXTRA_PIKO, true);
        intent.putExtras(bundle);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void startActivity(String activity_name)throws Exception{
        Bundle bundle = new Bundle();
        bundle.putBoolean(activity_name, true);
        bundle.putBoolean(EXTRA_PIKO, true);
        startActivity(activity_name,bundle);
    }

    public static void startSettingsActivity() throws Exception {
        startActivity(EXTRA_PIKO_SETTINGS);
    }
}
