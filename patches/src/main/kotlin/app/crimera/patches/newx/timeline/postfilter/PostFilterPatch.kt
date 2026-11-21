package app.crimera.patches.newx.timeline.postfilter

import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.settings.customScreen
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.timeline.newXTimelineFilterPatch
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.patch.patch

@Suppress("unused")
val postFilterPatch =
    patch(
        name = "NewX: Filter posts by keyword",
        description = "Filters NewX posts using user-defined words and phrases.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)
        category("NewX patches")
        dependsOn(newXTimelineTextModelAdapterPatch, newXTimelineFilterPatch)

        newXSettings {
            category(Categories.CONTENT) {
                customScreen(
                    id = "newx.content.post_filtering",
                    strings = settingStrings("piko_newx_post_filtering"),
                    order = 400,
                    fragmentClassDescriptor =
                        "Lapp/morphe/extension/newx/postfilter/PostFilterFragment;",
                    iconResourceName = "ic_vector_filter",
                )
            }
        }
    }
