package crimera.patches.twitter.featureFlag

import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

@Suppress("unused")
val disableChirpFontPatch = bytecodePatch(
    name = "Disable chirp font",
) {
    dependsOn(settingsPatch, featureFlagPatch)
    compatibleWith("com.twitter.android")

    val settingsStatusMatch by settingsStatusLoadFingerprint()
    val featureFlagsLoadMatch by featureFlagLoadFingerprint()

    execute {
        featureFlagsLoadMatch.enableFeatureFlag("chirpFont")
        settingsStatusMatch.enableSettings("enableFont")
    }
}