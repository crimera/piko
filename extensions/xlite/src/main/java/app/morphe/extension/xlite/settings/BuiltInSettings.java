package app.morphe.extension.xlite.settings;

final class BuiltInSettings {
    private static final String ADVANCED_CATEGORY_ID = "xlite.advanced";
    private static final String BACKUP_RESTORE_GROUP_ID = "xlite.advanced.backup_restore";

    private BuiltInSettings() {
    }

    static void register() {
        SettingsRegistry.registerCategory(
                ADVANCED_CATEGORY_ID,
                "piko_xlite_category_advanced_title",
                "piko_xlite_category_advanced_summary",
                "ic_vector_toolbox_stroke",
                500
        );
        SettingsRegistry.registerGroup(
                ADVANCED_CATEGORY_ID,
                BACKUP_RESTORE_GROUP_ID,
                "piko_xlite_backup_restore_title",
                "piko_xlite_backup_restore_summary",
                "ic_vector_settings_stroke",
                200
        );
        registerAction(
                "xlite.advanced.backup_restore.backup",
                "piko_xlite_backup_title",
                "piko_xlite_backup_summary",
                100,
                "Lapp/morphe/extension/xlite/settings/SettingsBackupRestore$BackupAction;"
        );
        registerAction(
                "xlite.advanced.backup_restore.restore",
                "piko_xlite_restore_title",
                "piko_xlite_restore_summary",
                200,
                "Lapp/morphe/extension/xlite/settings/SettingsBackupRestore$RestoreAction;"
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
