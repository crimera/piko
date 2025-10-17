package app.crimera.patches.twitter.misc.customize.font

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val customEmojiFont =
    bytecodePatch(
        name = "Custom emoji font",
        description = "Customise emoji font style",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(customFontHook, settingsPatch)

        execute {
            settingsStatusLoadFingerprint.enableSettings("customEmojiFont")
        }
    }
