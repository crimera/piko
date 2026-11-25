package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.misc.extension.newXInitHook
import app.crimera.patches.newx.misc.inlineactions.provesParameterValue
import app.crimera.patches.newx.misc.inlineactions.provesValueFromInstruction
import app.crimera.patches.newx.misc.inlineactions.provesValueFromInstructionOnSourcePaths
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.settings.SettingReadRegisterConstraint
import app.crimera.patches.settings.customScreen
import app.crimera.patches.settings.group
import app.crimera.patches.settings.injectRead
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.settings.toggle
import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.FOR_YOU_TOPIC_FILTER_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.FOR_YOU_TOPIC_FILTER_FRAGMENT_DESCRIPTOR
import app.crimera.bytecode.Block
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.instanceOf
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefByOrNull
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.numberOfParameterRegisters
import app.morphe.patcher.util.numberOfParameterRegistersLogical
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val ARRAY_LIST_DESCRIPTOR = "Ljava/util/ArrayList;"
private const val HOME_TIMELINE_PACKAGE = "Lcom/x/android/main/"
private const val HOME_MODELS_PACKAGE = "Lcom/x/models/"
private const val HOME_FILTER_GROUP_FILTER_TYPE_LABEL = "HomeFilterGroup(filterType="
private const val HOME_FILTER_GROUP_OPTIONS_LABEL = ", options="
private const val INTRINSICS_DESCRIPTOR = "Lkotlin/jvm/internal/Intrinsics;"
private const val OBJECT_LIST_DESCRIPTOR = "Ljava/util/List;"
private const val INTEGER_DESCRIPTOR = "I"
private const val CLEAR_AND_REFRESH_TIMELINE = "ClearAndRefreshTimeline"
private const val REQUEST_SCROLL_TO_TOP = "RequestScrollToTop"
private const val FOR_YOU_REFRESH_TARGET_DESCRIPTOR =
    "Lapp/morphe/extension/newx/timeline/ForYouTopicFilter\$RefreshTarget;"
private const val FOR_YOU_REFRESH_BRIDGE_NAME = "pikoRefreshForYouTopicFilter"
private const val TIMELINE_REFRESH_GATE_DESCRIPTOR =
    "Lapp/morphe/extension/newx/timeline/TimelineRefreshGate;"
private const val FOR_YOU_TOPIC_FILTER_INITIALIZE_DESCRIPTOR =
    "$FOR_YOU_TOPIC_FILTER_DESCRIPTOR->initialize(Landroid/content/Context;)V"
private const val FOR_YOU_TOPIC_FILTER_SHOW_SHEET_DESCRIPTOR =
    "$FOR_YOU_TOPIC_FILTER_DESCRIPTOR->showForYouTopicSheet($FOR_YOU_REFRESH_TARGET_DESCRIPTOR)Z"
private const val FOR_YOU_TOPIC_FILTER_RESOLVE_TOPIC_IDS_DESCRIPTOR =
    "$FOR_YOU_TOPIC_FILTER_DESCRIPTOR->resolveForYouTopicIds($LIST_DESCRIPTOR)$LIST_DESCRIPTOR"
private const val FOR_YOU_TOPIC_FILTER_CAPTURE_OPTIONS_DESCRIPTOR =
    "$FOR_YOU_TOPIC_FILTER_DESCRIPTOR->captureTopicOptions($OBJECT_DESCRIPTOR$OBJECT_DESCRIPTOR)V"
private const val INTRINSICS_ARE_EQUAL_DESCRIPTOR =
    "$INTRINSICS_DESCRIPTOR->areEqual($OBJECT_DESCRIPTOR$OBJECT_DESCRIPTOR)Z"
private const val FOR_YOU_REFRESH_GATE_MARK_DESCRIPTOR =
    "$TIMELINE_REFRESH_GATE_DESCRIPTOR->markForYouFilterRefresh()V"
private const val LIST_GET_DESCRIPTOR = "$LIST_DESCRIPTOR->get($INTEGER_DESCRIPTOR)$OBJECT_DESCRIPTOR"
private const val LIST_SIZE_DESCRIPTOR = "$LIST_DESCRIPTOR->size()$INTEGER_DESCRIPTOR"
private const val COLLECTIONS_GET_OR_NULL_DESCRIPTOR =
    "Lkotlin/collections/CollectionsKt;->getOrNull($LIST_DESCRIPTOR$INTEGER_DESCRIPTOR)$OBJECT_DESCRIPTOR"

private object HomeFilterGroupFingerprint : Fingerprint(
    definingClass = HOME_MODELS_PACKAGE,
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string(HOME_FILTER_GROUP_FILTER_TYPE_LABEL)),
)

private object HomeTimelineQueryFingerprint : Fingerprint(
    definingClass = HOME_TIMELINE_PACKAGE,
    name = "name",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("HomeTimeline")),
)

private data class ResolvedForYouRequestTarget(
    val requestFingerprint: Fingerprint,
    val queryConstructor: Method,
    val topicParameterIndex: Int,
)

private data class ResolvedForYouTabHook(
    val method: MutableMethod,
    val insertionIndex: Int,
    val refreshBridge: ResolvedForYouCurrentPageRefreshBridge,
    val forYouPageRegister: Int,
    val forYouSingletonField: FieldReference,
    val reservedRegisterBase: Int,
)

private data class ResolvedTimelineEvent(
    val field: FieldReference,
    val dispatchParameterTypes: Set<String>,
)

// The native page lookup is not kept: List.get and getOrNull read the same page, and the bridge
// emits its own bounds-checked List.get.
internal data class ResolvedForYouCurrentPageRefreshBridge(
    val stateField: FieldReference,
    val stateGetter: MethodReference,
    val pagesType: String,
    val pagesListField: FieldReference,
    val pagesIndexField: FieldReference,
    val componentType: String,
    val componentGetter: MethodReference,
    val forYouComponentType: String,
    val forYouControllerField: FieldReference,
    val refreshDispatch: MethodReference,
)

private data class ResolvedForYouPageTarget(
    val singletonField: FieldReference,
    val modelTypes: Set<String>,
)

private data class ResolvedModernForYouTabHook(
    val insertionIndex: Int,
    val pageRegister: Int,
    val componentType: String,
)

