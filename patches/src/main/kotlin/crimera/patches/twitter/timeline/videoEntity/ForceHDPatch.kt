package crimera.patches.twitter.timeline.videoEntity

import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

@Suppress("unused")
val forceHDPatch =
    bytecodePatch(
        name = "Enable force HD videos",
        description = "Videos will be played in highest quality always",
    ) {
        dependsOn(settingsPatch)
        compatibleWith("com.twitter.android")
        execute {
            val settingsStatusMatch by settingsStatusLoadFingerprint()
            settingsStatusMatch.enableSettings("enableForceHD")
        }
    }
