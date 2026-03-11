/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.utils

object Constants {
    const val INTEGRATIONS_PACKAGE = "Lapp/morphe/extension/twitter"
    const val UTILS_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/Utils"
    const val ACTIVITY_SETTINGS_CLASS = "$INTEGRATIONS_PACKAGE/settings"
    const val ACTIVITY_HOOK_CLASS = "$ACTIVITY_SETTINGS_CLASS/ActivityHook;"
    const val DEEPLINK_HOOK_CLASS = "$ACTIVITY_SETTINGS_CLASS/DeepLink;"
    const val ADD_PREF_DESCRIPTOR =
        "$UTILS_DESCRIPTOR;->addPref([Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/String;"

    const val PREF_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/Pref"
    const val PATCHES_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/patches"
    const val CUSTOMISE_DESCRIPTOR = "$PATCHES_DESCRIPTOR/customise/Customise"
    const val NATIVE_DESCRIPTOR = "$PATCHES_DESCRIPTOR/nativeFeatures"

    const val SSTS_DESCRIPTOR = "invoke-static {}, $ACTIVITY_SETTINGS_CLASS/SettingsStatus;"
    const val FSTS_DESCRIPTOR = "invoke-static {}, $INTEGRATIONS_PACKAGE/patches/FeatureSwitchPatch;"
}