@Suppress("unused")
val newXForYouTopicFilterPatch =
    patch(
        name = "NewX: Filter For You by topic",
        description = "Restricts the NewX For You timeline to selected topics.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)
        category("NewX patches")
        dependsOn(newXExtensionPatch)

        val forYouTabHookEnabled =
            newXSettings {
                category(Categories.TIMELINE) {
                    group(Groups.FOR_YOU_FILTERING) {
                        val setting =
                            toggle(
                                id = "newx.timeline.for_you_filtering.tab_hook",
                                strings = settingStrings("piko_newx_for_you_tab_hook"),
                                order = 100,
                                defaultValue = true,
                            )
                        customScreen(
                            id = "newx.content.topic_filtering.manage",
                            strings = settingStrings("piko_newx_topic_filtering"),
                            order = 200,
                            iconResourceName = "ic_vector_filter",
                            fragmentClassDescriptor = FOR_YOU_TOPIC_FILTER_FRAGMENT_DESCRIPTOR,
                        )
                        setting
                    }
                }
            }

        execute {
            // Resolutions run before any mutation, so none can go stale.
            val clearAndRefreshEvent = resolveSingletonTimelineEvent(CLEAR_AND_REFRESH_TIMELINE)
            val scrollToTopEvent = resolveSingletonTimelineEvent(REQUEST_SCROLL_TO_TOP)
            val forYouPageTarget = resolveForYouPageTarget()
            val reselectedEventType = resolveForYouReselectedEventType()
            val tabHook = resolveForYouTabHook(scrollToTopEvent, forYouPageTarget, reselectedEventType)
            val requestTarget = resolveForYouRequestTarget()

            val applicationOnCreate = newXInitHook.fingerprint.method
            applicationOnCreate.insertHook(0, relocateBranchTargets = false) {
                invokeStatic(
                    methodReference(FOR_YOU_TOPIC_FILTER_INITIALIZE_DESCRIPTOR),
                    applicationOnCreate.p0Register,
                )
            }

            patchHomeFilterGroupConstructor()

            installForYouRefreshBridge(tabHook, clearAndRefreshEvent, scrollToTopEvent)
            val hookEnabledRead =
                forYouTabHookEnabled.injectRead(
                    method = tabHook.method,
                    index = tabHook.insertionIndex,
                    registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                )
            val pageRegister = tabHook.forYouPageRegister
            val reservedRegisterBase = tabHook.reservedRegisterBase
            val registerCount = tabHook.method.implementation?.registerCount
                ?: throw PatchException("NewX For You tab hook has no implementation")
            val scratchRegisters = listOf(reservedRegisterBase, reservedRegisterBase + 1)
            if (scratchRegisters.any { it < 0 || it >= registerCount || it > 255 }) {
                throw PatchException(
                    "NewX For You identity registers are not encodable or reserved: " +
                        "${scratchRegisters.joinToString { "v$it" }} of $registerCount",
                )
            }
            // The identity guard keeps every register it touches inside the byte range.
            if (pageRegister > 255) {
                throw PatchException(
                    "NewX For You page register is outside the identity guard's range: v$pageRegister",
                )
            }
            val additionalRegisters = scratchRegisters
            val forYouPageComparisonRegister = additionalRegisters[0]
            val forYouSingletonRegister = additionalRegisters[1]
            val sheetResultRegister = forYouPageComparisonRegister
            val singletonField = tabHook.forYouSingletonField
            if (forYouSingletonRegister != forYouPageComparisonRegister + 1) {
                throw PatchException(
                    "NewX For You identity registers must be contiguous for invoke-range: " +
                        "page=v$forYouPageComparisonRegister, singleton=v$forYouSingletonRegister",
                )
            }
            // The old external label pointed at the original instruction at the insertion index, so
            // `Original` is the fall-through target: a path that reached the tab handler without the
            // sheet keeps skipping it, and branches into the handler stay on the original code.
            val receiverRegister = tabHook.method.p0Register
            tabHook.method.insertHook(
                index = hookEnabledRead.nextIndex,
                relocateBranchTargets = false,
            ) {
                ifEqz(hookEnabledRead.register, Target.Original)
                move(forYouPageComparisonRegister, pageRegister, OBJECT_DESCRIPTOR)
                sget(forYouSingletonRegister, singletonField)
                invokeStatic(
                    methodReference(INTRINSICS_ARE_EQUAL_DESCRIPTOR),
                    forYouPageComparisonRegister,
                    forYouSingletonRegister,
                )
                moveResult(forYouPageComparisonRegister, "Z")
                ifEqz(forYouPageComparisonRegister, Target.Original)
                invokeStatic(
                    methodReference(FOR_YOU_TOPIC_FILTER_SHOW_SHEET_DESCRIPTOR),
                    receiverRegister,
                )
                moveResult(sheetResultRegister, "Z")
                ifEqz(sheetResultRegister, Target.Original)
                returnVoid()
            }

            val matches = requestTarget.requestFingerprint.scopedMatchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX For You topic request builder, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            val match = matches.single()
            val method = match.method
            val constructorIndex = match.instructionMatches.last().index
            val constructorInstruction = method.instructions[constructorIndex]
            val constructorReference =
                constructorInstruction.getReference<MethodReference>()
                    ?: throw PatchException(
                        "NewX For You topic request constructor reference is missing in $method",
                    )
            if (!constructorReference.matches(requestTarget.queryConstructor)) {
                throw PatchException(
                    "NewX For You topic request constructor changed: $constructorReference",
                )
            }

            val topicRegister = constructorInstruction.topicArgumentRegister(
                requestTarget.queryConstructor,
                requestTarget.topicParameterIndex,
            )
            // The old insertion left incoming labels on the constructor call.
            method.insertHook(
                index = constructorIndex,
                relocateBranchTargets = false,
            ) {
                invokeStatic(methodReference(FOR_YOU_TOPIC_FILTER_RESOLVE_TOPIC_IDS_DESCRIPTOR), topicRegister)
                moveResult(topicRegister, LIST_DESCRIPTOR)
            }
        }
    }

/**
 * The classes whose toString() has a string matching [value]. A class has at most one toString().
 */
private fun toStringClasses(
    value: String,
    comparison: StringComparisonType,
): List<com.android.tools.smali.dexlib2.iface.ClassDef> =
    Fingerprint(
        name = "toString",
        returnType = STRING_DESCRIPTOR,
        parameters = emptyList(),
        filters = listOf(string(value, comparison)),
    ).matchAllOrNull().orEmpty().map { match -> match.originalClassDef }

