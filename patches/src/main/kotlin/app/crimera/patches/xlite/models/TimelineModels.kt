package app.crimera.patches.xlite.models

import app.crimera.patches.xlite.misc.extension.xLiteExtensionPatch
import app.crimera.patches.xlite.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.WeakHashMap

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"

private object TimelinePostModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/timelines/items/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("UrtTimelinePost(postResult="), string(", promotedMetadata=")),
)

private object TimelineModuleModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/timelines/items/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("UrtTimelineModule(innerContent="), string(", moduleHeader=")),
)

private object TimelineModuleItemModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/timelines/items/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("UrtTimelineModuleItem(item="), string(", isDispensable=")),
)

private object VerticalConversationModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/timelinemodule/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("VerticalConversation(allTweetIds=")),
)

private object ClientEventInfoModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("ClientEventInfo(component=")),
)

/** Immutable release facts shared by timeline adapters and timeline feature patches. */
internal data class ResolvedXLiteTimelineModels(
    val postDescriptor: String,
    val postResultField: FieldReference,
    val postEntryIdField: FieldReference,
    val postClientEventInfoField: FieldReference,
    val postPromotedMetadataField: FieldReference,
    val clientEventInfoDescriptor: String,
    val clientEventInfoComponentField: FieldReference,
    val postIdGetter: MethodReference,
    val postTextGetter: MethodReference,
    val moduleDescriptor: String,
    val moduleItemDescriptor: String,
    val verticalConversationDescriptor: String,
    val verticalConversationPostIdsField: FieldReference,
    val verticalConversationConstructor: MethodReference,
    val moduleInnerContentField: FieldReference,
    val moduleHeaderField: FieldReference,
    val moduleFooterField: FieldReference,
    val moduleDisplayTypeField: FieldReference,
    val moduleSortIndexField: FieldReference,
    val moduleEntryIdField: FieldReference,
    val moduleClientEventInfoField: FieldReference,
    val moduleItemField: FieldReference,
    val moduleItemDispensableField: FieldReference,
    val moduleConstructor: MethodReference,
    val moduleItemConstructor: MethodReference,
)

private object TimelineModelCache {
    private val values = WeakHashMap<BytecodePatchContext, ResolvedXLiteTimelineModels>()

    @Synchronized
    fun getOrPut(
        context: BytecodePatchContext,
        resolve: () -> ResolvedXLiteTimelineModels,
    ): ResolvedXLiteTimelineModels = values.getOrPut(context, resolve)
}

internal val xLiteTimelineModelResolutionPatch =
    bytecodePatch(default = false) {
        execute {
            resolvedXLiteTimelineModels()
        }
    }

internal val xLiteTimelineModelAdapterPatch =
    bytecodePatch(default = false) {
        dependsOn(xLiteTimelineModelResolutionPatch, xLiteExtensionPatch)

        execute {
            patchTimelineModelBridges(resolvedXLiteTimelineModels())
        }
    }

context(context: BytecodePatchContext)
internal fun resolvedXLiteTimelineModels(): ResolvedXLiteTimelineModels =
    TimelineModelCache.getOrPut(context) { resolveTimelineModels() }

