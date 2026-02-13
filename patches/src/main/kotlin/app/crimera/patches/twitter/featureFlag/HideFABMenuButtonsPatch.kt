package app.crimera.patches.twitter.featureFlag

import app.crimera.patches.twitter.featureFlag.featureFlagPatch.featureFlagPatch
import app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints.featureFlagLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.crimera.utils.flagSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideFABMenuButtonsPatch =
    bytecodePatch(
        name = "Hide FAB Menu Buttons",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(featureFlagPatch, settingsPatch)
        execute {

            featureFlagLoadFingerprint.flagSettings("fabMenu")
            SettingsStatusLoadFingerprint.enableSettings("hideFABBtns")
        }
    }
