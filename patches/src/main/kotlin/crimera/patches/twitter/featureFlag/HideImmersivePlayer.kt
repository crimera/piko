package crimera.patches.twitter.featureFlag

import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

@Suppress("unused")
val hideImmersivePlayer = bytecodePatch(
    name = "Hide immersive player",
    description = "Removes swipe up for more videos in video player",
) {
    dependsOn(settingsPatch, featureFlagPatch)
    compatibleWith("com.twitter.android")

    val settingsStatusMatch by settingsStatusLoadFingerprint()
    val featureFlagsLoadMatch by featureFlagLoadFingerprint()

    execute {
        featureFlagsLoadMatch.enableFeatureFlag("immersivePlayer")
        settingsStatusMatch.enableSettings("hideImmersivePlayer")
    }
}