context(context: BytecodePatchContext)
private fun resolveTimelineModels(): ResolvedXLiteTimelineModels {
    val postMatch = TimelinePostModelFingerprint.requireSingle("timeline post model")
    val moduleMatch = TimelineModuleModelFingerprint.requireSingle("timeline module model")
    val moduleItemMatch = TimelineModuleItemModelFingerprint.requireSingle("timeline module-item model")
    val verticalConversationMatch =
        VerticalConversationModelFingerprint.requireSingle("vertical-conversation display model")
    val clientEventInfoMatch = ClientEventInfoModelFingerprint.requireSingle("client-event-info model")

    val postClass = postMatch.originalClassDef
    val moduleClass = moduleMatch.originalClassDef
    val moduleItemClass = moduleItemMatch.originalClassDef
    val verticalConversationClass = verticalConversationMatch.originalClassDef

    val postIdGetter = postClass.methods.singleOrNull { method ->
        method.name == "getId" &&
            method.parameterTypes.isEmpty() &&
            method.returnType.startsWith("L")
    } ?: throw PatchException(
        "Expected one X-Lite timeline post getId() in $postClass: " +
            postClass.methods.joinToString(),
    )
    val postTextGetter = postClass.methods.singleOrNull { method ->
        method.name == "getText" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == STRING_DESCRIPTOR
    } ?: throw PatchException(
        "Expected one X-Lite timeline post getText() in $postClass: " +
            postClass.methods.joinToString(),
    )

    val postResultField = postMatch.fieldForToStringLabel("UrtTimelinePost(postResult=")
    val postEntryIdField = postMatch.fieldForToStringLabel(", entryId=")
    val postClientEventInfoField = postMatch.fieldForToStringLabel(", clientEventInfo=")
    val postPromotedMetadataField = postMatch.fieldForToStringLabel(", promotedMetadata=")
    val clientEventInfoComponentField =
        clientEventInfoMatch.fieldForToStringLabel("ClientEventInfo(component=")
    if (clientEventInfoComponentField.type != STRING_DESCRIPTOR) {
        throw PatchException(
            "X-Lite client-event-info component is not a String: $clientEventInfoComponentField",
        )
    }

    val verticalConversationPostIdsField =
        verticalConversationClass.requireSingleInstanceField(
            LIST_DESCRIPTOR,
            "vertical-conversation post IDs",
        )
    val verticalConversationConstructor = verticalConversationClass.methods.singleOrNull { method ->
        method.name == "<init>" &&
            method.parameterTypes.map(CharSequence::toString) == listOf(LIST_DESCRIPTOR) &&
            method.returnType == "V"
    } ?: throw PatchException(
        "Expected one X-Lite vertical-conversation constructor in $verticalConversationClass",
    )

    val moduleInnerContentField =
        moduleClass.requireSingleInstanceField(LIST_DESCRIPTOR, "timeline module children")
    val moduleHeaderField = moduleMatch.fieldForToStringLabel(", moduleHeader=")
    val moduleFooterField = moduleMatch.fieldForToStringLabel(", moduleFooter=")
    val moduleDisplayTypeField = moduleMatch.fieldForToStringLabel(", displayType=")
    val moduleSortIndexField = moduleClass.requireSingleInstanceField("J", "timeline module sort index")
    val moduleEntryIdField =
        moduleClass.requireSingleInstanceField(STRING_DESCRIPTOR, "timeline module entry ID")
    val moduleClientEventInfoField = moduleMatch.fieldForToStringLabel(", clientEventInfo=")
    val clientEventInfoDescriptor = clientEventInfoMatch.originalClassDef.type
    if (postClientEventInfoField.type != clientEventInfoDescriptor ||
        moduleClientEventInfoField.type != clientEventInfoDescriptor
    ) {
        throw PatchException(
            "X-Lite client-event-info field types changed: " +
                "post=${postClientEventInfoField.type}, module=${moduleClientEventInfoField.type}, " +
                "model=$clientEventInfoDescriptor",
        )
    }
    val moduleConstructorParameters = listOf(
        moduleInnerContentField.type,
        moduleHeaderField.type,
        moduleFooterField.type,
        moduleDisplayTypeField.type,
        "J",
        STRING_DESCRIPTOR,
        moduleClientEventInfoField.type,
    )
    val moduleConstructor = moduleClass.methods.singleOrNull { method ->
        method.name == "<init>" &&
            method.parameterTypes.map(CharSequence::toString) == moduleConstructorParameters &&
            method.returnType == "V"
    } ?: throw PatchException(
        "Expected one X-Lite timeline module constructor with $moduleConstructorParameters in $moduleClass",
    )

    val moduleItemField = moduleItemMatch.fieldForToStringLabel("UrtTimelineModuleItem(item=")
    val moduleItemDispensableField =
        moduleItemClass.requireSingleInstanceField("Z", "timeline module-item dispensable")
    val moduleItemConstructorParameters = listOf(moduleItemField.type, "Z")
    val moduleItemConstructor = moduleItemClass.methods.singleOrNull { method ->
        method.name == "<init>" &&
            method.parameterTypes.map(CharSequence::toString) == moduleItemConstructorParameters &&
            method.returnType == "V"
    } ?: throw PatchException(
        "Expected one X-Lite timeline module-item constructor with " +
            "$moduleItemConstructorParameters in $moduleItemClass",
    )

    return ResolvedXLiteTimelineModels(
        postDescriptor = postClass.type,
        postResultField = postResultField,
        postEntryIdField = postEntryIdField,
        postClientEventInfoField = postClientEventInfoField,
        postPromotedMetadataField = postPromotedMetadataField,
        clientEventInfoDescriptor = clientEventInfoDescriptor,
        clientEventInfoComponentField = clientEventInfoComponentField,
        postIdGetter = postIdGetter,
        postTextGetter = postTextGetter,
        moduleDescriptor = moduleClass.type,
        moduleItemDescriptor = moduleItemClass.type,
        verticalConversationDescriptor = verticalConversationClass.type,
        verticalConversationPostIdsField = verticalConversationPostIdsField,
        verticalConversationConstructor = verticalConversationConstructor,
        moduleInnerContentField = moduleInnerContentField,
        moduleHeaderField = moduleHeaderField,
        moduleFooterField = moduleFooterField,
        moduleDisplayTypeField = moduleDisplayTypeField,
        moduleSortIndexField = moduleSortIndexField,
        moduleEntryIdField = moduleEntryIdField,
        moduleClientEventInfoField = moduleClientEventInfoField,
        moduleItemField = moduleItemField,
        moduleItemDispensableField = moduleItemDispensableField,
        moduleConstructor = moduleConstructor,
        moduleItemConstructor = moduleItemConstructor,
    )
}

