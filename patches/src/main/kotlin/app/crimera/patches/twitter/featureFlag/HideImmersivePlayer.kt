package app.crimera.patches.twitter.featureFlag

import app.crimera.patches.twitter.featureFlag.featureFlagPatch.featureFlagPatch
import app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints.featureFlagLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.crimera.utils.flagSettings
import app.revanced.patcher.patch.bytecodePatch

val hideImmersivePlayer =
    bytecodePatch(
        name = "Hide immersive player",
        description = "Removes swipe up for more videos in video player",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(featureFlagPatch, settingsPatch)
        execute {

            featureFlagLoadFingerprint.method.flagSettings("immersivePlayer")

            settingsStatusLoadFingerprint.method.enableSettings("hideImmersivePlayer")
        }
    }
