package app.crimera.patches.twitter.misc.customize.font

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val customFont =
    bytecodePatch(
        name = "Custom font",
        description = "Customise font style",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(customFontHook, settingsPatch)

        execute {
            settingsStatusLoadFingerprint.enableSettings("customFont")
        }
    }
