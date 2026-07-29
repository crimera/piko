package app.morphe.extension.xlite.settings;

final class BuiltInSettings {
    private static final String CATEGORY_ID = "xlite.backup_restore";

    private BuiltInSettings() {
    }

    static void register() {
        SettingsRegistry.registerCategory(
                CATEGORY_ID,
                "piko_xlite_backup_restore_title",
                "piko_xlite_backup_restore_summary",
                "ic_vector_settings_stroke",
                500
        );
        registerAction(
                "xlite.backup_restore.backup",
                "piko_xlite_backup_title",
                "piko_xlite_backup_summary",
                100,
                "Lapp/morphe/extension/xlite/settings/SettingsBackupRestore$BackupAction;"
        );
        registerAction(
                "xlite.backup_restore.restore",
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
                CATEGORY_ID,
                id,
                titleResourceName,
                summaryResourceName,
                order
        );
        SettingsRegistry.configureAction(id, handlerClassDescriptor);
    }
}
