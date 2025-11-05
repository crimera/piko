package app.crimera.patches.twitter.misc.appicon

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val appIconPatch =
    bytecodePatch(
        name = "Change app icon",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch, appIconResourcePatch)
        execute {
            settingsStatusLoadFingerprint.enableSettings("appIconCustomisation")
        }
    }
