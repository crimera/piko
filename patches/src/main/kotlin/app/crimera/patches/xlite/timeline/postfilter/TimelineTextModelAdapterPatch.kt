package app.crimera.patches.xlite.timeline.postfilter

import app.crimera.patches.xlite.models.patchBridge
import app.crimera.patches.xlite.models.resolvedXLiteTimelineModels
import app.crimera.patches.xlite.models.smaliReference
import app.crimera.patches.xlite.models.xLiteTimelineModelAdapterPatch
import app.crimera.patches.xlite.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"

internal val xLiteTimelineTextModelAdapterPatch =
    bytecodePatch(default = false) {
        dependsOn(xLiteTimelineModelAdapterPatch)

        execute {
            val models = resolvedXLiteTimelineModels()
            patchPostTextBridge(
                postDescriptor = models.postDescriptor,
                textGetter = models.postTextGetter,
            )
        }
    }

context(context: BytecodePatchContext)
private fun patchPostTextBridge(
    postDescriptor: String,
    textGetter: com.android.tools.smali.dexlib2.iface.reference.MethodReference,
) {
    context.mutableClassDefBy(TIMELINE_FILTER_DESCRIPTOR).patchBridge(
                "getPostText",
                OBJECT_DESCRIPTOR,
                STRING_DESCRIPTOR,
                """
                    check-cast p0, $postDescriptor
                    invoke-virtual {p0}, ${textGetter.smaliReference()}
                    move-result-object p0
                    return-object p0
                """.trimIndent(),
            )
}
