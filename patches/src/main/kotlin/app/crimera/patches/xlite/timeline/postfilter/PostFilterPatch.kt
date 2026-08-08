package app.crimera.patches.xlite.timeline.postfilter

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.Groups
import app.crimera.patches.xlite.settings.customScreen
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteSettings
import app.crimera.patches.xlite.timeline.XLiteTimelineSuccessFingerprint
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val postFilterPatch =
    bytecodePatch(
        name = "X-Lite: Filter posts by keyword",
        description = "Filters X-Lite posts using user-defined words and phrases.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        xLiteSettings {
            category(Categories.CONTENT) {
                group(Groups.CONTENT_FILTERING) {
                    customScreen(
                        id = "xlite.content.post_filtering",
                        strings = settingStrings("piko_xlite_post_filtering"),
                        order = 400,
                        fragmentClassDescriptor =
                            "Lapp/morphe/extension/xlite/postfilter/PostFilterFragment;",
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

            matches.single().method.addInstructions(
                0,
                """
                    invoke-static {p2}, $TIMELINE_FILTER_DESCRIPTOR->filterPostsByKeyword(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object p2
                """.trimIndent(),
            )
        }
    }
