package app.revanced.extension.twitter.patches.customise.appIcon;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import app.revanced.extension.twitter.settings.Settings;
import java.util.List;
import app.revanced.extension.shared.Utils;

public class AppIconManager {

    private static final String PREF_NAME = Settings.SHARED_PREF_NAME;
    private static final String KEY_SELECTED_ICON = "selected_app_icon";
    private static final String defComponentName = "com.twitter.android.StartActivity";

    private final Context context;
    private final PackageManager pm;
    private final SharedPreferences prefs;

    public AppIconManager(Context context) {
        this.context = Utils.getContext();
        this.pm = this.context.getPackageManager();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------------------------------------
    // Get & Save Selected Icon
    // ---------------------------------------------------------------------------------------------
    public void saveSelectedIcon(String componentName) {
        prefs.edit().putString(KEY_SELECTED_ICON, componentName).apply();
    }

    public String getSavedIcon() {
        return prefs.getString(KEY_SELECTED_ICON, "");
    }
    // ---------------------------------------------------------------------------------------------
    // Disable a launcher icon
    // ---------------------------------------------------------------------------------------------
    public void disableIcon(String componentName){
        pm.setComponentEnabledSetting(
                new ComponentName(context, componentName),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
        logger("Killed: "+componentName);
    }
    // ---------------------------------------------------------------------------------------------
    // Disable all launcher icons
    // ---------------------------------------------------------------------------------------------
    public void disableAllIcons() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(context.getPackageName());

        List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);

        for (ResolveInfo info : infos) {
            String component = info.activityInfo.name;
            disableIcon(component);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Enable ONE icon, disable all others
    // ---------------------------------------------------------------------------------------------
    public void applyIcon(String componentName) {
        String prevComponentName = getSavedIcon();
        saveSelectedIcon(componentName);
        // If the icon is "default" remove all icons. This is a measure taken in case multiple icons are created.
        if (componentName.equals(defComponentName)) {
            disableAllIcons();
        }
        else{
            disableIcon(prevComponentName);
        }
        pm.setComponentEnabledSetting(
                new ComponentName(context, componentName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
        );
        logger("Set: "+componentName);
    }
    private void logger(String msg){
        app.revanced.extension.twitter.Utils.logger(msg);
    }
}
