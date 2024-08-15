package crimera.patches.twitter.featureFlag

import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

// Credits to @iKirby
@Suppress("unused")
val removeViewCountPatch = bytecodePatch(
    name = "Remove view count",
    description = "Removes the view count from the bottom of tweets",
) {
    dependsOn(settingsPatch, featureFlagPatch)
    compatibleWith("com.twitter.android")

    val settingsStatusMatch by settingsStatusLoadFingerprint()
    val featureFlagsLoadMatch by featureFlagLoadFingerprint()

    execute {
        featureFlagsLoadMatch.enableFeatureFlag("viewCount")
        settingsStatusMatch.enableSettings("hideViewCount")
    }

}