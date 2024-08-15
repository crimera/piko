package crimera.patches.twitter.ads.timelineEntryHook

import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

@Suppress("unused")
val hideSuperHeroEvent = bytecodePatch(
    name = "Remove superhero event",
    description = "Removes superhero event from Explore page",
) {
    dependsOn(settingsPatch, timelineEntryHookPatch)
    compatibleWith("com.twitter.android")

    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        settingsStatusMatch.enableSettings("hideSuperheroEvent")
    }
}