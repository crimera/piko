package app.revanced.extension.twitter.patches.customise.appIcon;

import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import app.revanced.extension.twitter.settings.Settings;
import app.revanced.extension.shared.Utils;
import java.util.ArrayList;
import java.util.List;
import app.revanced.extension.shared.StringRef;

@SuppressWarnings("deprecation")
public class IconSelectorFragment extends Fragment {

    private LinearLayout iconListContainer;
    private RadioButton lastChecked = null;
    IconOption prevSelectedIcon = null;

    private List<RadioButton> allRadioButtons = new ArrayList<>();
    private List<IconSection> iconSections;
    private SharedPreferences prefs;

    private static final String PREFS_NAME = Settings.SHARED_PREF_NAME;
    private static final String KEY_SELECTED_ICON = "selected_app_icon";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(Utils.getResourceIdentifier("fragment_icon_selector", "layout"), container, false);
        iconListContainer = root.findViewById(Utils.getResourceIdentifier("icon_list_container", "id"));
        prefs = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        iconSections = getIconSections();
        String currentIcon = prefs.getString(KEY_SELECTED_ICON, "");
        app.revanced.extension.twitter.Utils.logger("Previous: " +currentIcon);
        for (IconSection section : iconSections) {
            // Inflate section header
            View headerView = inflater.inflate(Utils.getResourceIdentifier("section_header", "layout"), iconListContainer, false);
            TextView headerText = headerView.findViewById(Utils.getResourceIdentifier("section_header_text", "id"));
            headerText.setText(section.header);
            iconListContainer.addView(headerView);

            List<IconOption> iconList = section.icons;

            // Inflate each icon item
            if ( iconList!= null) {
                for (final IconOption option : iconList) {
                    View item = inflater.inflate(Utils.getResourceIdentifier("icon_item", "layout"), iconListContainer, false);

                    ImageView iconImage = item.findViewById(Utils.getResourceIdentifier("icon_image", "id"));
                    TextView iconName = item.findViewById(Utils.getResourceIdentifier("icon_name", "id"));
                    RadioButton radioButton = item.findViewById(Utils.getResourceIdentifier("radio_button", "id"));

                    iconImage.setImageResource(option.iconResId);
                    iconName.setText(option.name);

                    // Keep reference to every radio button
                    allRadioButtons.add(radioButton);

                    if (option.componentName.equals(currentIcon)) {
                        prevSelectedIcon = option;
                        radioButton.setChecked(true);
                        lastChecked = radioButton;
                    }

                    // Handle click on entire row
                    item.setOnClickListener(v -> selectIcon(option, radioButton));

                    // Handle direct click on radio
                    radioButton.setOnClickListener(v -> selectIcon(option, radioButton));

                    iconListContainer.addView(item);
                }
            }
        }