private fun resolveForYouTabHook(
    currentPageRefreshEvent: ResolvedTimelineEvent,
    forYouPageTarget: ResolvedForYouPageTarget,
    reselectedEventType: String?,
): ResolvedForYouTabHook {
    val candidates = mutableListOf<ResolvedForYouTabHook>()
    // The hook branches on an instance-of check of the reselected event.
    val eventCheckMethods = reselectedEventType?.let { eventType ->
        Fingerprint(
            returnType = "V",
            filters = listOf(instanceOf(eventType)),
        ).matchAllOrNull()
    }.orEmpty()
    eventCheckMethods.forEach { match ->
        val originalMethod = match.originalMethod
        if (originalMethod.parameterTypes.size == 1) {
            val modernHookCandidate = reselectedEventType?.let { eventType ->
                originalMethod.resolveModernForYouTabHook(forYouPageTarget, eventType)
            }
            if (modernHookCandidate != null) {
                val mutableClass = mutableClassDefBy(match.originalClassDef.type)
                val mutableMethod = mutableClass.methods.singleOrNull { method ->
                    method.name == originalMethod.name &&
                        method.returnType == originalMethod.returnType &&
                        method.parameterTypes == originalMethod.parameterTypes
                } ?: throw PatchException(
                    "NewX modern For You tab event hook is not mutable: $originalMethod",
                )
                val refreshBridge = requireExactlyOne(
                    "NewX For You refresh controller for $originalMethod",
                    mutableClass.methods.flatMap { method ->
                        method.resolveProvenForYouCurrentPageRefreshBridges(currentPageRefreshEvent.dispatchParameterTypes)
                    }.distinct(),
                )
                if (refreshBridge.componentType != modernHookCandidate.componentType) {
                    throw PatchException(
                        "NewX For You refresh bridge page type ${refreshBridge.componentType} " +
                            "differs from the tab hook page type ${modernHookCandidate.componentType}",
                    )
                }
                val originalRegisterCount = mutableMethod.implementation?.registerCount
                    ?: throw PatchException("NewX For You tab event hook has no implementation: $mutableMethod")
                val additionalRegisters = mutableMethod.numberOfParameterRegisters + 4
                val method = mutableMethod.cloneMutable(
                    additionalRegisters = additionalRegisters,
                ).also { expandedMethod ->
                    mutableClass.methods.remove(mutableMethod)
                    mutableClass.methods.add(expandedMethod)
                }
                candidates += ResolvedForYouTabHook(
                    method = method,
                    insertionIndex = modernHookCandidate.insertionIndex +
                        mutableMethod.numberOfParameterRegistersLogical,
                    refreshBridge = refreshBridge,
                    forYouPageRegister = modernHookCandidate.pageRegister,
                    forYouSingletonField = forYouPageTarget.singletonField,
                    reservedRegisterBase = originalRegisterCount,
                )
                return@forEach
            }
        }
    }
    if (candidates.size != 1) {
        throw PatchException(
            "Expected one NewX For You tab event hook, found ${candidates.size}: " +
                candidates.joinToString { "${it.method.definingClass}->${it.method.name}${it.method.parameterTypes}" },
        )
    }
    return candidates.single()
}

private fun resolveForYouReselectedEventType(): String? {
    val candidates =
        toStringClasses("OnTabReselected(tabIndex=", StringComparisonType.CONTAINS).map { it.type }
    if (candidates.size > 1) {
        throw PatchException(
            "Expected one NewX OnTabReselected event class, found ${candidates.size}: " +
                candidates.joinToString(),
        )
    }
    return candidates.singleOrNull()
}

private fun resolveForYouPageTarget(): ResolvedForYouPageTarget {
    val candidates = toStringClasses("ForYou", StringComparisonType.EQUALS).flatMap { classDef ->
        classDef.fields.filter { field ->
            AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type == classDef.type
        }.map { field -> classDef to field }
    }
    if (candidates.size != 1) {
        throw PatchException(
            "Expected one NewX For You page singleton, found ${candidates.size}: " +
                candidates.joinToString { (classDef, field) -> "${classDef.type}->$field" },
        )
    }

    val (forYouClass, singletonField) = candidates.single()
    val modelTypes = linkedSetOf<String>()
    fun addType(type: String?) {
        if (type == null || type == OBJECT_DESCRIPTOR || !modelTypes.add(type)) return
        val definition = classDefByOrNull(type) ?: return
        addType(definition.superclass)
        definition.interfaces.forEach { interfaceType -> addType(interfaceType.toString()) }
    }
    addType(forYouClass.type)
    return ResolvedForYouPageTarget(
        singletonField = singletonField,
        modelTypes = modelTypes,
    )
}

