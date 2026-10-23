package app.morphe.extension.newx.settings;

import app.morphe.extension.crimera.settings.SettingsRegistry;

final class BuiltInSettings {
    private static final String ADVANCED_CATEGORY_ID = "newx.advanced";
    private static final String BACKUP_RESTORE_GROUP_ID = "newx.advanced.backup_restore";
    private static final String DEVELOPER_TOOLS_GROUP_ID = "newx.advanced.debug_tools";

    private BuiltInSettings() {
    }

    static void register() {
        SettingsRegistry.registerCategory(
                ADVANCED_CATEGORY_ID,
                "piko_newx_category_advanced_title",
                "piko_newx_category_advanced_summary",
                "ic_vector_wrench",
                600
        );
        registerDeveloperTools();
        SettingsRegistry.registerGroup(
                ADVANCED_CATEGORY_ID,
                BACKUP_RESTORE_GROUP_ID,
                "piko_newx_backup_restore_title",
                "piko_newx_backup_restore_summary",
                "ic_vector_settings_stroke",
                200
        );
        registerAction(
                BACKUP_RESTORE_GROUP_ID,
                "newx.advanced.backup_restore.backup",
                "piko_newx_backup_title",
                "piko_newx_backup_summary",
                100,
                "Lapp/morphe/extension/crimera/settings/SettingsBackupRestore$BackupAction;"
        );
        registerAction(
                BACKUP_RESTORE_GROUP_ID,
                "newx.advanced.backup_restore.restore",
                "piko_newx_restore_title",
                "piko_newx_restore_summary",
                200,
                "Lapp/morphe/extension/crimera/settings/SettingsBackupRestore$RestoreAction;"
        );
    }

    /**
     * Registers the developer-tools group and the diagnostics toggle that gates
     * {@link NewXLogger}. Keeping the toggle native means the gate exists whenever the NewX
     * settings registry loads, instead of only when the optional browse-object patch is applied.
     */
    private static void registerDeveloperTools() {
        SettingsRegistry.registerGroup(
                ADVANCED_CATEGORY_ID,
                DEVELOPER_TOOLS_GROUP_ID,
                "piko_newx_group_debug_tools_title",
                "piko_newx_group_debug_tools_summary",
                "ic_vector_bug_stroke",
                300
        );
        SettingsRegistry.registerToggle(
                DEVELOPER_TOOLS_GROUP_ID,
                NewXLogger.LOGGING_SETTING_ID,
                "piko_newx_logging_title",
                "piko_newx_logging_summary",
                50
        );
        SettingsRegistry.configureToggle(NewXLogger.LOGGING_SETTING_ID, false, false);
    }

    private static void registerAction(
            String parentId,
            String id,
            String titleResourceName,
            String summaryResourceName,
            int order,
            String handlerClassDescriptor
    ) {
        SettingsRegistry.registerAction(
                parentId,
                id,
                titleResourceName,
                summaryResourceName,
                order
        );
        SettingsRegistry.configureAction(id, handlerClassDescriptor);
    }
}
