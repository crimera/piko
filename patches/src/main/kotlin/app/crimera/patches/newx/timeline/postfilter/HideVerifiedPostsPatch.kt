package app.crimera.patches.newx.timeline.postfilter

import app.crimera.patches.newx.misc.postoptions.FILTERED_REPLIES_ACTION
import app.crimera.patches.newx.misc.postoptions.newXPostOption
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.customScreen
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.multiChoice
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.timeline.newXTimelineFilterPatch
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.patch.bytecodePatch

private const val FILTERED_REPLIES_HANDLER =
    "Lapp/morphe/extension/newx/misc/FilteredRepliesPostOptionHandler;"

@Suppress("unused")
val newXHideVerifiedPostsPatch =
    bytecodePatch(
        name = "NewX: Hide posts by verified account type",
        description = "Hides posts and replies authored by selected timeline-reported verification types.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXTimelineTextModelAdapterPatch, newXTimelineFilterPatch)

        val (filterTimeline, filterThread, verifiedTypesToHide) =
            newXSettings {
                category(Categories.CONTENT) {
                    group(Groups.VERIFIED_ACCOUNT_FILTERING) {
                        val filterTimeline =
                            toggle(
                                id = "newx.content.verified_account_filtering.timeline",
                                strings = settingStrings("piko_newx_verified_account_timeline_filter"),
                                order = 100,
                                defaultValue = false,
                            )
                        val filterThread =
                            toggle(
                                id = "newx.content.verified_account_filtering.thread",
                                strings = settingStrings("piko_newx_verified_account_thread_filter"),
                                order = 200,
                                defaultValue = false,
                            )
                        toggle(
                            id = "newx.content.verified_account_filtering.filtered_replies_menu",
                            strings = settingStrings("piko_newx_verified_account_filtered_replies_menu"),
                            order = 250,
                            defaultValue = true,
                        )
                        val verifiedTypesToHide =
                            multiChoice(
                                id = "newx.content.hide_verified_account_types",
                                strings = settingStrings("piko_newx_hide_verified_account_types"),
                                order = 300,
                                defaultValue = emptySet(),
                                options =
                                    listOf(
                                        choice("Business", "piko_newx_hide_verified_account_types_business"),
                                        choice("Government", "piko_newx_hide_verified_account_types_government"),
                                        choice("User", "piko_newx_hide_verified_account_types_user"),
                                        choice("Unknown", "piko_newx_hide_verified_account_types_unknown"),
                                    ),
                            )
                        customScreen(
                            id = "newx.content.verified_account_whitelist",
                            strings = settingStrings("piko_newx_verified_account_whitelist"),
                            order = 400,
                            fragmentClassDescriptor =
                                "Lapp/morphe/extension/newx/postfilter/VerifiedAccountWhitelistFragment;",
                        )
                        Triple(filterTimeline, filterThread, verifiedTypesToHide)
                    }
                }
            }

        newXPostOption(
            handlerDescriptor = FILTERED_REPLIES_HANDLER,
            actionName = FILTERED_REPLIES_ACTION,
            iconResourceName = "ic_vector_filter",
            order = 270,
        )
    }
