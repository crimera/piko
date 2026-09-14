package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.models.newXTimelineModelAdapterPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val newXHideWhoToFollowPatch =
    bytecodePatch(
        name = "NewX: Hide who to follow",
        description = "Hides recommended-user sections (\"Who to follow\") from NewX timelines and profile pages.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXTimelineModelAdapterPatch, newXTimelineFilterPatch)

        newXSettings {
            category(Categories.CONTENT) {
                toggle(
                    id = "newx.content.hide_who_to_follow",
                    strings = settingStrings("piko_newx_hide_who_to_follow"),
                    order = 200,
                    defaultValue = false,
                )
            }
        }
    }
