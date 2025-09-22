package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.patch.bytecodePatch

val hideVideosForYou =
    bytecodePatch(
        name = "Remove videos for you",
        description = "Removes \"videos for you\" from explore",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(timelineModuleItemHookPatch, settingsPatch)
        execute {
            settingsStatusLoadFingerprint.enableSettings("hideVideosForYou")
        }
    }
