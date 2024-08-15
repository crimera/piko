package app.revanced.integrations.twitter.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.widget.Toolbar;
import app.revanced.integrations.shared.Utils;

import static app.revanced.integrations.shared.Utils.context;

@SuppressWarnings("deprecation")
public class ActivityHook {
    public static Toolbar toolbar;
    private static final String EXTRA_PIKO_SETTINGS = "piko_settings";

    public static boolean create(Activity act) {
        if (!act.getIntent().getBooleanExtra(EXTRA_PIKO_SETTINGS, false)) {
            return false;
        }

        Log.d("BRUH", "Activity requested");

        act.setContentView(Utils.getResourceIdentifier("preference_fragment_activity", "layout"));
        toolbar = act.findViewById(Utils.getResourceIdentifier("toolbar", "id"));
        toolbar.setNavigationIcon(Utils.getResourceIdentifier("ic_vector_arrow_left", "drawable"));
        toolbar.setTitle(Utils.getResourceString("piko_title_settings"));
        toolbar.setNavigationOnClickListener(view -> act.onBackPressed());

        act.getFragmentManager().beginTransaction().replace(Utils.getResourceIdentifier("fragment_container", "id"), new SettingsFragment()).commit();

        return true;
    }

    public static void startSettingsActivity() throws Exception {
        Intent intent = new Intent(context, Class.forName("com.twitter.android.AuthorizeAppActivity"));
        Bundle bundle = new Bundle();
        bundle.putBoolean(EXTRA_PIKO_SETTINGS, true);
        intent.putExtras(bundle);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
