package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch


@Suppress("unused")
val viewStoriesAnonymouslyPatch = bytecodePatch(
    name = "View stories anonymously",
) {
    dependsOn(settingsPatch, interceptUriPatch)
    compatibleWith("com.instagram.android")

    execute {
        enableSettings("viewStoriesAnonymously")
    }
}