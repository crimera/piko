package crimera.patches.twitter.ads.timelineEntryHook

import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

@Suppress("unused")
val hideVideosForYou = bytecodePatch(
    name = "Remove videos for you",
    description = "Removes \"videos for you\" from explore",
) {
    dependsOn(settingsPatch, timelineModuleItemHookPatch)
    compatibleWith("com.twitter.android")

    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        settingsStatusMatch.enableSettings("hideVideosForYou")
    }
}