package app.crimera.patches.twitter.featureFlag

import app.crimera.patches.twitter.featureFlag.fingerprints.featureFlagLoadFingerprint
import app.crimera.patches.twitter.misc.settings.*
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.crimera.utils.flagSettings
import app.revanced.patcher.patch.bytecodePatch

// Credits to @iKirby
val removeViewCountPatch =
    bytecodePatch(
        name = "Remove view count",
        description = "Removes the view count from the bottom of tweets",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(featureFlagPatch, settingsPatch)
        execute {

            featureFlagLoadFingerprint.method.flagSettings("viewCount")
            settingsStatusLoadFingerprint.method.enableSettings("hideViewCount")
        }
    }
