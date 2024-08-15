package crimera.patches.twitter.timeline.tweetinfo

import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

@Suppress("unused")
val forceTranslate = bytecodePatch(
    name = "Force enable translate",
    description = "Get translate option for all posts",
) {
    dependsOn(settingsPatch, tweetInfoHook)
    compatibleWith("com.twitter.android")

    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        settingsStatusMatch.enableSettings("forceTranslate")
    }
}