package app.crimera.patches.newx.ads

import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.models.newXTimelineModelAdapterPatch
import app.crimera.patches.newx.models.patchBridge
import app.crimera.patches.newx.models.patchObjectAccessorGetter
import app.crimera.patches.newx.models.readModelAccessor
import app.crimera.patches.newx.models.requireBridge
import app.crimera.patches.newx.models.requireSingle
import app.crimera.patches.newx.models.resolveFieldAccessor
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.crimera.bytecode.Target
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.p0Register

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"

/** Label the promoted-metadata bodies jump to when the nested model is absent. */
private const val NULL_RESULT_LABEL = "cond_null"

private object TimelineRtbImageAdModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/timelines/items/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("UrtTimelineRtbImageAd(advertiserAvatarImageUrl=")),
)

private object UrtTimelineTrendModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/timelines/items/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("UrtTimelineTrend(timelineTrend=")),
)

private object TimelineTrendModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("TimelineTrend(thumbnailImageUrl="), string(", promotedMetadata=")),
)

private object UrtTimelineEventSummaryModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/timelines/items/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("UrtTimelineEventSummary(eventSummary=")),
)

private object EventSummaryModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("EventSummary(title="), string(", promotedMetadata=")),
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
            patchExploreAdModelBridges()
        }
    }

context(context: BytecodePatchContext)
private fun patchAdModelBridges(adDescriptor: String) {
    val filterClass = context.mutableClassDefBy(TIMELINE_FILTER_DESCRIPTOR)
    val isTimelineRtbImageAd =
        filterClass.requireBridge("isTimelineRtbImageAd", OBJECT_DESCRIPTOR, "Z")
    isTimelineRtbImageAd.patchBridge {
        val receiver = isTimelineRtbImageAd.p0Register
        instanceOf(receiver, receiver, adDescriptor)
        returnValue(receiver)
    }
}

