package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.models.newXTimelineModelAdapterPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val newXHideDiscoverMorePatch =
    bytecodePatch(
        name = "NewX: Hide Discover more",
        description = "Removes the Discover more module from post-detail timelines.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXTimelineModelAdapterPatch, newXTimelineFilterPatch)

        newXSettings {
            category(Categories.CONTENT) {
                toggle(
                    id = "newx.content.hide_discover_more",
                    strings = settingStrings("piko_newx_hide_discover_more"),
                    order = 250,
                    defaultValue = false,
                )
            }
        }
    }
