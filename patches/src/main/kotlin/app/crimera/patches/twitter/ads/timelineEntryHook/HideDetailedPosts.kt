package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.patch.bytecodePatch

// TODO: Rename when migrating to the new patcher?"
val hideDetailedPosts =
    bytecodePatch(
        name = "Remove Detailed posts",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(timelineEntryHookPatch, settingsPatch)
        execute {
            settingsStatusLoadFingerprint.enableSettings("hideDetailedPost")
        }
    }
