package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch


@Suppress("unused")
val disableAnalyticsPatch = bytecodePatch(
    name = "Disable analytics",
    description = "Block analytics that are sent to Instagram/Facebook servers.",
) {
    dependsOn(settingsPatch, interceptUriPatch)
    compatibleWith("com.instagram.android")

    execute {
        enableSettings("disableAnalytics")
    }
}