private fun Method.resolveModernForYouTabHook(
    forYouPageTarget: ResolvedForYouPageTarget,
    reselectedEventType: String,
): ResolvedModernForYouTabHook? {
    val instructions = implementation?.instructions?.toList() ?: return null
    val eventBranches = instructions.withIndex()
        .mapNotNull { (instanceOfIndex, instanceOfInstruction) ->
            if (instanceOfInstruction.opcode != Opcode.INSTANCE_OF) {
                return@mapNotNull null
            }
            val instanceOf = instanceOfInstruction as? TwoRegisterInstruction
                ?: return@mapNotNull null
            val eventType = instanceOfInstruction.getReference<TypeReference>()?.type
                ?: return@mapNotNull null
            if (eventType != reselectedEventType) return@mapNotNull null
            val branchInstruction = instructions.getOrNull(instanceOfIndex + 1)
                ?: return@mapNotNull null
            if (branchInstruction.opcode != Opcode.IF_EQZ ||
                (branchInstruction as? OneRegisterInstruction)?.registerA != instanceOf.registerA
            ) {
                return@mapNotNull null
            }
            val branchIndex = instanceOfIndex + 1
            val eventCastInstruction = instructions.getOrNull(branchIndex + 1)
                ?: return@mapNotNull null
            if (eventCastInstruction.opcode != Opcode.CHECK_CAST ||
                eventCastInstruction.getReference<TypeReference>()?.type != eventType
            ) {
                return@mapNotNull null
            }
            val eventCastIndex = branchIndex + 1
            Triple(instanceOfIndex, branchIndex, eventCastIndex)
        }
    if (eventBranches.isEmpty()) return null
    if (eventBranches.size != 1) {
        throw PatchException(
            "Expected one NewX modern For You reselection event branch, found " +
                "${eventBranches.size}: ${eventBranches.joinToString()}",
        )
    }
    val eventBranch = eventBranches.single()
    val hookCandidates = mutableListOf<ResolvedModernForYouTabHook>()
    for ((pageLookupIndex, pageLookupInstruction) in instructions.withIndex()
        .drop(eventBranch.third + 1)) {
        val pageLookup = pageLookupInstruction.getReference<MethodReference>()
            ?: continue
        if (pageLookupInstruction.opcode != Opcode.INVOKE_STATIC ||
            pageLookup.name != "getOrNull" ||
            pageLookup.returnType != OBJECT_DESCRIPTOR ||
            pageLookup.parameterTypes.map(CharSequence::toString) !=
                listOf(OBJECT_LIST_DESCRIPTOR, INTEGER_DESCRIPTOR)
        ) {
            continue
        }

        val resultIndex = pageLookupIndex + 1
        if (resultIndex >= instructions.size) continue
        val componentCastIndex = resultIndex + 1
        if (componentCastIndex >= instructions.size ||
            instructions[resultIndex].opcode != Opcode.MOVE_RESULT_OBJECT ||
            instructions[componentCastIndex].opcode != Opcode.CHECK_CAST
        ) {
            continue
        }
        val componentCast = instructions[componentCastIndex] as? OneRegisterInstruction
            ?: continue
        val componentType = instructions[componentCastIndex]
            .getReference<TypeReference>()?.type
            ?: continue

        for ((modelGetterIndex, modelGetterInstruction) in instructions.withIndex()
            .drop(componentCastIndex + 1)) {
            val modelGetter = modelGetterInstruction.getReference<MethodReference>()
                ?: continue
            if (modelGetterInstruction.opcode != Opcode.INVOKE_VIRTUAL ||
                modelGetter.definingClass != componentType ||
                modelGetter.returnType != OBJECT_DESCRIPTOR ||
                modelGetter.parameterTypes.isNotEmpty() ||
                modelGetterInstruction.registersUsed != listOf(componentCast.registerA)
            ) {
                continue
            }

            val modelResultIndex = modelGetterIndex + 1
            val modelCastIndex = modelResultIndex + 1
            if (modelCastIndex >= instructions.size ||
                instructions[modelResultIndex].opcode != Opcode.MOVE_RESULT_OBJECT ||
                instructions[modelCastIndex].opcode != Opcode.CHECK_CAST
            ) {
                continue
            }
            val modelCast = instructions[modelCastIndex] as? OneRegisterInstruction
                ?: continue
            val modelType = instructions[modelCastIndex]
                .getReference<TypeReference>()?.type
                ?: continue
            val modelFieldIndex = modelCastIndex + 1
            if (modelFieldIndex >= instructions.size ||
                instructions[modelFieldIndex].opcode != Opcode.IGET_OBJECT
            ) {
                continue
            }
            val modelFieldInstruction = instructions[modelFieldIndex] as? TwoRegisterInstruction
                ?: continue
            val modelField = instructions[modelFieldIndex].getReference<FieldReference>()
                ?: continue
            if (modelFieldInstruction.registerB != modelCast.registerA ||
                modelField.definingClass != modelType ||
                modelField.type !in forYouPageTarget.modelTypes
            ) {
                continue
            }

            hookCandidates += ResolvedModernForYouTabHook(
                insertionIndex = modelFieldIndex + 1,
                pageRegister = modelFieldInstruction.registerA,
                componentType = componentType,
            )
        }
    }
    if (hookCandidates.size != 1) {
        throw PatchException(
            "Expected one NewX modern For You tab hook after the reselection event, found " +
                "${hookCandidates.size}: ${hookCandidates.joinToString()}",
        )
    }
    return hookCandidates.single()
}

private fun installForYouRefreshBridge(
    tabHook: ResolvedForYouTabHook,
    clearAndRefreshEvent: ResolvedTimelineEvent,
    scrollToTopEvent: ResolvedTimelineEvent,
) {
    val classDef = mutableClassDefBy(tabHook.method.definingClass)
    if (!classDef.interfaces.contains(FOR_YOU_REFRESH_TARGET_DESCRIPTOR)) {
        classDef.interfaces.add(FOR_YOU_REFRESH_TARGET_DESCRIPTOR)
    }
    if (classDef.methods.any { method ->
        method.name == FOR_YOU_REFRESH_BRIDGE_NAME &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "V"
    }) {
        throw PatchException(
            "NewX For You tab handler already has refresh bridge $FOR_YOU_REFRESH_BRIDGE_NAME",
        )
    }

    // A typed hook needs an existing implementation, so the bridge starts as an empty four-register
    // body (v0-v2 locals, p0 the tab handler) that the typed block below fills in.
    val implementation = MutableMethodImplementation(4)
    val bridgeMethod = MutableMethod(
        ImmutableMethod(
            classDef.type,
            FOR_YOU_REFRESH_BRIDGE_NAME,
            emptyList(),
            "V",
            AccessFlags.PUBLIC.value,
            emptySet(),
            emptySet(),
            implementation,
        ),
    )
    classDef.methods.add(bridgeMethod)

    bridgeMethod.insertHook(0, relocateBranchTargets = false) {
        emitForYouCurrentPageRefresh(
            bridge = tabHook.refreshBridge,
            receiverRegister = bridgeMethod.p0Register,
            clearAndRefreshField = clearAndRefreshEvent.field,
            scrollToTopField = scrollToTopEvent.field,
        )
    }
}

private fun resolveSingletonTimelineEvent(
    eventLabel: String,
): ResolvedTimelineEvent {
    val eventClasses = toStringClasses(eventLabel, StringComparisonType.CONTAINS)
    if (eventClasses.size != 1) {
        throw PatchException(
            "Expected one NewX $eventLabel event class, found " +
                "${eventClasses.size}: ${eventClasses.joinToString { it.type }}",
        )
    }

    val eventClass = eventClasses.single()
    val fields = eventClass.fields.filter { field ->
        AccessFlags.STATIC.isSet(field.accessFlags) &&
            field.type == eventClass.type
    }
    if (fields.size != 1) {
        throw PatchException(
            "Expected one NewX $eventLabel singleton field in ${eventClass.type}, " +
                "found ${fields.size}: ${fields.joinToString()}",
        )
    }
    val dispatchParameterTypes = eventClass.interfaces.map(CharSequence::toString).toSet()
    if (dispatchParameterTypes.isEmpty()) {
        throw PatchException(
            "NewX $eventLabel event class ${eventClass.type} has no dispatch interface",
        )
    }
    return ResolvedTimelineEvent(fields.single(), dispatchParameterTypes)
}

