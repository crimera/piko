package app.crimera.patches.newx.timeline.postfilter

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.customScreen
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.timeline.NewXTimelineSuccessFingerprint
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val postFilterPatch =
    bytecodePatch(
        name = "NewX: Filter posts by keyword",
        description = "Filters NewX posts using user-defined words and phrases.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXTimelineTextModelAdapterPatch)

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

        execute {
            val matches = NewXTimelineSuccessFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX timeline success constructor, found ${matches.size}: " +
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
