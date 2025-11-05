package app.revanced.extension.twitter.patches.customise.appIcon;

import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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

            // Inflate each icon item
            for (final IconOption option : section.icons) {
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

        return root;
    }

    private void selectIcon(IconOption option, RadioButton clickedButton) {
        // Uncheck all others
//        for (RadioButton rb : allRadioButtons) {
//            rb.setChecked(false);
//        }
        if (lastChecked != null) lastChecked.setChecked(false);
        clickedButton.setChecked(true);

        // Persist and change icon
        saveSelectedIcon(option.componentName);
        changeAppIcon(option.componentName);
    }

    private void saveSelectedIcon(String componentName) {
        prefs.edit().putString(KEY_SELECTED_ICON, componentName).apply();
    }

    private List<IconSection> getIconSections() {
        List<IconSection> sections = new ArrayList<>();
//        sections.add(new IconSection(StringRef.str("piko_app_icon_thanks"), null));

        List<IconOption> legacy = new ArrayList<>();
        legacy.add(new IconOption(StringRef.str("piko_app_icon_name_default"), Utils.getResourceIdentifier("ic_launcher_twitter", "mipmap"), "com.twitter.android.StartActivity"));
        sections.add(new IconSection(StringRef.str("piko_app_icon_category_legacy"), legacy));

        List<IconOption> seasonal_event = new ArrayList<>();
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_autumn_2021"), Utils.getResourceIdentifier("ic_seasonal_autumn_2021", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon100"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_autumn_2022"), Utils.getResourceIdentifier("ic_seasonal_autumn_2022", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon101"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_south_spring_2021"), Utils.getResourceIdentifier("ic_seasonal_south_spring_2021", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon102"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_south_spring_2022"), Utils.getResourceIdentifier("ic_seasonal_south_spring_2022", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon103"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_winter_1"), Utils.getResourceIdentifier("ic_seasonal_winter_1", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon104"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_winter_2"), Utils.getResourceIdentifier("ic_seasonal_winter_2", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon105"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_summer_1"), Utils.getResourceIdentifier("ic_seasonal_summer_1", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon106"));
        seasonal_event.add(new IconOption(StringRef.str("piko_app_icon_name_summer_2"), Utils.getResourceIdentifier("ic_seasonal_summer_2", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon107"));
        sections.add(new IconSection(StringRef.str("piko_app_icon_category_seasonal_event"), seasonal_event));

        List<IconOption> category_sports = new ArrayList<>();
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_beijing_olympics_1"), Utils.getResourceIdentifier("ic_seasonal_beijing_olympics_1", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon108"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_beijing_olympics_2"), Utils.getResourceIdentifier("ic_seasonal_beijing_olympics_2", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon109"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_daytona"), Utils.getResourceIdentifier("ic_seasonal_daytona", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon110"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_formulaone"), Utils.getResourceIdentifier("ic_seasonal_formulaone", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon111"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_kentucky_derby"), Utils.getResourceIdentifier("ic_seasonal_kentucky_derby", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon112"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_mlb"), Utils.getResourceIdentifier("ic_seasonal_mlb", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon113"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_nba"), Utils.getResourceIdentifier("ic_seasonal_nba", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon114"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_nba_2"), Utils.getResourceIdentifier("ic_seasonal_nba_2", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon115"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_ncaa"), Utils.getResourceIdentifier("ic_seasonal_ncaa", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon116"));
        category_sports.add(new IconOption(StringRef.str("piko_app_icon_name_masters"), Utils.getResourceIdentifier("ic_seasonal_masters", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon117"));
        sections.add(new IconSection(StringRef.str("piko_app_icon_category_sports"), category_sports));

        List<IconOption> cultural_and_celebrations = new ArrayList<>();
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_mothers_day"), Utils.getResourceIdentifier("ic_seasonal_mothers_day", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon118"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_womansday"), Utils.getResourceIdentifier("ic_seasonal_womansday", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon119"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_halloween_2021"), Utils.getResourceIdentifier("ic_seasonal_halloween_2021", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon120"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_halloween_2022"), Utils.getResourceIdentifier("ic_seasonal_halloween_2022", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon121"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_holi"), Utils.getResourceIdentifier("ic_seasonal_holi", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon122"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_stpatricks_day"), Utils.getResourceIdentifier("ic_seasonal_stpatricks_day", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon123"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_easter"), Utils.getResourceIdentifier("ic_seasonal_easter", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon124"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_anzac"), Utils.getResourceIdentifier("ic_seasonal_anzac", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon125"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_ramadan"), Utils.getResourceIdentifier("ic_seasonal_ramadan", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon126"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_black_history"), Utils.getResourceIdentifier("ic_seasonal_black_history", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon127"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_eurovisionfinal"), Utils.getResourceIdentifier("ic_seasonal_eurovisionfinal", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon128"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_lunar_new_year_1"), Utils.getResourceIdentifier("ic_seasonal_lunar_new_year_1", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon129"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_lunar_new_year_2"), Utils.getResourceIdentifier("ic_seasonal_lunar_new_year_2", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon130"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_may_the_fourth"), Utils.getResourceIdentifier("ic_seasonal_may_the_fourth", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon131"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_barbie"), Utils.getResourceIdentifier("ic_seasonal_barbie", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon132"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_thanksgiving_1"), Utils.getResourceIdentifier("ic_seasonal_thanksgiving_1", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon133"));
        cultural_and_celebrations.add(new IconOption(StringRef.str("piko_app_icon_name_thanksgiving_2"), Utils.getResourceIdentifier("ic_seasonal_thanksgiving_2", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon134"));
        sections.add(new IconSection(StringRef.str("piko_app_icon_category_cultural_and_celebrations"), cultural_and_celebrations));

        List<IconOption> pride = new ArrayList<>();
        pride.add(new IconOption(StringRef.str("piko_app_icon_name_pridesouthern"), Utils.getResourceIdentifier("ic_seasonal_pridesouthern", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon135"));
        pride.add(new IconOption(StringRef.str("piko_app_icon_name_newzealand_pride_1"), Utils.getResourceIdentifier("ic_seasonal_newzealand_pride_1", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon136"));
        pride.add(new IconOption(StringRef.str("piko_app_icon_name_newzealand_pride_2"), Utils.getResourceIdentifier("ic_seasonal_newzealand_pride_2", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon137"));
        sections.add(new IconSection(StringRef.str("piko_app_icon_category_pride"), pride));

        return sections;
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
        pm.setComponentEnabledSetting(
                new ComponentName(pkg,defCmp),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
        app.revanced.extension.twitter.Utils.logger("Killed: "+defCmp);


        // If the icon is "default" remove all icons. This is a measure taken in case multiple icons are created.
        if (newComponentName.equals(defCmp)) {
            for (IconSection section : iconSections) {
                for (IconOption option : section.icons) {
                    pm.setComponentEnabledSetting(
                            new ComponentName(pkg, option.componentName),
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                    );
                    app.revanced.extension.twitter.Utils.logger("Killed: " + option.componentName);
                }
            }
        }

        pm.setComponentEnabledSetting(
                new ComponentName(pkg, newComponentName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
        );
        app.revanced.extension.twitter.Utils.logger("Set: "+newComponentName);
    }
}