/** One read of the tab handler's pages state: `this.F` → `F.getter()` → `check-cast Pages` → `Pages.field`. */
private data class PagesStateRead(
    val stateField: FieldReference,
    val stateGetter: MethodReference,
    val pagesType: String,
    val pagesField: FieldReference,
)

/**
 * The pages-state read at [readIndex], when its four instructions are adjacent and the state holder
 * is proven to be a field of this handler read from the receiver.
 */
private fun Method.pagesStateReadAt(
    code: List<Instruction>,
    readIndex: Int,
    readOpcode: Opcode,
    fieldType: String,
): PagesStateRead? {
    if (readIndex < 3) return null
    val read = code[readIndex]
    if (read.opcode != readOpcode) return null
    val pagesField = read.getReference<FieldReference>()?.takeIf { it.type == fieldType } ?: return null
    val pagesRegister = (read as? TwoRegisterInstruction)?.registerB ?: return null
    val cast = code[readIndex - 1]
    if (cast.opcode != Opcode.CHECK_CAST ||
        cast.getReference<TypeReference>()?.type != pagesField.definingClass ||
        (cast as? OneRegisterInstruction)?.registerA != pagesRegister
    ) {
        return null
    }
    val result = code[readIndex - 2]
    if (result.opcode != Opcode.MOVE_RESULT_OBJECT || (result as? OneRegisterInstruction)?.registerA != pagesRegister) {
        return null
    }
    val getterIndex = readIndex - 3
    val getterCall = code[getterIndex] as? FiveRegisterInstruction ?: return null
    val getter = code[getterIndex].getReference<MethodReference>() ?: return null
    if (code[getterIndex].opcode != Opcode.INVOKE_VIRTUAL ||
        getterCall.registerCount != 1 ||
        getter.returnType != OBJECT_DESCRIPTOR ||
        getter.parameterTypes.isNotEmpty()
    ) {
        return null
    }
    val stateReads = code.withIndex().filter { (index, instruction) ->
        instruction.opcode == Opcode.IGET_OBJECT &&
            instruction.getReference<FieldReference>()?.let { field ->
                field.definingClass == definingClass && field.type == getter.definingClass
            } == true &&
            provesValueFromInstruction(
                index,
                (instruction as TwoRegisterInstruction).registerA,
                getterIndex,
                getterCall.registerC,
            )
    }
    val stateRead = requireAtMostOne("NewX For You pages state read in $this", stateReads) ?: return null
    if (!provesParameterValue(stateRead.index, (stateRead.value as TwoRegisterInstruction).registerB, p0Register)) {
        return null
    }
    return PagesStateRead(
        stateField = stateRead.value.getReference<FieldReference>() ?: return null,
        stateGetter = getter,
        pagesType = pagesField.definingClass,
        pagesField = pagesField,
    )
}

/**
 * Bridges proven by data flow in any method of the tab handler: the active page is looked up from the
 * pages state's own list and index, its instance is narrowed with Kotlin's `as?` to a timeline page,
 * and that page's controller receives a ClearAndRefreshTimeline-compatible event.
 *
 * 12.45 smali (`g(Lcom/x/home/o0;)V`, title-tap branch):
 * ```
 * iget-object v2, v0, Tab;->X:State          # v0 = p0
 * invoke-virtual {v2}, State;->a()Object ; move-result-object v1 ; check-cast v1, Pages ; iget-object v1, v1, Pages;->a:List
 * invoke-virtual {v2}, State;->a()Object ; move-result-object v2 ; check-cast v2, Pages ; iget v2, v2, Pages;->b:I
 * invoke-static {v1, v2}, CollectionsKt;->getOrNull(List;I)Object ; move-result-object v1 ; check-cast v1, Child
 * if-eqz v1 ; invoke-virtual {v1}, Child;->b()Object ; move-result-object v1 ; check-cast v1, PageBase
 * instance-of v2, v1, Page ; if-eqz v2 ; check-cast v1, Page ; ... iget-object v1, v1, Page;->a:Controller
 * ... invoke-interface {v1, v3}, Controller;->k(Event)V
 * ```
 * The `as?` joins merge the cast value with null, so the instance, controller and dispatch links are
 * proven on the paths leaving their source.
 */
