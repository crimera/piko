package app.crimera.patches.instagram.links.distractionFree

import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch


@Suppress("unused")
val disableExplorePatch = bytecodePatch(
    name = "Disable explore",
) {
    dependsOn(settingsPatch, interceptUriPatch)
    compatibleWith("com.instagram.android")

    execute {
        enableSettings("disableExplore")
    }
}