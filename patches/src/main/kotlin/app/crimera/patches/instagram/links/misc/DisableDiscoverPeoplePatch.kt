package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch


@Suppress("unused")
val disableDiscoverPeoplePatch = bytecodePatch(
    name = "Disable discover people",
    description = "Disables discover people section on user profile",
) {
    dependsOn(settingsPatch, interceptUriPatch)
    compatibleWith("com.instagram.android")

    execute {
        enableSettings("disableDiscoverPeople")
    }
}