internal fun Method.resolveProvenForYouCurrentPageRefreshBridges(
    dispatchParameterTypes: Set<String>,
): List<ResolvedForYouCurrentPageRefreshBridge> {
    if (AccessFlags.STATIC.isSet(accessFlags)) return emptyList()
    val code = implementation?.instructions?.toList() ?: return emptyList()
    val bridges = mutableListOf<ResolvedForYouCurrentPageRefreshBridge>()
    for ((lookupIndex, lookup) in code.withIndex()) {
        val lookupReference = lookup.getReference<MethodReference>()?.toString() ?: continue
        val isPageLookup =
            lookup.opcode == Opcode.INVOKE_INTERFACE && lookupReference == LIST_GET_DESCRIPTOR ||
                lookup.opcode == Opcode.INVOKE_STATIC && lookupReference == COLLECTIONS_GET_OR_NULL_DESCRIPTOR
        if (!isPageLookup) continue
        val lookupCall = lookup as? FiveRegisterInstruction ?: continue
        if (lookupCall.registerCount != 2) continue

        fun pagesReadFor(readOpcode: Opcode, fieldType: String, argumentRegister: Int): PagesStateRead? =
            code.indices.filter { index ->
                code[index].opcode == readOpcode &&
                    code[index].getReference<FieldReference>()?.type == fieldType &&
                    provesValueFromInstruction(
                        index,
                        (code[index] as TwoRegisterInstruction).registerA,
                        lookupIndex,
                        argumentRegister,
                    )
            }.mapNotNull { index -> pagesStateReadAt(code, index, readOpcode, fieldType) }
                .distinct()
                .singleOrNull()

        val listRead = pagesReadFor(Opcode.IGET_OBJECT, OBJECT_LIST_DESCRIPTOR, lookupCall.registerC) ?: continue
        val indexRead = pagesReadFor(Opcode.IGET, INTEGER_DESCRIPTOR, lookupCall.registerD) ?: continue
        if (listRead.stateField.toString() != indexRead.stateField.toString() ||
            listRead.stateGetter.toString() != indexRead.stateGetter.toString() ||
            listRead.pagesType != indexRead.pagesType
        ) {
            continue
        }

        val resultIndex = lookupIndex + 1
        val componentCastIndex = lookupIndex + 2
        if (componentCastIndex >= code.size || code[resultIndex].opcode != Opcode.MOVE_RESULT_OBJECT ||
            code[componentCastIndex].opcode != Opcode.CHECK_CAST
        ) {
            continue
        }
        val componentRegister = (code[componentCastIndex] as OneRegisterInstruction).registerA
        if ((code[resultIndex] as OneRegisterInstruction).registerA != componentRegister) continue
        val componentType = code[componentCastIndex].getReference<TypeReference>()?.type ?: continue

        for (getterIndex in componentCastIndex + 1 until code.size - 1) {
            val getterCall = code[getterIndex] as? FiveRegisterInstruction ?: continue
            val componentGetter = code[getterIndex].getReference<MethodReference>() ?: continue
            if (code[getterIndex].opcode != Opcode.INVOKE_VIRTUAL ||
                componentGetter.definingClass != componentType ||
                componentGetter.returnType != OBJECT_DESCRIPTOR ||
                componentGetter.parameterTypes.isNotEmpty() ||
                getterCall.registerCount != 1 ||
                code[getterIndex + 1].opcode != Opcode.MOVE_RESULT_OBJECT ||
                !provesValueFromInstruction(componentCastIndex, componentRegister, getterIndex, getterCall.registerC)
            ) {
                continue
            }
            val instanceIndex = getterIndex + 1
            val instanceRegister = (code[instanceIndex] as OneRegisterInstruction).registerA
            bridges += resolveProvenForYouControllerDispatches(
                code = code,
                instanceIndex = instanceIndex,
                instanceRegister = instanceRegister,
                dispatchParameterTypes = dispatchParameterTypes,
            ).map { (forYouComponentType, controllerField, refreshDispatch) ->
                ResolvedForYouCurrentPageRefreshBridge(
                    stateField = listRead.stateField,
                    stateGetter = listRead.stateGetter,
                    pagesType = listRead.pagesType,
                    pagesListField = listRead.pagesField,
                    pagesIndexField = indexRead.pagesField,
                    componentType = componentType,
                    componentGetter = componentGetter,
                    forYouComponentType = forYouComponentType,
                    forYouControllerField = controllerField,
                    refreshDispatch = refreshDispatch,
                )
            }
        }
    }
    return bridges
}

/**
 * From the page instance produced at [instanceIndex]: `instance-of t, o, Page; if-eqz t; check-cast o, Page`,
 * a read of a `Page` controller field from that cast, and a one-event interface dispatch on the controller.
 */
private fun Method.resolveProvenForYouControllerDispatches(
    code: List<Instruction>,
    instanceIndex: Int,
    instanceRegister: Int,
    dispatchParameterTypes: Set<String>,
): List<Triple<String, FieldReference, MethodReference>> {
    val dispatches = mutableListOf<Triple<String, FieldReference, MethodReference>>()
    for (instanceOfIndex in instanceIndex + 1 until code.size - 2) {
        val instanceOf = code[instanceOfIndex] as? TwoRegisterInstruction ?: continue
        if (code[instanceOfIndex].opcode != Opcode.INSTANCE_OF) continue
        val pageType = code[instanceOfIndex].getReference<TypeReference>()?.type ?: continue
        val branch = code[instanceOfIndex + 1]
        val cast = code[instanceOfIndex + 2]
        if (branch.opcode != Opcode.IF_EQZ ||
            (branch as OneRegisterInstruction).registerA != instanceOf.registerA ||
            cast.opcode != Opcode.CHECK_CAST ||
            (cast as OneRegisterInstruction).registerA != instanceOf.registerB ||
            cast.getReference<TypeReference>()?.type != pageType ||
            !provesValueFromInstructionOnSourcePaths(instanceIndex, instanceRegister, instanceOfIndex, instanceOf.registerB)
        ) {
            continue
        }
        val castIndex = instanceOfIndex + 2
        for ((controllerReadIndex, controllerRead) in code.withIndex().drop(castIndex + 1)) {
            if (controllerRead.opcode != Opcode.IGET_OBJECT) continue
            val controllerField = controllerRead.getReference<FieldReference>() ?: continue
            if (controllerField.definingClass != pageType) continue
            val read = controllerRead as TwoRegisterInstruction
            if (!provesValueFromInstructionOnSourcePaths(castIndex, instanceOf.registerB, controllerReadIndex, read.registerB)) {
                continue
            }
            for ((dispatchIndex, dispatch) in code.withIndex().drop(controllerReadIndex + 1)) {
                if (dispatch.opcode != Opcode.INVOKE_INTERFACE) continue
                val dispatchCall = dispatch as? FiveRegisterInstruction ?: continue
                val refreshDispatch = dispatch.getReference<MethodReference>() ?: continue
                if (refreshDispatch.definingClass != controllerField.type ||
                    refreshDispatch.returnType != "V" ||
                    refreshDispatch.parameterTypes.size != 1 ||
                    refreshDispatch.parameterTypes.single().toString() !in dispatchParameterTypes ||
                    dispatchCall.registerCount != 2 ||
                    !provesValueFromInstructionOnSourcePaths(
                        controllerReadIndex,
                        read.registerA,
                        dispatchIndex,
                        dispatchCall.registerC,
                    )
                ) {
                    continue
                }
                dispatches += Triple(pageType, controllerField, refreshDispatch)
            }
        }
    }
    return dispatches
}

/**
 * Re-dispatches a refresh of the For You page that is currently shown.
 *
 * The bridge method is created with four registers: v0-v2 are its locals and [receiverRegister] is
 * the receiver (the tab handler that owns the live page list). The done label keeps the block
 * falling through to `return-void` when the shown page is not a For You page or has no controller.
 */
