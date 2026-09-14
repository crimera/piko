package app.crimera.patches.newx.ads

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.timeline.newXTimelineFilterPatch
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val newXHideAdsPatch =
    bytecodePatch(
        name = "NewX: Remove ads",
        description = "Filters promoted posts and modules from NewX timelines.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXTimelineAdModelAdapterPatch, newXTimelineFilterPatch)

        newXSettings {
            category(Categories.CONTENT) {
                toggle(
                    id = "newx.content.filter_promoted_posts",
                    strings = settingStrings("piko_newx_filter_promoted_posts"),
                    order = 100,
                    defaultValue = true,
                )
            }
        }
    }
