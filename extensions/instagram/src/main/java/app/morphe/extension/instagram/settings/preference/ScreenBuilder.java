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
    public void ghostSection(){
        if (!(SettingsStatus.ghostSection())) return;

        PreferenceCategory category = category = addCategory(Strings.CATEGORY_GHOST);

        if (SettingsStatus.viewStoriesAnonymously) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.VIEW_STORIES_ANONYMOUSLY,
                            "",
                            Settings.VIEW_STORIES_ANONYMOUSLY
                    )
            );
        }
        if (SettingsStatus.viewLiveAnonymously) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.VIEW_LIVE_ANONYMOUSLY,
                            "",
                            Settings.VIEW_LIVE_ANONYMOUSLY
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
        if (SettingsStatus.sanitizeShareLinks) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.SANITIZE_SHARE_LINKS,
                            "",
                            Settings.SANITIZE_SHARE_LINKS
                    )
            );
        }
    }

    public void distractionFreeSection(){
        if (!(SettingsStatus.distractionFreeSection())) return;

        PreferenceCategory category = category = addCategory(Strings.CATEGORY_DISTRACTION_FREE);

        if (SettingsStatus.disableStories) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_STORIES,
                            "",
                            Settings.DISABLE_STORIES
                    )
            );
        }
        if (SettingsStatus.disableFeed) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_FEED,
                            "",
                            Settings.DISABLE_FEED
                    )
            );
        }
        if (SettingsStatus.disableReels) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_REELS,
                            "",
                            Settings.DISABLE_REELS
                    )
            );
        }
        if (SettingsStatus.disableExplore) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_EXPLORE,
                            "",
                            Settings.DISABLE_EXPLORE
                    )
            );
        }
        if (SettingsStatus.disableComments) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_COMMENTS,
                            "",
                            Settings.DISABLE_COMMENTS
                    )
            );
        }
    }

    public void buildMiscSection() {
        if (!(SettingsStatus.miscSection())) return;

        PreferenceCategory category = category = addCategory(Strings.CATEGORY_MISC);
        if (SettingsStatus.disableAnalytics) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_ANALYTICS,
                            Strings.DISABLE_ANALYTICS_DESC,
                            Settings.DISABLE_ANALYTICS
                    )
            );
        }
        if (SettingsStatus.disableDiscoverPeople) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_DISCOVER_PEOPLE,
                            Strings.DISABLE_DISCOVER_PEOPLE_DESC,
                            Settings.DISABLE_DISCOVER_PEOPLE
                    )
            );
        }
        if (SettingsStatus.followBackIndicator) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.FOLLOW_BACK_INDICATOR,
                            "",
                            Settings.FOLLOW_BACK_INDICATOR
                    )
            );
        }
    }
    //end
}