private fun Block.emitForYouCurrentPageRefresh(
    bridge: ResolvedForYouCurrentPageRefreshBridge,
    receiverRegister: Int,
    clearAndRefreshField: FieldReference,
    scrollToTopField: FieldReference,
) {
    val doneLabel = "piko_for_you_refresh_current_page_done"
    iget(0, receiverRegister, bridge.stateField)
    invokeVirtual(bridge.stateGetter, 0)
    moveResult(1, bridge.pagesType)
    checkCast(1, bridge.pagesType)
    iget(1, 1, bridge.pagesListField)
    invokeVirtual(bridge.stateGetter, 0)
    moveResult(0, bridge.pagesType)
    checkCast(0, bridge.pagesType)
    iget(0, 0, bridge.pagesIndexField)
    // 12.45 looks the page up with getOrNull, so an index outside the list leaves the bridge idle.
    ifLtz(0, Target.Local(doneLabel))
    invokeInterface(methodReference(LIST_SIZE_DESCRIPTOR), 1)
    moveResult(2, INTEGER_DESCRIPTOR)
    ifGe(0, 2, Target.Local(doneLabel))
    invokeInterface(methodReference(LIST_GET_DESCRIPTOR), 1, 0)
    moveResult(0, bridge.componentType)
    checkCast(0, bridge.componentType)
    invokeVirtual(bridge.componentGetter, 0)
    moveResult(0, OBJECT_DESCRIPTOR)
    instanceOf(1, 0, bridge.forYouComponentType)
    ifEqz(1, Target.Local(doneLabel))
    checkCast(0, bridge.forYouComponentType)
    iget(1, 0, bridge.forYouControllerField)
    ifEqz(1, Target.Local(doneLabel))
    invokeStatic(methodReference(FOR_YOU_REFRESH_GATE_MARK_DESCRIPTOR))
    sget(0, clearAndRefreshField)
    invokeInterface(bridge.refreshDispatch, 1, 0)
    sget(0, scrollToTopField)
    invokeInterface(bridge.refreshDispatch, 1, 0)
    label(doneLabel)
    returnVoid()
}

private fun patchHomeFilterGroupConstructor() {
    val matches = HomeFilterGroupFingerprint.scopedMatchAll()
    if (matches.size != 1) {
        throw PatchException(
            "Expected one NewX HomeTimelineFilters group model, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
    }

    val match = matches.single()
    val filterTypeField = match.fieldForToStringLabel(HOME_FILTER_GROUP_FILTER_TYPE_LABEL)
    val optionsField = match.fieldForToStringLabel(HOME_FILTER_GROUP_OPTIONS_LABEL)
    val parameterTypes = { method: Method -> method.parameterTypes.map(CharSequence::toString) }
    val constructors = match.classDef.methods.filter { method ->
        val parameters = parameterTypes(method)
        method.name == "<init>" &&
            method.returnType == "V" &&
            parameters.size == 4 &&
            parameters[0].startsWith(HOME_MODELS_PACKAGE) &&
            parameters[1] == STRING_DESCRIPTOR &&
            parameters[2] == "Z" &&
            parameters[3] in listOf(LIST_DESCRIPTOR, ARRAY_LIST_DESCRIPTOR)
    }
    if (constructors.size != 1) {
        throw PatchException(
            "Expected one NewX HomeTimelineFilters group constructor, found " +
                "${constructors.size}: ${constructors.joinToString()}",
        )
    }

    val constructor = constructors.single()
    val filterTypeParameterIndex = constructor.parameterIndexForField(filterTypeField)
    val optionsParameterIndex = constructor.parameterIndexForField(
        optionsField,
        setOf(LIST_DESCRIPTOR, ARRAY_LIST_DESCRIPTOR),
    )
    if (filterTypeParameterIndex != 0 || optionsParameterIndex != 3) {
        throw PatchException(
            "NewX HomeFilterGroup constructor parameter mapping changed: " +
                "filterType=$filterTypeParameterIndex, options=$optionsParameterIndex",
        )
    }

    val superCandidates = constructor.instructions.withIndex().filter { (_, instruction) ->
        if (instruction.opcode != Opcode.INVOKE_DIRECT) return@filter false
        val reference = instruction.getReference<MethodReference>() ?: return@filter false
        reference.name == "<init>" && reference.definingClass != constructor.definingClass
    }
    if (superCandidates.size != 1) {
        throw PatchException(
            "Expected one NewX HomeTimelineFilters group super call, found " +
                "${superCandidates.size}: ${superCandidates.joinToString()}",
        )
    }
    val superIndex = superCandidates.single().index

    // `p1` is the filter type and `p4` the options list; both parameters are single-word on this
    // constructor, so the slots are plain receiver offsets. The old insertion left incoming labels
    // on the instruction after the super call.
    val receiverRegister = constructor.p0Register
    constructor.insertHook(
        index = superIndex + 1,
        relocateBranchTargets = false,
    ) {
        invokeStatic(
            methodReference(FOR_YOU_TOPIC_FILTER_CAPTURE_OPTIONS_DESCRIPTOR),
            receiverRegister + 1,
            receiverRegister + 4,
        )
    }
}

private fun resolveForYouRequestTarget(): ResolvedForYouRequestTarget {
    val queryMatches = HomeTimelineQueryFingerprint.scopedMatchAll()
    if (queryMatches.size != 1) {
        throw PatchException(
            "Expected one NewX HomeTimeline query class, found ${queryMatches.size}: " +
                queryMatches.joinToString { it.originalClassDef.type },
        )
    }

    val queryClass = queryMatches.single().originalClassDef
    val queryDocuments = queryClass.methods.filter { method ->
        method.returnType == STRING_DESCRIPTOR && method.containsStringFragment("query HomeTimeline(")
    }
    val topicWriters = queryClass.methods.filter { method ->
        method.returnType == "V" && method.containsStringFragment("topic_ids")
    }
    if (queryDocuments.size != 1 ||
        !queryDocuments.single().containsStringFragment("home_timeline_urt") ||
        !queryDocuments.single().containsStringFragment("topic_ids:") ||
        topicWriters.size != 1) {
        throw PatchException(
            "NewX HomeTimeline query does not serialize the expected topic_ids argument: " +
                "documents=${queryDocuments.joinToString { it.toString() }}, " +
                "writers=${topicWriters.joinToString { it.toString() }}",
        )
    }
    val topicField = topicWriters.single().fieldForSerializedArgument(
        argumentName = "topic_ids",
        ownerDescriptor = queryClass.type,
        expectedFieldType = LIST_DESCRIPTOR,
    )
    val constructorCandidates =
        queryClass.methods.mapNotNull { constructor ->
            if (constructor.name != "<init>" || constructor.returnType != "V") return@mapNotNull null
            val topicParameterIndex = constructor.parameterIndexForField(
                topicField,
                setOf(LIST_DESCRIPTOR),
            ) ?: return@mapNotNull null
            constructor to topicParameterIndex
        }

    if (constructorCandidates.size != 1) {
        throw PatchException(
            "Expected one NewX HomeTimeline constructor carrying topic_ids, found " +
                "${constructorCandidates.size}: ${constructorCandidates.joinToString { it.first.toString() }}",
        )
    }

    val (constructor, topicParameterIndex) = constructorCandidates.single()
    val constructorParameters = constructor.parameterTypes.map(CharSequence::toString)
    // 12.27/12.28 build the For You request in a (Object,Object)→Object lambda; 12.29 R8-moved
    // the body into a typed repository method returning the query class. In later builds R8
    // moves that method outside the repository package. The enum read and exact query constructor
    // together identify the request without depending on its relocated owner.
    val requestFingerprint =
        Fingerprint(
                filters =
                    listOf(
                        fieldAccess(
                            opcode = Opcode.SGET_OBJECT,
                            name = "FOR_YOU",
                    ),
                    methodCall(
                        definingClass = queryClass.type,
                        name = "<init>",
                        parameters = constructorParameters,
                        returnType = "V",
                    ),
                ),
        )
    return ResolvedForYouRequestTarget(
        requestFingerprint = requestFingerprint,
        queryConstructor = constructor,
        topicParameterIndex = topicParameterIndex,
    )
}

private fun Method.parameterIndexForField(
    field: FieldReference,
    expectedParameterTypes: Set<String> = emptySet(),
): Int? {
    val implementation = implementation ?: return null
    val instructions = implementation.instructions.toList()
    val thisRegister = implementation.registerCount -
        parameterTypes.sumOf { type -> type.toString().registerWidth() } - 1
    val writes =
        instructions.withIndex().filter { (_, instruction) ->
            instruction.opcode == Opcode.IPUT_OBJECT &&
                instruction.getReference<FieldReference>()?.toString() == field.toString()
        }
    val write = requireAtMostOne(
        label = "NewX parameter field write for $field in $this",
        candidates = writes,
    ) ?: return null
    val writeInstruction = write.value as? TwoRegisterInstruction ?: return null
    val sourceRegisters = if (writeInstruction.registerB == thisRegister) {
        listOf(
            writeInstruction.registerA,
            instructions.resolveObjectOriginRegister(write.index, writeInstruction.registerA),
        )
    } else {
        listOf(instructions.resolveObjectOriginRegister(write.index, writeInstruction.registerA))
    }
    val parameterCandidates = sourceRegisters.asSequence()
        .mapNotNull { sourceRegister ->
            parameterTypes.indexOfRegister(sourceRegister, thisRegister)
        }
        .distinct()
        .filter { parameterIndex ->
            expectedParameterTypes.isEmpty() ||
                parameterTypes[parameterIndex].toString() in expectedParameterTypes
        }
        .toList()
    return requireAtMostOne(
        label = "NewX parameter index candidates for field $field",
        candidates = parameterCandidates,
    )
}

private fun List<CharSequence>.indexOfRegister(
    register: Int,
    receiverRegister: Int,
): Int? {
    var parameterRegister = receiverRegister + 1
    val parameterIndex = indexOfFirst { parameterType ->
        val matches = parameterRegister == register
        parameterRegister += parameterType.toString().registerWidth()
        matches
    }
    return parameterIndex.takeIf { it >= 0 }
}

private val MOVE_OBJECT_OPCODES =
    setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)