        return root;
    }

    private void selectIcon(IconOption option, RadioButton clickedButton) {
        if (lastChecked != null) lastChecked.setChecked(false);
        clickedButton.setChecked(true);

        // Persist and change icon
        saveSelectedIcon(option.componentName);
        changeAppIcon(option.componentName);
    }

    private void saveSelectedIcon(String componentName) {
        app.revanced.extension.twitter.Utils.logger("Saved: "+componentName);
        prefs.edit().putString(KEY_SELECTED_ICON, componentName).apply();
    }

    private List<IconSection> getIconSections() {
        List<IconSection> sections = new ArrayList<>();
        sections.add(new IconSection(StringRef.str("piko_app_icon_thanks"), null));

        List<IconOption> legacy = new ArrayList<>();
        legacy.add(new IconOption(StringRef.str("piko_app_icon_name_default"), Utils.getResourceIdentifier("ic_launcher_twitter", "mipmap"), "com.twitter.android.StartActivity"));
        sections.add(new IconSection(StringRef.str("piko_app_icon_category_legacy"), legacy));

        List<IconOption> space = new ArrayList<>();
        space.add(new IconOption(StringRef.str("piko_app_icon_name_earth"), Utils.getResourceIdentifier("ic_app_icon_10", "mipmap"), "app.revanced.extension.twitter.appicon40"));
        space.add(new IconOption(StringRef.str("piko_app_icon_name_moon"), Utils.getResourceIdentifier("ic_app_icon_11", "mipmap"), "app.revanced.extension.twitter.appicon41"));
        space.add(new IconOption(StringRef.str("piko_app_icon_name_mars"), Utils.getResourceIdentifier("ic_app_icon_12", "mipmap"), "app.revanced.extension.twitter.appicon42"));
        space.add(new IconOption(StringRef.str("piko_app_icon_name_stars"), Utils.getResourceIdentifier("ic_app_icon_13", "mipmap"), "app.revanced.extension.twitter.appicon43"));
        sections.add(new IconSection(StringRef.str("piko_app_icon_category_space"), space));

        List<IconOption> seasonal_event = new ArrayList<>();
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_autumn_2021"), Utils.getResourceIdentifier("ic_seasonal_autumn_2021", "mipmap"), "app.revanced.extension.twitter.appicon0"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_autumn_2022"), Utils.getResourceIdentifier("ic_seasonal_autumn_2022", "mipmap"), "app.revanced.extension.twitter.appicon1"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_autumn_southern"), Utils.getResourceIdentifier("ic_seasonal_autumn_southern", "mipmap"), "app.revanced.extension.twitter.appicon48"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_south_spring_2021"), Utils.getResourceIdentifier("ic_seasonal_south_spring_2021", "mipmap"), "app.revanced.extension.twitter.appicon2"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_south_spring_2022"), Utils.getResourceIdentifier("ic_seasonal_south_spring_2022", "mipmap"), "app.revanced.extension.twitter.appicon3"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_winter"), Utils.getResourceIdentifier("ic_seasonal_winter", "mipmap"), "app.revanced.extension.twitter.appicon61"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_winter_1"), Utils.getResourceIdentifier("ic_seasonal_winter_1", "mipmap"), "app.revanced.extension.twitter.appicon4"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_winter_2"), Utils.getResourceIdentifier("ic_seasonal_winter_2", "mipmap"), "app.revanced.extension.twitter.appicon5"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_summer"), Utils.getResourceIdentifier("ic_seasonal_summer", "mipmap"), "app.revanced.extension.twitter.appicon60"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_summer_1"), Utils.getResourceIdentifier("ic_seasonal_summer_1", "mipmap"), "app.revanced.extension.twitter.appicon6"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_summer_2"), Utils.getResourceIdentifier("ic_seasonal_summer_2", "mipmap"), "app.revanced.extension.twitter.appicon7"));
        sections.add(new IconSection(StringRef.str("piko_app_icon_category_seasonal_event"), seasonal_event));

        List<IconOption> category_sports = new ArrayList<>();
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_beijing_olympics_1"), Utils.getResourceIdentifier("ic_seasonal_beijing_olympics_1", "mipmap"), "app.revanced.extension.twitter.appicon8"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_beijing_olympics_2"), Utils.getResourceIdentifier("ic_seasonal_beijing_olympics_2", "mipmap"), "app.revanced.extension.twitter.appicon9"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_daytona"), Utils.getResourceIdentifier("ic_seasonal_daytona", "mipmap"), "app.revanced.extension.twitter.appicon10"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_formulaone"), Utils.getResourceIdentifier("ic_seasonal_formulaone", "mipmap"), "app.revanced.extension.twitter.appicon11"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_kentucky_derby"), Utils.getResourceIdentifier("ic_seasonal_kentucky_derby", "mipmap"), "app.revanced.extension.twitter.appicon12"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_mlb"), Utils.getResourceIdentifier("ic_seasonal_mlb", "mipmap"), "app.revanced.extension.twitter.appicon13"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_nba"), Utils.getResourceIdentifier("ic_seasonal_nba", "mipmap"), "app.revanced.extension.twitter.appicon14"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_nba_2"), Utils.getResourceIdentifier("ic_seasonal_nba_2", "mipmap"), "app.revanced.extension.twitter.appicon15"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_nba_finals"), Utils.getResourceIdentifier("ic_seasonal_nba_finals", "mipmap"), "app.revanced.extension.twitter.appicon57"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_masters"), Utils.getResourceIdentifier("ic_seasonal_masters", "mipmap"), "app.revanced.extension.twitter.appicon17"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_commonwealth"), Utils.getResourceIdentifier("ic_seasonal_commonwealth", "mipmap"), "app.revanced.extension.twitter.appicon51"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_stanley_cup"), Utils.getResourceIdentifier("ic_seasonal_stanley_cup", "mipmap"), "app.revanced.extension.twitter.appicon59"));
        sections.add(new IconSection(StringRef.str("piko_app_icon_category_sports"), category_sports));

        List<IconOption> cultural_and_celebrations = new ArrayList<>();
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_mothers_day"), Utils.getResourceIdentifier("ic_seasonal_mothers_day", "mipmap"), "app.revanced.extension.twitter.appicon18"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_womansday"), Utils.getResourceIdentifier("ic_seasonal_womansday", "mipmap"), "app.revanced.extension.twitter.appicon19"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_earth_hour"), Utils.getResourceIdentifier("ic_seasonal_earth_hour", "mipmap"), "app.revanced.extension.twitter.appicon52"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_halloween_2021"), Utils.getResourceIdentifier("ic_seasonal_halloween_2021", "mipmap"), "app.revanced.extension.twitter.appicon20"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_halloween_2022"), Utils.getResourceIdentifier("ic_seasonal_halloween_2022", "mipmap"), "app.revanced.extension.twitter.appicon21"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_holi"), Utils.getResourceIdentifier("ic_seasonal_holi", "mipmap"), "app.revanced.extension.twitter.appicon22"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_stpatricks_day"), Utils.getResourceIdentifier("ic_seasonal_stpatricks_day", "mipmap"), "app.revanced.extension.twitter.appicon23"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_easter"), Utils.getResourceIdentifier("ic_seasonal_easter", "mipmap"), "app.revanced.extension.twitter.appicon24"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_anzac"), Utils.getResourceIdentifier("ic_seasonal_anzac", "mipmap"), "app.revanced.extension.twitter.appicon25"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_ramadan"), Utils.getResourceIdentifier("ic_seasonal_ramadan", "mipmap"), "app.revanced.extension.twitter.appicon26"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_black_history"), Utils.getResourceIdentifier("ic_seasonal_black_history", "mipmap"), "app.revanced.extension.twitter.appicon27"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_independence_day"), Utils.getResourceIdentifier("ic_seasonal_independence_day", "mipmap"), "app.revanced.extension.twitter.appicon54"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_canada_day"), Utils.getResourceIdentifier("ic_seasonal_canada_day", "mipmap"), "app.revanced.extension.twitter.appicon49"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_canada_indigenous"), Utils.getResourceIdentifier("ic_seasonal_canada_indigenous", "mipmap"), "app.revanced.extension.twitter.appicon50"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_euro"), Utils.getResourceIdentifier("ic_seasonal_euro", "mipmap"), "app.revanced.extension.twitter.appicon53"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_eurovisionfinal"), Utils.getResourceIdentifier("ic_seasonal_eurovisionfinal", "mipmap"), "app.revanced.extension.twitter.appicon28"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_lunar_new_year_1"), Utils.getResourceIdentifier("ic_seasonal_lunar_new_year_1", "mipmap"), "app.revanced.extension.twitter.appicon29"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_lunar_new_year_2"), Utils.getResourceIdentifier("ic_seasonal_lunar_new_year_2", "mipmap"), "app.revanced.extension.twitter.appicon30"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_may_the_fourth"), Utils.getResourceIdentifier("ic_seasonal_may_the_fourth", "mipmap"), "app.revanced.extension.twitter.appicon31"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_barbie"), Utils.getResourceIdentifier("ic_seasonal_barbie", "mipmap"), "app.revanced.extension.twitter.appicon32"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_thanksgiving_1"), Utils.getResourceIdentifier("ic_seasonal_thanksgiving_1", "mipmap"), "app.revanced.extension.twitter.appicon33"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_thanksgiving_2"), Utils.getResourceIdentifier("ic_seasonal_thanksgiving_2", "mipmap"), "app.revanced.extension.twitter.appicon34"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_juneteenth"), Utils.getResourceIdentifier("ic_seasonal_juneteenth", "mipmap"), "app.revanced.extension.twitter.appicon55"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_naidoc"), Utils.getResourceIdentifier("ic_seasonal_naidoc", "mipmap"), "app.revanced.extension.twitter.appicon56"));
        sections.add(new IconSection(StringRef.str("piko_app_icon_category_cultural_and_celebrations"), cultural_and_celebrations));

        List<IconOption> pride = new ArrayList<>();
        pride.add(new IconOption(StringRef.str("piko_app_icon_name_pridesouthern"), Utils.getResourceIdentifier("ic_seasonal_pridesouthern", "mipmap"), "app.revanced.extension.twitter.appicon35"));
        pride.add(new IconOption(StringRef.str("piko_app_icon_name_newzealand_pride_1"), Utils.getResourceIdentifier("ic_seasonal_newzealand_pride_1", "mipmap"), "app.revanced.extension.twitter.appicon36"));
        pride.add(new IconOption(StringRef.str("piko_app_icon_name_newzealand_pride_2"), Utils.getResourceIdentifier("ic_seasonal_newzealand_pride_2", "mipmap"), "app.revanced.extension.twitter.appicon37"));
        pride.add(new IconOption(StringRef.str("piko_app_icon_name_pride_month"), Utils.getResourceIdentifier("ic_seasonal_pride_month", "mipmap"), "app.revanced.extension.twitter.appicon58"));
        sections.add(new IconSection(StringRef.str("piko_app_icon_category_pride"), pride));

        List<IconOption> misc = new ArrayList<>();
        misc.add(new IconOption(StringRef.str("piko_app_icon_name_broken"), Utils.getResourceIdentifier("ic_app_icon_7", "mipmap"), "app.revanced.extension.twitter.appicon38"));
        misc.add(new IconOption(StringRef.str("piko_app_icon_name_fancy"), Utils.getResourceIdentifier("ic_app_icon_8", "mipmap"), "app.revanced.extension.twitter.appicon39"));
        misc.add(new IconOption(StringRef.str("piko_app_icon_name_cyber"), Utils.getResourceIdentifier("ic_app_icon_14", "mipmap"), "app.revanced.extension.twitter.appicon44"));
        sections.add(new IconSection(StringRef.str("piko_app_icon_category_misc"), misc));

        return sections;
    }

    private void killAllLauncherIcons(Context context){
        PackageManager pm = context.getPackageManager();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(context.getPackageName());

        List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);

        for (ResolveInfo info : infos) {
            String component = info.activityInfo.name;
            pm.setComponentEnabledSetting(
                    new ComponentName(context, component),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
            );

            app.revanced.extension.twitter.Utils.logger("Killed: " + component);
        }
    }

    private void changeAppIcon(String newComponentName) {
        Context ctx = Utils.getContext();
        if (ctx == null) return;
        PackageManager pm = ctx.getPackageManager();
        String pkg = ctx.getPackageName();

        String cmp = prevSelectedIcon.componentName;
        pm.setComponentEnabledSetting(
                new ComponentName(pkg,cmp),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
        app.revanced.extension.twitter.Utils.logger("Killed: "+cmp);

        String defCmp = "com.twitter.android.StartActivity";
        // If the icon is "default" remove all icons. This is a measure taken in case multiple icons are created.
        if (newComponentName.equals(defCmp)) {
            killAllLauncherIcons(ctx);
        }

        pm.setComponentEnabledSetting(
                new ComponentName(pkg, newComponentName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
        );
        app.revanced.extension.twitter.Utils.logger("Set: "+newComponentName);
    }
}