context(context: BytecodePatchContext)
private fun patchTimelineModelBridges(models: ResolvedXLiteTimelineModels) {
    // ALPHA PATH uses public model fields; BETA PATH uses private-model getters.
    // The shared bridges keep the extension API stable while this compatibility split remains.
    val postClass = context.mutableClassDefBy(models.postDescriptor)
    val postEntryIdAccessor =
        postClass.resolveFieldAccessor(models.postEntryIdField, "timeline post entry ID")
    val postClientEventInfoAccessor =
        postClass.resolveFieldAccessor(
            models.postClientEventInfoField,
            "timeline post client-event info",
        )
    val postPromotedMetadataAccessor =
        postClass.resolveFieldAccessor(
            models.postPromotedMetadataField,
            "timeline post promoted metadata",
        )

    val clientEventInfoClass = context.mutableClassDefBy(models.clientEventInfoDescriptor)
    val clientEventInfoComponentAccessor =
        clientEventInfoClass.resolveFieldAccessor(
            models.clientEventInfoComponentField,
            "client-event-info component",
        )

    val verticalConversationClass = context.mutableClassDefBy(models.verticalConversationDescriptor)
    val verticalConversationPostIdsAccessor =
        verticalConversationClass.resolveFieldAccessor(
            models.verticalConversationPostIdsField,
            "vertical-conversation post IDs",
        )

    val moduleClass = context.mutableClassDefBy(models.moduleDescriptor)
    val moduleInnerContentAccessor =
        moduleClass.resolveFieldAccessor(models.moduleInnerContentField, "timeline module inner content")
    val moduleHeaderAccessor =
        moduleClass.resolveFieldAccessor(models.moduleHeaderField, "timeline module header")
    val moduleFooterAccessor =
        moduleClass.resolveFieldAccessor(models.moduleFooterField, "timeline module footer")
    val moduleDisplayTypeAccessor =
        moduleClass.resolveFieldAccessor(models.moduleDisplayTypeField, "timeline module display type")
    val moduleSortIndexAccessor =
        moduleClass.resolveFieldAccessor(models.moduleSortIndexField, "timeline module sort index")
    val moduleEntryIdAccessor =
        moduleClass.resolveFieldAccessor(models.moduleEntryIdField, "timeline module entry ID")
    val moduleClientEventInfoAccessor =
        moduleClass.resolveFieldAccessor(
            models.moduleClientEventInfoField,
            "timeline module client-event info",
        )

    val moduleItemClass = context.mutableClassDefBy(models.moduleItemDescriptor)
    val moduleItemAccessor =
        moduleItemClass.resolveFieldAccessor(models.moduleItemField, "timeline module item")
    val moduleItemDispensableAccessor =
        moduleItemClass.resolveFieldAccessor(
            models.moduleItemDispensableField,
            "timeline module-item dispensable flag",
        )

    val filterClass = context.mutableClassDefBy(TIMELINE_FILTER_DESCRIPTOR)
    filterClass.patchBridge(
        "isTimelineModuleItem",
        OBJECT_DESCRIPTOR,
        "Z",
        "instance-of p0, p0, ${models.moduleItemDescriptor}\nreturn p0",
    )
    filterClass.patchBridge(
        "isTimelinePost",
        OBJECT_DESCRIPTOR,
        "Z",
        "instance-of p0, p0, ${models.postDescriptor}\nreturn p0",
    )
    filterClass.patchBridge(
        "isTimelineModule",
        OBJECT_DESCRIPTOR,
        "Z",
        "instance-of p0, p0, ${models.moduleDescriptor}\nreturn p0",
    )
    filterClass.patchBridge(
        "isPromotedClientEventInfo",
        OBJECT_DESCRIPTOR,
        "Z",
        """
            check-cast p0, ${models.clientEventInfoDescriptor}
            ${clientEventInfoComponentAccessor.readObject("p0")}
            invoke-static {p0}, $TIMELINE_FILTER_DESCRIPTOR->hasPromotedClientEventInfoComponent(Ljava/lang/String;)Z
            move-result p0
            return p0
        """.trimIndent(),
    )
    filterClass.patchBridge(
        "getPostId",
        OBJECT_DESCRIPTOR,
        OBJECT_DESCRIPTOR,
        """
            check-cast p0, ${models.postDescriptor}
            invoke-virtual {p0}, ${models.postIdGetter.smaliReference()}
            move-result-object p0
            return-object p0
        """.trimIndent(),
    )
    filterClass.patchBridge(
        "isVerticalConversation",
        OBJECT_DESCRIPTOR,
        "Z",
        "instance-of p0, p0, ${models.verticalConversationDescriptor}\nreturn p0",
    )
    filterClass.patchObjectAccessorGetter(
        "getVerticalConversationPostIds",
        models.verticalConversationDescriptor,
        verticalConversationPostIdsAccessor,
        LIST_DESCRIPTOR,
    )
    filterClass.patchBridge(
        "copyVerticalConversation",
        "$OBJECT_DESCRIPTOR$LIST_DESCRIPTOR",
        OBJECT_DESCRIPTOR,
        """
            new-instance p0, ${models.verticalConversationDescriptor}
            invoke-direct {p0, p1}, ${models.verticalConversationConstructor.smaliReference()}
            return-object p0
        """.trimIndent(),
    )
    filterClass.patchObjectAccessorGetter(
        "getModuleItem",
        models.moduleItemDescriptor,
        moduleItemAccessor,
    )
    filterClass.patchBooleanAccessorGetter(
        "isModuleItemDispensable",
        models.moduleItemDescriptor,
        moduleItemDispensableAccessor,
    )
    filterClass.patchBridge(
        "copyModuleItem",
        "$OBJECT_DESCRIPTOR$OBJECT_DESCRIPTOR" + "Z",
        OBJECT_DESCRIPTOR,
        """
            check-cast p1, ${models.moduleItemField.type}
            new-instance p0, ${models.moduleItemDescriptor}
            invoke-direct {p0, p1, p2}, ${models.moduleItemConstructor.smaliReference()}
            return-object p0
        """.trimIndent(),
    )
    filterClass.patchObjectAccessorGetter(
        "getModuleInnerContent",
        models.moduleDescriptor,
        moduleInnerContentAccessor,
        LIST_DESCRIPTOR,
    )
    filterClass.patchObjectAccessorGetter(
        "getModuleHeader",
        models.moduleDescriptor,
        moduleHeaderAccessor,
    )
    filterClass.patchObjectAccessorGetter(
        "getModuleFooter",
        models.moduleDescriptor,
        moduleFooterAccessor,
    )
    filterClass.patchObjectAccessorGetter(
        "getModuleDisplayType",
        models.moduleDescriptor,
        moduleDisplayTypeAccessor,
    )
    filterClass.patchWideAccessorGetter(
        "getModuleSortIndex",
        models.moduleDescriptor,
        moduleSortIndexAccessor,
    )
    filterClass.patchObjectAccessorGetter(
        "getModuleEntryId",
        models.moduleDescriptor,
        moduleEntryIdAccessor,
        STRING_DESCRIPTOR,
    )
    filterClass.patchObjectAccessorGetter(
        "getModuleClientEventInfo",
        models.moduleDescriptor,
        moduleClientEventInfoAccessor,
    )
    filterClass.patchObjectAccessorGetter(
        "getPostEntryId",
        models.postDescriptor,
        postEntryIdAccessor,
        STRING_DESCRIPTOR,
    )
    filterClass.patchObjectAccessorGetter(
        "getPostClientEventInfo",
        models.postDescriptor,
        postClientEventInfoAccessor,
    )
    filterClass.patchObjectAccessorGetter(
        "getPostPromotedMetadata",
        models.postDescriptor,
        postPromotedMetadataAccessor,
    )
    filterClass.patchBridge(
        "copyModule",
        "$OBJECT_DESCRIPTOR$LIST_DESCRIPTOR$OBJECT_DESCRIPTOR$OBJECT_DESCRIPTOR" +
            "$OBJECT_DESCRIPTOR" + "J$STRING_DESCRIPTOR$OBJECT_DESCRIPTOR",
        OBJECT_DESCRIPTOR,
        """
            check-cast p1, ${models.moduleInnerContentField.type}
            check-cast p2, ${models.moduleHeaderField.type}
            check-cast p3, ${models.moduleFooterField.type}
            check-cast p4, ${models.moduleDisplayTypeField.type}
            check-cast p8, ${models.moduleClientEventInfoField.type}
            new-instance p0, ${models.moduleDescriptor}
            invoke-direct/range {p0 .. p8}, ${models.moduleConstructor.smaliReference()}
            return-object p0
        """.trimIndent(),
    )
}
