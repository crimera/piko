package app.crimera.patches.newx.ads

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.timeline.NewXTimelineSuccessFingerprint
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val newXHideAdsPatch =
    bytecodePatch(
        name = "NewX: Remove ads",
        description = "Filters promoted posts and modules from NewX timelines.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXTimelineAdModelAdapterPatch)

        val filterPromotedPosts =
            newXSettings {
                category(Categories.CONTENT) {
                    group(Groups.CONTENT_FILTERING) {
                        toggle(
                            id = "newx.content.filter_promoted_posts",
                            strings = settingStrings("piko_newx_filter_promoted_posts"),
                            order = 100,
                            defaultValue = true,
                        )
                    }
                }
            }

        execute {
            val matches = NewXTimelineSuccessFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX timeline success constructor, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            matches.single().method.apply {
                val read =
                    filterPromotedPosts.injectRead(
                        method = this,
                        index = 0,
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )
                addInstructions(
                    read.nextIndex,
                    """
                        invoke-static {p2, v${read.register}}, $TIMELINE_FILTER_DESCRIPTOR->filterPromotedItems(Ljava/lang/Object;Z)Ljava/lang/Object;
                        move-result-object p2
                    """.trimIndent(),
                )
            }
        }
    }
