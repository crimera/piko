package app.morphe.extension.newx.settings;

final class BuiltInSettings {
    private static final String ADVANCED_CATEGORY_ID = "newx.advanced";
    private static final String BACKUP_RESTORE_GROUP_ID = "newx.advanced.backup_restore";

    private BuiltInSettings() {
    }

    static void register() {
        SettingsRegistry.registerCategory(
                ADVANCED_CATEGORY_ID,
                "piko_newx_category_advanced_title",
                "piko_newx_category_advanced_summary",
                "ic_vector_toolbox_stroke",
                600
        );
        SettingsRegistry.registerGroup(
                ADVANCED_CATEGORY_ID,
                BACKUP_RESTORE_GROUP_ID,
                "piko_newx_backup_restore_title",
                "piko_newx_backup_restore_summary",
                "ic_vector_settings_stroke",
                200
        );
        registerAction(
                "newx.advanced.backup_restore.backup",
                "piko_newx_backup_title",
                "piko_newx_backup_summary",
                100,
                "Lapp/morphe/extension/newx/settings/SettingsBackupRestore$BackupAction;"
        );
        registerAction(
                "newx.advanced.backup_restore.restore",
                "piko_newx_restore_title",
                "piko_newx_restore_summary",
                200,
                "Lapp/morphe/extension/newx/settings/SettingsBackupRestore$RestoreAction;"
        );
    }

    private static void registerAction(
            String id,
            String titleResourceName,
            String summaryResourceName,
            int order,
            String handlerClassDescriptor
    ) {
        SettingsRegistry.registerAction(
                BACKUP_RESTORE_GROUP_ID,
                id,
                titleResourceName,
                summaryResourceName,
                order
        );
        SettingsRegistry.configureAction(id, handlerClassDescriptor);
    }
}
