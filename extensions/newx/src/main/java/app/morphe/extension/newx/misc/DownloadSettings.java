package app.morphe.extension.newx.misc;

import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.settings.StringSetting;

/**
 * Registry access for the download options.
 *
 * <p>Every persisted download value is a NewX settings node rather than a raw preference write, so
 * the settings backup carries chosen folders and the filename template between installs.
 */
final class DownloadSettings {
    static final String IMAGES_TREE_URI = "newx.content.inline_download.images_tree_uri";
    static final String VIDEOS_TREE_URI = "newx.content.inline_download.videos_tree_uri";
    static final String IMAGES_DISPLAY_PATH = "newx.content.inline_download.images_display_path";
    static final String VIDEOS_DISPLAY_PATH = "newx.content.inline_download.videos_display_path";
    static final String FILENAME_TEMPLATE = "newx.content.inline_download.filename_template";
    static final String CONFLICT_POLICY = "newx.content.inline_download_conflict";

    static final String CONFLICT_OVERWRITE = "overwrite";
    static final String CONFLICT_RENAME = "rename";
    static final String CONFLICT_SKIP = "skip";

    private DownloadSettings() {
    }

    /**
     * Downloading can run before the settings screen is ever opened, so the registry is forced
     * here instead of assuming the settings activity initialized it.
     */
    private static void ensureLoaded() {
        if (SettingsRegistry.isLoaded()) return;
        SettingsRegistry.load();
    }

    static String imagesTreeUri() {
        ensureLoaded();
        return SettingsRegistry.getStringOrDefault(IMAGES_TREE_URI, "");
    }

    static String videosTreeUri() {
        ensureLoaded();
        return SettingsRegistry.getStringOrDefault(VIDEOS_TREE_URI, "");
    }

    static String imagesDisplayPath() {
        ensureLoaded();
        return SettingsRegistry.getStringOrDefault(IMAGES_DISPLAY_PATH, "");
    }

    static String videosDisplayPath() {
        ensureLoaded();
        return SettingsRegistry.getStringOrDefault(VIDEOS_DISPLAY_PATH, "");
    }

    static String filenameTemplate() {
        ensureLoaded();
        return SettingsRegistry.getStringOrDefault(FILENAME_TEMPLATE, DownloadFileName.DEFAULT_TEMPLATE);
    }

    static String conflictPolicy() {
        ensureLoaded();
        return SettingsRegistry.getStringOrDefault(CONFLICT_POLICY, CONFLICT_SKIP);
    }

    static void setString(String settingId, String value) {
        ensureLoaded();
        Setting<?> setting = SettingsRegistry.settingOrNull(settingId);
        if (!(setting instanceof StringSetting text)) {
            throw new IllegalStateException("Missing NewX download setting " + settingId);
        }
        text.save(value);
    }
}
