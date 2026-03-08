package app.crimera.patches.instagram.utils

object Constants {
    const val INTEGRATIONS_PACKAGE = "Lapp/morphe/extension/instagram"
    const val ACTIVITY_SETTINGS_CLASS = "$INTEGRATIONS_PACKAGE/settings"
    const val ACTIVITY_HOOK_CLASS = "$ACTIVITY_SETTINGS_CLASS/ActivityHook;"
    const val UTILS_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/utils"

    const val PREF_DESCRIPTOR = "$UTILS_DESCRIPTOR/Pref;"
    const val PREF_CALL_DESCRIPTOR = "invoke-static {}, $PREF_DESCRIPTOR"

    const val PATCHES_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/patches"
    const val JSONPARSER_CHECK_DESCRIPTOR = """invoke-static {v%s}, $PATCHES_DESCRIPTOR/Block;->replaceJsonParserKey(Ljava/lang/String;)Ljava/lang/String;
        move-result-object v%s"""

    const val LINKS_DESCRIPTOR = "$PATCHES_DESCRIPTOR/Links;"
    const val DOWNLOAD_DESCRIPTOR = "$PATCHES_DESCRIPTOR/download"

    const val ACTIVITY_SETTINGS_STATUS_CLASS = "$ACTIVITY_SETTINGS_CLASS/SettingsStatus;"
    const val ENTITY_CLASS = "$INTEGRATIONS_PACKAGE/entity"
    const val SSTS_DESCRIPTOR = "invoke-static {}, $ACTIVITY_SETTINGS_STATUS_CLASS->%s()V"
}
