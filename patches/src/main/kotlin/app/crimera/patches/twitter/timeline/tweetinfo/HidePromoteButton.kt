package app.crimera.patches.twitter.timeline.tweetinfo

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hidePromoteButton =
    bytecodePatch(
        name = "Hide promote button",
        description = "Hides promote button under self posts",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch, tweetInfoHook)

        execute {
            SettingsStatusLoadFingerprint.enableSettings("hidePromoteButton")
        }
    }
