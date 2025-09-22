package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.patch.bytecodePatch

val hideCTJ =
    bytecodePatch(
        name = "Remove \"Communities to join\" Banner",
    ) {
        execute {
            compatibleWith("com.twitter.android")
            dependsOn(timelineEntryHookPatch, settingsPatch)

            settingsStatusLoadFingerprint.enableSettings("hideCommToJoin")
        }
    }
