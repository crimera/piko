package app.crimera.patches.xlite.ads

import app.crimera.patches.xlite.settings.injectBooleanRead
import app.crimera.patches.xlite.settings.toggleSetting
import app.crimera.patches.xlite.settings.xLiteSettingsContributionPatch
import app.crimera.patches.xlite.timeline.XLiteTimelineSuccessFingerprint
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.morphe.extension.xlite.api.XLiteSettings.Categories
import app.morphe.extension.xlite.api.XLiteSettings.Keys
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.findFreeRegister

private val filterPromotedPosts =
    toggleSetting(
        key = Keys.FILTER_PROMOTED_POSTS,
        titleResourceName = "piko_xlite_filter_promoted_posts_title",
        summaryResourceName = "piko_xlite_filter_promoted_posts_summary",
        order = 100,
        defaultValue = true,
    )

private val filterPromotedPostsSettingsPatch =
    xLiteSettingsContributionPatch {
        category(Categories.CONTENT) {
            add(filterPromotedPosts)
        }
    }

@Suppress("unused")
val xLiteHideAdsPatch =
    bytecodePatch(
        name = "X-Lite: Remove ads",
        description = "Filters promoted posts and modules from X-Lite timelines.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)
        dependsOn(filterPromotedPostsSettingsPatch)

        execute {
            val matches = XLiteTimelineSuccessFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite timeline success constructor, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            matches.single().method.apply {
                val settingRegister = findFreeRegister(0)
                val readInstructionCount =
                    filterPromotedPosts.injectBooleanRead(this, 0, settingRegister)
                addInstructions(
                    readInstructionCount,
                    """
                        invoke-static {p2, v$settingRegister}, $TIMELINE_FILTER_DESCRIPTOR->filterPromotedItems(Ljava/lang/Object;Z)Ljava/lang/Object;
                        move-result-object p2
                    """.trimIndent(),
                )
            }
        }
    }