context(context: BytecodePatchContext)
private fun patchExploreAdModelBridges() {
    val urtTrendMatch = UrtTimelineTrendModelFingerprint.requireSingle("URT trend item model")
    val trendMatch = TimelineTrendModelFingerprint.requireSingle("trend model")
    val urtEventMatch = UrtTimelineEventSummaryModelFingerprint.requireSingle("URT event summary item model")
    val eventMatch = EventSummaryModelFingerprint.requireSingle("event summary model")

    val urtTrendClass = context.mutableClassDefBy(urtTrendMatch.originalClassDef.type)
    val trendClass = context.mutableClassDefBy(trendMatch.originalClassDef.type)
    val urtEventClass = context.mutableClassDefBy(urtEventMatch.originalClassDef.type)
    val eventClass = context.mutableClassDefBy(eventMatch.originalClassDef.type)

    val timelineTrendField = urtTrendMatch.fieldForToStringLabel("UrtTimelineTrend(timelineTrend=")
    val trendEntryIdField = urtTrendMatch.fieldForToStringLabel(", entryId=")
    val trendClientEventInfoField = urtTrendMatch.fieldForToStringLabel(", clientEventInfo=")

    val trendPromotedMetadataField = trendMatch.fieldForToStringLabel(", promotedMetadata=")
    val trendPromotedDescField = trendMatch.fieldForToStringLabel(", promotedDescription=")

    val eventSummaryField = urtEventMatch.fieldForToStringLabel("UrtTimelineEventSummary(eventSummary=")
    val eventEntryIdField = urtEventMatch.fieldForToStringLabel(", entryId=")
    val eventClientEventInfoField = urtEventMatch.fieldForToStringLabel(", clientEventInfo=")

    val eventPromotedMetadataField = eventMatch.fieldForToStringLabel(", promotedMetadata=")

    val timelineTrendAccessor = urtTrendClass.resolveFieldAccessor(timelineTrendField, "URT timeline-trend model")
    val trendEntryIdAccessor = urtTrendClass.resolveFieldAccessor(trendEntryIdField, "URT trend entry ID")
    val trendClientEventInfoAccessor = urtTrendClass.resolveFieldAccessor(trendClientEventInfoField, "URT trend client-event info")

    val trendPromotedMetadataAccessor = trendClass.resolveFieldAccessor(trendPromotedMetadataField, "trend promoted metadata")
    val trendPromotedDescAccessor = trendClass.resolveFieldAccessor(trendPromotedDescField, "trend promoted description")

    val eventSummaryAccessor = urtEventClass.resolveFieldAccessor(eventSummaryField, "URT event-summary model")
    val eventEntryIdAccessor = urtEventClass.resolveFieldAccessor(eventEntryIdField, "URT event-summary entry ID")
    val eventClientEventInfoAccessor = urtEventClass.resolveFieldAccessor(eventClientEventInfoField, "URT event-summary client-event info")

    val eventPromotedMetadataAccessor = eventClass.resolveFieldAccessor(eventPromotedMetadataField, "event-summary promoted metadata")

    val filterClass = context.mutableClassDefBy(TIMELINE_FILTER_DESCRIPTOR)

    val isTimelineTrend = filterClass.requireBridge("isTimelineTrend", OBJECT_DESCRIPTOR, "Z")
    isTimelineTrend.patchBridge {
        val receiver = isTimelineTrend.p0Register
        instanceOf(receiver, receiver, urtTrendClass.type)
        returnValue(receiver)
    }
    filterClass.patchObjectAccessorGetter(
        "getTrendEntryId",
        urtTrendClass.type,
        trendEntryIdAccessor,
        STRING_DESCRIPTOR,
    )
    filterClass.patchObjectAccessorGetter(
        "getTrendClientEventInfo",
        urtTrendClass.type,
        trendClientEventInfoAccessor,
    )
    val getTrendPromotedMetadata =
        filterClass.requireBridge("getTrendPromotedMetadata", OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR)
    getTrendPromotedMetadata.patchBridge {
        val receiver = getTrendPromotedMetadata.p0Register
        checkCast(receiver, urtTrendClass.type)
        readModelAccessor(timelineTrendAccessor, receiver)
        ifEqz(receiver, Target.Local(NULL_RESULT_LABEL))
        checkCast(receiver, trendClass.type)
        readModelAccessor(trendPromotedMetadataAccessor, receiver)
        returnObject(receiver)
        label(NULL_RESULT_LABEL)
        constInt(receiver, 0)
        returnObject(receiver)
    }
    val getTrendPromotedDescription =
        filterClass.requireBridge("getTrendPromotedDescription", OBJECT_DESCRIPTOR, STRING_DESCRIPTOR)
    getTrendPromotedDescription.patchBridge {
        val receiver = getTrendPromotedDescription.p0Register
        checkCast(receiver, urtTrendClass.type)
        readModelAccessor(timelineTrendAccessor, receiver)
        ifEqz(receiver, Target.Local(NULL_RESULT_LABEL))
        checkCast(receiver, trendClass.type)
        readModelAccessor(trendPromotedDescAccessor, receiver)
        returnObject(receiver)
        label(NULL_RESULT_LABEL)
        constInt(receiver, 0)
        returnObject(receiver)
    }

    val isTimelineEventSummary =
        filterClass.requireBridge("isTimelineEventSummary", OBJECT_DESCRIPTOR, "Z")
    isTimelineEventSummary.patchBridge {
        val receiver = isTimelineEventSummary.p0Register
        instanceOf(receiver, receiver, urtEventClass.type)
        returnValue(receiver)
    }
    filterClass.patchObjectAccessorGetter(
        "getEventSummaryEntryId",
        urtEventClass.type,
        eventEntryIdAccessor,
        STRING_DESCRIPTOR,
    )
    filterClass.patchObjectAccessorGetter(
        "getEventSummaryClientEventInfo",
        urtEventClass.type,
        eventClientEventInfoAccessor,
    )
    val getEventSummaryPromotedMetadata =
        filterClass.requireBridge(
            "getEventSummaryPromotedMetadata",
            OBJECT_DESCRIPTOR,
            OBJECT_DESCRIPTOR,
        )
    getEventSummaryPromotedMetadata.patchBridge {
        val receiver = getEventSummaryPromotedMetadata.p0Register
        checkCast(receiver, urtEventClass.type)
        readModelAccessor(eventSummaryAccessor, receiver)
        ifEqz(receiver, Target.Local(NULL_RESULT_LABEL))
        checkCast(receiver, eventClass.type)
        readModelAccessor(eventPromotedMetadataAccessor, receiver)
        returnObject(receiver)
        label(NULL_RESULT_LABEL)
        constInt(receiver, 0)
        returnObject(receiver)
    }
}

