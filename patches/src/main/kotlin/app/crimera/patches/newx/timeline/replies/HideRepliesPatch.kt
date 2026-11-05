package app.crimera.patches.newx.timeline.replies

import app.crimera.patches.newx.misc.postoptions.FILTERED_REPLIES_ACTION
import app.crimera.patches.newx.misc.postoptions.newXPostOption
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.timeline.newXTimelineFilterPatch
import app.crimera.patches.newx.timeline.postfilter.newXTimelineTextModelAdapterPatch
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.settings.group
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.settings.toggle
import app.crimera.patches.newx.settings.newXSettings
import app.morphe.patcher.patch.bytecodePatch

private const val FILTERED_REPLIES_HANDLER =
    "Lapp/morphe/extension/newx/misc/FilteredRepliesPostOptionHandler;"

@Suppress("unused")
val newXHideRepliesPatch =
    bytecodePatch(
        name = "NewX: Hide replies",
        description = "Hides replies from timelines and conversation threads while keeping " +
            "root posts, quote posts, and the author's own thread chains.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXTimelineTextModelAdapterPatch, newXTimelineFilterPatch)

        newXSettings {
            category(Categories.CONTENT) {
                group(Groups.REPLIES) {
                    toggle(
                        id = "newx.content.hide_replies.timeline",
                        strings = settingStrings("piko_newx_hide_replies_timeline"),
                        order = 100,
                        defaultValue = false,
                    )
                    toggle(
                        id = "newx.content.hide_replies.thread",
                        strings = settingStrings("piko_newx_hide_replies_thread"),
                        order = 200,
                        defaultValue = false,
                    )
                }
            }
        }

        // Shares the Filtered Replies menu with the verified-account filter; the contribution is
        // deduplicated by handler descriptor when both patches are selected.
        newXPostOption(
            handlerDescriptor = FILTERED_REPLIES_HANDLER,
            actionName = FILTERED_REPLIES_ACTION,
            iconResourceName = "ic_vector_filter",
            order = 270,
        )
    }
