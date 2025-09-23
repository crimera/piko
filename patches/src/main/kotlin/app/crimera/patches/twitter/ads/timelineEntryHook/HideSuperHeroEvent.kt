package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val hideSuperHeroEvent =
    bytecodePatch(
        name = "Remove superhero event",
        description = "Removes superhero event from Explore page",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(timelineEntryHookPatch, settingsPatch)
        execute {

            settingsStatusLoadFingerprint.enableSettings("hideSuperheroEvent")
        }
    }
