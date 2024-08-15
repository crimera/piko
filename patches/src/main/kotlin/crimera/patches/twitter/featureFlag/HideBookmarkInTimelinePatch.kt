package crimera.patches.twitter.featureFlag


import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

@Suppress("unused")
val hideBookmarkInTimelinePatch = bytecodePatch(
    name = "Hide bookmark icon in timeline",
) {
    dependsOn(settingsPatch, featureFlagPatch)
    compatibleWith("com.twitter.android")

    val settingsStatusMatch by settingsStatusLoadFingerprint()
    val featureFlagsLoadMatch by featureFlagLoadFingerprint()

    execute {
        featureFlagsLoadMatch.enableFeatureFlag("bookmarkInTimeline")
        settingsStatusMatch.enableSettings("hideInlineBmk")
    }

}