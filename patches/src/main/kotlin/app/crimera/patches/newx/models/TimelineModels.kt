package app.crimera.patches.newx.models

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.crimera.bytecode.methodReference
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.util.getReference
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import java.util.WeakHashMap

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val ITERABLE_DESCRIPTOR = "Ljava/lang/Iterable;"
private const val X_PACKAGE_SCOPE = "Lcom/x/"

private object TimelineItemsImmutableListConverterFingerprint : Fingerprint(
    definingClass = X_PACKAGE_SCOPE,
    parameters = listOf(ITERABLE_DESCRIPTOR),
    custom = { method, _ ->
        AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.returnType.startsWith("Lkotlinx/collections/immutable/")
    },
)

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
internal data class ResolvedNewXTimelineModels(
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
    private val values = WeakHashMap<BytecodePatchContext, ResolvedNewXTimelineModels>()

    @Synchronized
    fun getOrPut(
        context: BytecodePatchContext,
        resolve: () -> ResolvedNewXTimelineModels,
    ): ResolvedNewXTimelineModels = values.getOrPut(context, resolve)
}

internal val newXTimelineModelResolutionPatch =
    bytecodePatch(default = false) {
        execute {
            resolvedNewXTimelineModels()
        }
    }

internal val newXTimelineModelAdapterPatch =
    bytecodePatch(default = false) {
        dependsOn(newXTimelineModelResolutionPatch, newXExtensionPatch)

        execute {
            patchTimelineModelBridges(resolvedNewXTimelineModels())
        }
    }

context(context: BytecodePatchContext)
internal fun resolvedNewXTimelineModels(): ResolvedNewXTimelineModels =
    TimelineModelCache.getOrPut(context) { resolveTimelineModels() }

