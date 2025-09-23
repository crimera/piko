package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.patch.bytecodePatch

// TODO: Rename when migrating to the new patcher?"
@Suppress("unused")
val hideDetailedPosts =
    bytecodePatch(
        name = "Remove Detailed posts",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(timelineEntryHookPatch, settingsPatch)
        execute {
            settingsStatusLoadFingerprint.enableSettings("hideDetailedPost")
        }
    }
