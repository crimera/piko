package app.crimera.patches.twitter.misc.selectabletext

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val selectableTextPatch =
    bytecodePatch(
        name = "Selectable Text",
        description = "Makes bio and username selectable",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch, selectableTextResources)

        execute {
            settingsStatusLoadFingerprint.enableSettings("selectableText")
        }
    }
