package app.crimera.patches.twitter.featureFlag

import app.crimera.patches.twitter.featureFlag.featureFlagPatch.featureFlagPatch
import app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints.featureFlagLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.crimera.utils.flagSettings
import app.morphe.patcher.patch.bytecodePatch

// Credits to @iKirby
@Suppress("unused")
val removeViewCountPatch =
    bytecodePatch(
        name = "Remove view count",
        description = "Removes the view count from the bottom of tweets",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(featureFlagPatch, settingsPatch)
        execute {

            featureFlagLoadFingerprint.flagSettings("viewCount")
            SettingsStatusLoadFingerprint.enableSettings("hideViewCount")
        }
    }
