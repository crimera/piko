package app.crimera.patches.xlite.ads

import app.crimera.patches.xlite.timeline.fieldForToStringLabel
import app.crimera.patches.xlite.timeline.makeFieldsPublic
import app.crimera.patches.xlite.timeline.patchBridge
import app.crimera.patches.xlite.timeline.patchObjectFieldGetter
import app.crimera.patches.xlite.timeline.resolveTimelinePostModelMatch
import app.crimera.patches.xlite.timeline.xLiteTimelineModelAdapterPatch
import app.crimera.patches.xlite.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
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

internal val xLiteTimelineAdModelAdapterPatch =
    bytecodePatch(default = false) {
        dependsOn(xLiteTimelineModelAdapterPatch)

        execute {
            val postMatch = resolveTimelinePostModelMatch()
            val adMatches = TimelineRtbImageAdModelFingerprint.scopedMatchAll()
            if (adMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite timeline RTB image-ad model, found ${adMatches.size}: " +
                        adMatches.joinToString { it.originalMethod.toString() },
                )
            }

            patchAdModelBridges(
                postMatch = postMatch,
                adDescriptor = adMatches.single().classDef.type,
            )
        }
    }

context(context: BytecodePatchContext)
private fun patchAdModelBridges(
    postMatch: app.morphe.patcher.Match,
    adDescriptor: String,
) {
    val postClass = postMatch.classDef
    val entryIdField = postMatch.fieldForToStringLabel(", entryId=")
    val clientEventInfoField = postMatch.fieldForToStringLabel(", clientEventInfo=")
    val promotedMetadataField = postMatch.fieldForToStringLabel(", promotedMetadata=")
    postClass.makeFieldsPublic(listOf(entryIdField, clientEventInfoField, promotedMetadataField))

    val filterClass = context.mutableClassDefBy(TIMELINE_FILTER_DESCRIPTOR)
    filterClass.patchBridge(
        "isTimelineRtbImageAd",
        OBJECT_DESCRIPTOR,
        "Z",
        "instance-of p0, p0, $adDescriptor\nreturn p0",
    )
    filterClass.patchObjectFieldGetter(
        "getPostEntryId",
        postClass.type,
        entryIdField,
        STRING_DESCRIPTOR,
    )
    filterClass.patchObjectFieldGetter(
        "getPostClientEventInfo",
        postClass.type,
        clientEventInfoField,
    )
    filterClass.patchObjectFieldGetter(
        "getPostPromotedMetadata",
        postClass.type,
        promotedMetadataField,
    )
}
