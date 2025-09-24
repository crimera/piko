package app.crimera.patches.twitter.timeline.tweetinfo

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val forceTranslate =
    bytecodePatch(
        name = "Force enable translate",
        description = "Get translate option for all posts",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch, tweetInfoHook)

        execute {
            settingsStatusLoadFingerprint.enableSettings("forceTranslate")
        }
    }
