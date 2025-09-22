package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.patch.bytecodePatch

val hidePremiumPrompt =
    bytecodePatch(
        name = "Remove message prompts Banner",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(timelineEntryHookPatch, settingsPatch)
        execute {

            settingsStatusLoadFingerprint.enableSettings("hidePremiumPrompt")
        }
    }
