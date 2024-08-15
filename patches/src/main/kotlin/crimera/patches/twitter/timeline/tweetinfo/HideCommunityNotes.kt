package crimera.patches.twitter.timeline.tweetinfo

import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

@Suppress("unused")
val hideCommunityNotes = bytecodePatch(
    name = "Hide Community Notes",
) {
    dependsOn(settingsPatch, tweetInfoHook)
    compatibleWith("com.twitter.android")

    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        settingsStatusMatch.enableSettings("hideCommunityNotes")
    }
}