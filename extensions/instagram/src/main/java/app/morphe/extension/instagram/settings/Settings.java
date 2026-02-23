package app.morphe.extension.instagram.settings;

import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.BooleanSetting;

public class Settings extends BaseSettings {
    public static final BooleanSetting DISABLE_ADS = new BooleanSetting("disable_ads", true);
    public static final BooleanSetting HIDE_SUGGESTED_CONTENT = new BooleanSetting("hide_suggested_content", true);
    public static final BooleanSetting DEVELOPER_OPTIONS = new BooleanSetting("enable_developer_options", true);
}