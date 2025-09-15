package app.crimera.patches.twitter.featureFlag

import app.crimera.patches.twitter.featureFlag.fingerprints.featureFlagLoadFingerprint
import app.crimera.patches.twitter.misc.settings.*
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.crimera.utils.flagSettings
import app.revanced.patcher.patch.bytecodePatch

val hideFABMenuButtonsPatch =
    bytecodePatch(
        name = "Hide FAB Menu Buttons",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(featureFlagPatch, settingsPatch)
        execute {

            featureFlagLoadFingerprint.method.flagSettings("fabMenu")
            settingsStatusLoadFingerprint.method.enableSettings("hideFABBtns")
        }
    }
