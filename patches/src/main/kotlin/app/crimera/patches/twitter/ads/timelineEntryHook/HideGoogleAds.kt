package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints.featureFlagLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.crimera.utils.flagSettings
import app.revanced.patcher.patch.bytecodePatch

val hideGoogleAds =
    bytecodePatch(
        name = "Remove Google Ads",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(timelineEntryHookPatch, settingsPatch)
        execute {

            featureFlagLoadFingerprint.method.flagSettings("hideGoogleAds")
            settingsStatusLoadFingerprint.method.enableSettings("hideGAds")
        }
    }
