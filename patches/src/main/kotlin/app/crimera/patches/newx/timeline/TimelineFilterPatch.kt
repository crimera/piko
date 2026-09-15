package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.models.newXTimelineModelAdapterPatch
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/** Installs the shared single-pass timeline filter hook used by individual features. */
internal val newXTimelineFilterPatch =
    bytecodePatch(default = false) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXTimelineModelAdapterPatch)

        execute {
            val successConstructor =
                requireExactlyOne(
                    "NewX timeline success constructor",
                    NewXTimelineSuccessFingerprint.scopedMatchAll(),
                )
            successConstructor.method.addInstructions(
                0,
                """
                    invoke-static {p2}, $TIMELINE_FILTER_DESCRIPTOR->filterTimelineItems(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object p2
                """.trimIndent(),
            )
        }
    }
