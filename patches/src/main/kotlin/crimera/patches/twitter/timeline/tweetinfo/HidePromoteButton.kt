package crimera.patches.twitter.timeline.tweetinfo

import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

@Suppress("unused")
val hidePromoteButton = bytecodePatch(
    name = "Hide promote button",
    description = "Hides promote button under self posts",
) {
    dependsOn(settingsPatch, tweetInfoHook)
    compatibleWith("com.twitter.android")

    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        settingsStatusMatch.enableSettings("hidePromoteButton")
    }
}