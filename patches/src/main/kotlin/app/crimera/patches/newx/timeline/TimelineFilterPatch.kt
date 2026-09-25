package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.models.newXTimelineModelAdapterPatch
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.p0Register

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val FILTER_TIMELINE_ITEMS_DESCRIPTOR =
    "$TIMELINE_FILTER_DESCRIPTOR->filterTimelineItems($OBJECT_DESCRIPTOR)$OBJECT_DESCRIPTOR"

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
            val method = successConstructor.method
            // `p2` is the constructor's third argument, the `timelineItems` list it stores; the
            // hook rewrites that register in place so the field keeps the filtered value.
            val itemsRegister = method.p0Register + 2
            method.insertHook(
                index = 0,
                // The old plain insertion left every incoming label on the constructor head, so a
                // branch that reached it keeps skipping the hook, exactly as before.
                relocateBranchTargets = false,
            ) {
                invokeStatic(methodReference(FILTER_TIMELINE_ITEMS_DESCRIPTOR), itemsRegister)
                moveResult(itemsRegister, OBJECT_DESCRIPTOR)
            }
        }
    }