private fun List<Instruction>.resolveObjectOriginRegister(
    writeIndex: Int,
    destinationRegister: Int,
): Int {
    var register = destinationRegister
    var searchEnd = writeIndex
    val visitedRegisters = mutableSetOf<Int>()
    while (visitedRegisters.add(register)) {
        val move =
            (searchEnd - 1 downTo 0).firstOrNull { index ->
                val instruction = this[index]
                if (instruction.opcode !in MOVE_OBJECT_OPCODES) return@firstOrNull false
                val moveInstruction = instruction as? TwoRegisterInstruction ?: return@firstOrNull false
                moveInstruction.registerA == register
            } ?: return register
        val moveInstruction = this[move] as TwoRegisterInstruction
        register = moveInstruction.registerB
        searchEnd = move
    }
    return register
}

private fun Instruction.topicArgumentRegister(
    constructor: Method,
    topicParameterIndex: Int,
): Int {
    val argumentRegisters = registersUsed
    val parameterTypes = constructor.parameterTypes.map(CharSequence::toString)
    val expectedRegisterCount = 1 + parameterTypes.sumOf { type -> type.registerWidth() }
    if (argumentRegisters.size != expectedRegisterCount) {
        throw PatchException(
            "Unexpected NewX HomeTimeline constructor register span: " +
                "expected $expectedRegisterCount, found ${argumentRegisters.size}",
        )
    }
    val argumentOffset =
        1 + parameterTypes
            .take(topicParameterIndex)
            .sumOf { type -> type.registerWidth() }
    return argumentRegisters[argumentOffset]
}

private fun String.registerWidth(): Int = if (this == "J" || this == "D") 2 else 1

private fun Method.containsStringFragment(fragment: String): Boolean =
    implementation?.instructions?.any { instruction ->
        instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.StringReference>()
            ?.string
            ?.contains(fragment) == true
    } == true

private fun Method.fieldForSerializedArgument(
    argumentName: String,
    ownerDescriptor: String,
    expectedFieldType: String,
): FieldReference {
    val instructions = implementation?.instructions?.toList().orEmpty()
    val argumentIndices = instructions.withIndex().filter { (_, instruction) ->
        instruction.getReference<StringReference>()?.string == argumentName
    }
    if (argumentIndices.size != 1) {
        throw PatchException(
            "Expected one NewX serialized argument '$argumentName' in $this, found " +
                "${argumentIndices.size}",
        )
    }

    val argumentIndex = argumentIndices.single().index
    val nextArgumentIndex = instructions.withIndex()
        .drop(argumentIndex + 1)
        .firstOrNull { (_, instruction) ->
            instruction.getReference<StringReference>() != null
        }?.index ?: instructions.size
    val fields = instructions.subList(argumentIndex + 1, nextArgumentIndex)
        .mapNotNull { instruction ->
            if (instruction.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
            instruction.getReference<FieldReference>()?.takeIf { field ->
                field.definingClass == ownerDescriptor && field.type == expectedFieldType
            }
        }
        .distinctBy(FieldReference::toString)
    return requireExactlyOne(
        label = "NewX serialized field for '$argumentName' in $this",
        candidates = fields,
    )
}

private fun MethodReference.matches(method: Method): Boolean =
    definingClass == method.definingClass &&
        name == method.name &&
        returnType == method.returnType &&
        parameterTypes.map(CharSequence::toString) == method.parameterTypes.map(CharSequence::toString)
