package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.patch.bytecodePatch

val hideRevistBmk =
    bytecodePatch(
        name = "Remove \"Revisit Bookmark\" Banner",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(timelineEntryHookPatch, settingsPatch)
        execute {

            settingsStatusLoadFingerprint.method.enableSettings("hideRevistBookmark")
        }
    }
