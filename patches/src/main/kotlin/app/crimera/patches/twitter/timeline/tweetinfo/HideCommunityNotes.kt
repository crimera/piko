package app.crimera.patches.twitter.timeline.tweetinfo

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideCommunityNotes =
    bytecodePatch(
        name = "Hide Community Notes",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch, tweetInfoHook)

        execute {
            SettingsStatusLoadFingerprint.enableSettings("hideCommunityNotes")
        }
    }
