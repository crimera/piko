package app.crimera.patches.twitter.featureFlag

import app.crimera.patches.twitter.featureFlag.featureFlagPatch.featureFlagPatch
import app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints.featureFlagLoadFingerprint
import app.crimera.patches.twitter.misc.settings.*
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.crimera.utils.flagSettings
import app.revanced.patcher.patch.bytecodePatch

val hideBookmarkInTimelinePatch =
    bytecodePatch(
        name = "Hide bookmark icon in timeline",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(featureFlagPatch, settingsPatch)
        execute {

            featureFlagLoadFingerprint.method.flagSettings("bookmarkInTimeline")
            settingsStatusLoadFingerprint.method.enableSettings("hideInlineBmk")
        }
    }
