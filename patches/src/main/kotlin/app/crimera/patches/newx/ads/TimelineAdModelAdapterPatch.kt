package app.crimera.patches.newx.ads

import app.crimera.patches.newx.models.patchBridge
import app.crimera.patches.newx.models.newXTimelineModelAdapterPatch
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"

private object TimelineRtbImageAdModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/timelines/items/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("UrtTimelineRtbImageAd(advertiserAvatarImageUrl=")),
)

internal val newXTimelineAdModelAdapterPatch =
    bytecodePatch(default = false) {
        dependsOn(newXTimelineModelAdapterPatch)

        execute {
            val adMatches = TimelineRtbImageAdModelFingerprint.scopedMatchAll()
            if (adMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX timeline RTB image-ad model, found ${adMatches.size}: " +
                        adMatches.joinToString { it.originalMethod.toString() },
                )
            }

            patchAdModelBridges(adMatches.single().classDef.type)
        }
    }

context(context: BytecodePatchContext)
private fun patchAdModelBridges(adDescriptor: String) {
    val filterClass = context.mutableClassDefBy(TIMELINE_FILTER_DESCRIPTOR)
    filterClass.patchBridge(
        "isTimelineRtbImageAd",
        OBJECT_DESCRIPTOR,
        "Z",
        "instance-of p0, p0, $adDescriptor\nreturn p0",
    )
}
