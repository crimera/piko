package app.morphe.extension.instagram.settings.preference;


import android.content.Context;
import android.preference.PreferenceScreen;
import android.preference.Preference;
import android.preference.PreferenceCategory;

import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.preference.widgets.*;

public class ScreenBuilder {
    private final Context context;
    private final PreferenceScreen screen;
    private final Helper helper;

    public ScreenBuilder(Context context,PreferenceScreen screen,Helper helper){
        this.context = context;
        this.screen = screen;
        this.helper = helper;
    }

    private void addPreference(PreferenceCategory category,Preference pref){
        if(category!=null){
            category.addPreference(pref);
        }else {
            screen.addPreference(pref);
        }
    }

    private PreferenceCategory addCategory(String title) {
        PreferenceCategory preferenceCategory = new PreferenceCategory(context);
        preferenceCategory.setTitle(title);
        screen.addPreference(preferenceCategory);
        return preferenceCategory;
    }

    public void buildAdsSection(){
        if (!(SettingsStatus.adsSection())) return;

        PreferenceCategory category = category = addCategory(Strings.CATEGORY_ADS);
        if (SettingsStatus.disableAds) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_ADS,
                            "",
                            Settings.DISABLE_ADS
                    )
            );
        }
        if (SettingsStatus.hideSuggestedContent) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.HIDE_SUGEESTED_CONTENT,
                            Strings.HIDE_SUGEESTED_CONTENT_DESC,
                            Settings.HIDE_SUGGESTED_CONTENT
                    )
            );
        }
    }
    public void buildDeveloperSection(){
        if (!(SettingsStatus.developerOptionsSection())) return;

        PreferenceCategory category = category = addCategory(Strings.CATEGORY_DEV_OPTIONS);
        if (SettingsStatus.enableDeveloperOptions) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.ENABLE_DEV_OPTIONS,
                            "",
                            Settings.DEVELOPER_OPTIONS
                    )
            );
        }
        if (SettingsStatus.removeBuildExpirePopup) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.REMOVE_BUILD_EXPIRE_POPUP,
                            Strings.REMOVE_BUILD_EXPIRE_POPUP_DESC,
                            Settings.REMOVE_BUILD_EXPIRE_POPUP
                    )
            );
        }
    }

    public void linksSection(){
        if (!(SettingsStatus.linksSection())) return;

        PreferenceCategory category = category = addCategory(Strings.CATEGORY_LINKS);
        if (SettingsStatus.openLinksExternally) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.OPEN_LINKS_EXTERNALLY,
                            Strings.OPEN_LINKS_EXTERNALLY_DESC,
                            Settings.OPEN_LINKS_EXTERNALLY
                    )
            );
        }
    }
    //end
}
