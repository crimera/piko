package crimera.patches.twitter.ads.timelineEntryHook

import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

@Suppress("unused")
val HideTodaysNews =
    bytecodePatch(
        name = "Remove \"Today's News\"",
    ) {
        dependsOn(settingsPatch, timelineEntryHookPatch)
        compatibleWith("com.twitter.android")

        val settingsStatusMatch by settingsStatusLoadFingerprint()

        execute {
            settingsStatusMatch.enableSettings("hideTodaysNews")
        }
    }
