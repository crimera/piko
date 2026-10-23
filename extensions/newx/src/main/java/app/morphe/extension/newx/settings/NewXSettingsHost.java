package app.morphe.extension.newx.settings;

import app.morphe.extension.crimera.settings.SettingsHost;
import app.morphe.extension.crimera.theme.PikoTheme;
import app.morphe.extension.newx.featureswitches.FeatureSwitchStore;
import app.morphe.extension.newx.postfilter.PostFilterRuleStore;
import app.morphe.extension.newx.timeline.ForYouTopicFilter;
import app.morphe.extension.newx.ui.NewXSettingsTheme;

/** Binds the shared settings system to NewX: its logger, resources, theme and built-in settings. */
public final class NewXSettingsHost {
    private static final String STRING_PREFIX = "piko_newx_";
    private static final String BACKUP_FILE_PREFIX = "piko_newx_settings_";

    private NewXSettingsHost() {
    }

    /**
     * Injection point: the patch calls this from the application init hook, before
     * {@code SettingsRegistry.load()}. Safe to call more than once.
     */
    public static void install() {
        PikoTheme.install(new NewXSettingsTheme());
        SettingsHost.install(SettingsHost.builder(NewXLogger.logger(), STRING_PREFIX)
                .backupFilePrefix(BACKUP_FILE_PREFIX)
                .icons("ic_vector_arrow_left", "ic_vector_search_stroke", "ic_vector_close")
                .beforeBackup(NewXSettingsHost::loadStoresForBackup)
                .contributor(BuiltInSettings::register)
                .build());
    }

    /** The backup exports whatever settings are loaded, so create the lazily built stores first. */
    private static void loadStoresForBackup() {
        FeatureSwitchStore.shared();
        ForYouTopicFilter.shared().enabled.get();
        PostFilterRuleStore.shared();
    }
}
