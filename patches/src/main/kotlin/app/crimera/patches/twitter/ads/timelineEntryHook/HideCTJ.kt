package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.patch.bytecodePatch

val hideCTJ =
    bytecodePatch(
        name = "Remove \"Communities to join\" Banner",
        use = true,
    ) {
        execute {
            compatibleWith("com.twitter.android")
            dependsOn(timelineEntryHookPatch)

            settingsStatusLoadFingerprint.enableSettings("hideCommToJoin")
        }
    }