context(context: BytecodePatchContext)
private fun resolveTimelineModels(): ResolvedNewXTimelineModels {
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
        "Expected one NewX timeline post getId() in $postClass: " +
            postClass.methods.joinToString(),
    )
    val postTextGetter = postClass.methods.singleOrNull { method ->
        method.name == "getText" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == STRING_DESCRIPTOR
    } ?: throw PatchException(
        "Expected one NewX timeline post getText() in $postClass: " +
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
            "NewX client-event-info component is not a String: $clientEventInfoComponentField",
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
        "Expected one NewX vertical-conversation constructor in $verticalConversationClass",
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
            "NewX client-event-info field types changed: " +
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
        "Expected one NewX timeline module constructor with $moduleConstructorParameters in $moduleClass",
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
        "Expected one NewX timeline module-item constructor with " +
            "$moduleItemConstructorParameters in $moduleItemClass",
    )

    return ResolvedNewXTimelineModels(
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
private fun patchTimelineModelBridges(models: ResolvedNewXTimelineModels) {
    // Resolve public fields and generated getters into one stable extension-facing contract.
    val immutableListMatches = TimelineItemsImmutableListConverterFingerprint.scopedMatchAll()
    val immutableListConverterCandidates = immutableListMatches.mapNotNull { match ->
        val method = match.originalMethod
        val instructions = method.implementation?.instructions?.toList() ?: return@mapNotNull null
        if (!isJavaListType(method.returnType) ||
            instructions.count { instruction ->
                instruction.opcode == Opcode.INSTANCE_OF &&
                    instruction.getReference<TypeReference>()?.type == method.returnType
            } != 1
        ) {
            return@mapNotNull null
        }
        val fallbackCalls = instructions.mapNotNull { instruction ->
            val reference = instruction.getReference<MethodReference>() ?: return@mapNotNull null
            reference.takeIf {
                instruction.opcode == Opcode.INVOKE_STATIC &&
                    it.definingClass == method.definingClass &&
                    it.parameterTypes.map(CharSequence::toString) == listOf(ITERABLE_DESCRIPTOR) &&
                    isJavaListType(it.returnType.toString())
            }
        }
        if (fallbackCalls.size != 1) return@mapNotNull null
        method
    }
    if (immutableListConverterCandidates.size != 1) {
        throw PatchException(
            "Expected one NewX timeline immutable-list converter, found " +
                "${immutableListConverterCandidates.size}: " +
                immutableListConverterCandidates.joinToString(),
        )
    }
    val immutableListConverter = immutableListConverterCandidates.single()
    if (!AccessFlags.STATIC.isSet(immutableListConverter.accessFlags)) {
        throw PatchException("NewX timeline immutable-list converter is not static: $immutableListConverter")
    }
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
    val immutableListBridge =
        filterClass.requireBridge("immutableList", LIST_DESCRIPTOR, OBJECT_DESCRIPTOR)
    immutableListBridge.patchBridge {
        val receiver = immutableListBridge.p0Register
        checkCast(receiver, ITERABLE_DESCRIPTOR)
        invokeStatic(immutableListConverter, receiver)
        moveResult(receiver, immutableListConverter.returnType)
        returnObject(receiver)
    }
    val isTimelineModuleItem =
        filterClass.requireBridge("isTimelineModuleItem", OBJECT_DESCRIPTOR, "Z")
    isTimelineModuleItem.patchBridge {
        val receiver = isTimelineModuleItem.p0Register
        instanceOf(receiver, receiver, models.moduleItemDescriptor)
        returnValue(receiver)
    }
    val isTimelinePost = filterClass.requireBridge("isTimelinePost", OBJECT_DESCRIPTOR, "Z")
    isTimelinePost.patchBridge {
        val receiver = isTimelinePost.p0Register
        instanceOf(receiver, receiver, models.postDescriptor)
        returnValue(receiver)
    }
    val isTimelineModule = filterClass.requireBridge("isTimelineModule", OBJECT_DESCRIPTOR, "Z")
    isTimelineModule.patchBridge {
        val receiver = isTimelineModule.p0Register
        instanceOf(receiver, receiver, models.moduleDescriptor)
        returnValue(receiver)
    }
    val isPromotedClientEventInfo =
        filterClass.requireBridge("isPromotedClientEventInfo", OBJECT_DESCRIPTOR, "Z")
    isPromotedClientEventInfo.patchBridge {
        val receiver = isPromotedClientEventInfo.p0Register
        val componentCheck = methodReference(
            "$TIMELINE_FILTER_DESCRIPTOR->hasPromotedClientEventInfoComponent" +
                "(Ljava/lang/String;)Z",
        )
        checkCast(receiver, models.clientEventInfoDescriptor)
        readModelAccessor(clientEventInfoComponentAccessor, receiver)
        invokeStatic(componentCheck, receiver)
        moveResult(receiver, componentCheck.returnType)
        returnValue(receiver)
    }
    val getPostId = filterClass.requireBridge("getPostId", OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR)
    getPostId.patchBridge {
        val receiver = getPostId.p0Register
        checkCast(receiver, models.postDescriptor)
        invokeVirtual(models.postIdGetter, receiver)
        moveResult(receiver, models.postIdGetter.returnType)
        returnObject(receiver)
    }
    val isVerticalConversation =
        filterClass.requireBridge("isVerticalConversation", OBJECT_DESCRIPTOR, "Z")
    isVerticalConversation.patchBridge {
        val receiver = isVerticalConversation.p0Register
        instanceOf(receiver, receiver, models.verticalConversationDescriptor)
        returnValue(receiver)
    }
    filterClass.patchObjectAccessorGetter(
        "getVerticalConversationPostIds",
        models.verticalConversationDescriptor,
        verticalConversationPostIdsAccessor,
        LIST_DESCRIPTOR,
    )
    val copyVerticalConversation =
        filterClass.requireBridge(
            "copyVerticalConversation",
            "$OBJECT_DESCRIPTOR$LIST_DESCRIPTOR",
            OBJECT_DESCRIPTOR,
        )
    copyVerticalConversation.patchBridge {
        val receiver = copyVerticalConversation.p0Register
        newInstance(receiver, models.verticalConversationDescriptor)
        invokeDirect(models.verticalConversationConstructor, receiver, receiver + 1)
        returnObject(receiver)
    }
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
    val copyModuleItem =
        filterClass.requireBridge(
            "copyModuleItem",
            "$OBJECT_DESCRIPTOR$OBJECT_DESCRIPTOR" + "Z",
            OBJECT_DESCRIPTOR,
        )
    copyModuleItem.patchBridge {
        val receiver = copyModuleItem.p0Register
        checkCast(receiver + 1, models.moduleItemField.type)
        newInstance(receiver, models.moduleItemDescriptor)
        invokeDirect(models.moduleItemConstructor, receiver, receiver + 1, receiver + 2)
        returnObject(receiver)
    }
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
    val copyModule =
        filterClass.requireBridge(
            "copyModule",
            "$OBJECT_DESCRIPTOR$LIST_DESCRIPTOR$OBJECT_DESCRIPTOR$OBJECT_DESCRIPTOR" +
                "$OBJECT_DESCRIPTOR" + "J$STRING_DESCRIPTOR$OBJECT_DESCRIPTOR",
            OBJECT_DESCRIPTOR,
        )
    copyModule.patchBridge {
        val receiver = copyModule.p0Register
        checkCast(receiver + 1, models.moduleInnerContentField.type)
        checkCast(receiver + 2, models.moduleHeaderField.type)
        checkCast(receiver + 3, models.moduleFooterField.type)
        checkCast(receiver + 4, models.moduleDisplayTypeField.type)
        checkCast(receiver + 8, models.moduleClientEventInfoField.type)
        newInstance(receiver, models.moduleDescriptor)
        invokeDirect(
            models.moduleConstructor,
            receiver,
            receiver + 1,
            receiver + 2,
            receiver + 3,
            receiver + 4,
            receiver + 5,
            receiver + 6,
            receiver + 7,
            receiver + 8,
        )
        returnObject(receiver)
    }
}

context(context: BytecodePatchContext)
private fun isJavaListType(type: String): Boolean {
    if (type == LIST_DESCRIPTOR) return true
    if (!type.startsWith("L")) return false
    return isJavaListType(type, mutableSetOf())
}

context(context: BytecodePatchContext)
private fun isJavaListType(type: String, visitedTypes: MutableSet<String>): Boolean {
    if (type == LIST_DESCRIPTOR) return true
    if (!visitedTypes.add(type)) return false
    val definition = runCatching { context.mutableClassDefBy(type) }.getOrNull() ?: return false
    if (definition.interfaces.any { interfaceType ->
            isJavaListType(interfaceType.toString(), visitedTypes)
        }
    ) {
        return true
    }
    return definition.superclass?.let { superclass ->
        isJavaListType(superclass, visitedTypes)
    } == true
}
