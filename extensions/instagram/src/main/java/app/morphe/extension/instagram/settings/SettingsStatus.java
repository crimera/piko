package app.morphe.extension.instagram.settings;

public class SettingsStatus {
    public static boolean enableDeveloperOptions = false;
    public static void enableDeveloperOptions() {
        enableDeveloperOptions = true;
    }


    public static boolean developerOptionsSection() {
        return enableDeveloperOptions;
    }

    public static void load() {
    }
}