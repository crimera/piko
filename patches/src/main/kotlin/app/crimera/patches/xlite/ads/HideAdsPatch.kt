package app.crimera.patches.xlite.ads

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.Groups
import app.crimera.patches.xlite.settings.SettingReadRegisterConstraint
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.injectRead
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.toggle
import app.crimera.patches.xlite.settings.xLiteSettings
import app.crimera.patches.xlite.timeline.XLiteTimelineSuccessFingerprint
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val xLiteHideAdsPatch =
    bytecodePatch(
        name = "X-Lite: Remove ads",
        description = "Filters promoted posts and modules from X-Lite timelines.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)
        dependsOn(xLiteTimelineAdModelAdapterPatch)

        val filterPromotedPosts =
            xLiteSettings {
                category(Categories.CONTENT) {
                    group(Groups.CONTENT_FILTERING) {
                        toggle(
                            id = "xlite.content.filter_promoted_posts",
                            strings = settingStrings("piko_xlite_filter_promoted_posts"),
                            order = 100,
                            defaultValue = true,
                        )
                    }
                }
            }

        execute {
            val matches = XLiteTimelineSuccessFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite timeline success constructor, found ${matches.size}: " +
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
