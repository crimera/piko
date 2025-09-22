package app.crimera.patches.twitter.featureFlag

import app.crimera.patches.twitter.featureFlag.featureFlagPatch.featureFlagPatch
import app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints.featureFlagLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.crimera.utils.flagSettings
import app.revanced.patcher.patch.bytecodePatch

val disableChirpFontPatch =
    bytecodePatch(
        name = "Disable chirp font",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(featureFlagPatch, settingsPatch)
        execute {

            featureFlagLoadFingerprint.flagSettings("chirpFont")
            settingsStatusLoadFingerprint.enableSettings("enableFont")
        }
    }
