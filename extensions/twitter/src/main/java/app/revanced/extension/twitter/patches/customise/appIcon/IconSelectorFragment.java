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
      //  View root = inflater.inflate(R.layout.fragment_icon_selector, container, false);
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


//                radioButton.setOnClickListener(v -> {
//                  //  if (lastChecked != null) lastChecked.setChecked(false);
//                  //  radioButton.setChecked(true);
//                  //  lastChecked = radioButton;
//                    prevSelectedIcon = option;
//                    changeAppIcon(option.componentName);
//                    saveSelectedIcon(option.componentName);
//                });

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

        List<IconOption> section1 = new ArrayList<>();
        section1.add(new IconOption("Retro 2004", Utils.getResourceIdentifier("ic_app_icon_10", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon10"));
        section1.add(new IconOption("Pixelated", Utils.getResourceIdentifier("ic_app_icon_11", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon11"));
        section1.add(new IconOption("Anzac", Utils.getResourceIdentifier("ic_seasonal_anzac", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon100"));
        sections.add(new IconSection("Classic", section1));

        List<IconOption> section2 = new ArrayList<>();
        section2.add(new IconOption("Cuddling", Utils.getResourceIdentifier("ic_app_icon_12", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon12"));
        section2.add(new IconOption("Pride", Utils.getResourceIdentifier("ic_app_icon_13", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon13"));
        section1.add(new IconOption("Autumn 2021", Utils.getResourceIdentifier("ic_seasonal_autumn_2021", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon101"));
        sections.add(new IconSection("Modern", section2));

        List<IconOption> section3 = new ArrayList<>();
        section3.add(new IconOption("Flaming", Utils.getResourceIdentifier("ic_app_icon_25", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon25"));
        section3.add(new IconOption("Minimal", Utils.getResourceIdentifier("ic_app_icon_26", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon26"));
        section1.add(new IconOption("Autumn 2022", Utils.getResourceIdentifier("ic_seasonal_autumn_2022", "mipmap"), "com.twitter.subscriptions.appicon.implementation.icon102"));
        sections.add(new IconSection("Other", section3));

        return sections;
    }

    private void changeAppIcon(String newComponentName) {
        Context ctx = Utils.getContext();
        if (ctx == null) return;
        PackageManager pm = ctx.getPackageManager();
        String pkg = ctx.getPackageName();

//        for (IconSection section : iconSections) {
//            for (IconOption option : section.icons) {
//                pm.setComponentEnabledSetting(
//                        new ComponentName(pkg, option.componentName),
//                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
//                        PackageManager.DONT_KILL_APP
//                );
//                app.revanced.extension.twitter.Utils.logger("Killed: "+option.componentName);
//            }
//        }

        String cmp =  "com.twitter.android.StartActivity";
        pm.setComponentEnabledSetting(
                new ComponentName(pkg,cmp),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
        app.revanced.extension.twitter.Utils.logger("Killed: "+cmp);

        cmp = prevSelectedIcon.componentName;
        pm.setComponentEnabledSetting(
                new ComponentName(pkg,cmp),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
        app.revanced.extension.twitter.Utils.logger("Killed: "+cmp);

        pm.setComponentEnabledSetting(
                new ComponentName(pkg, newComponentName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
        );
        app.revanced.extension.twitter.Utils.logger("Set: "+newComponentName);
    }
}
