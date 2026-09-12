package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.misc.extension.newXInitHook
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.customScreen
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.FOR_YOU_TOPIC_FILTER_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.FOR_YOU_TOPIC_FILTER_FRAGMENT_DESCRIPTOR
import app.crimera.patches.newx.utils.requireAtMostOne
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.numberOfParameterRegistersLogical
import app.morphe.util.registersUsed
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.Method
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

private data class ResolvedForYouCurrentPageRefreshBridge(
    val stateField: FieldReference,
    val stateGetter: MethodReference,
    val pagesType: String,
    val pagesListField: FieldReference,
    val pagesIndexField: FieldReference,
    val pageLookup: MethodReference,
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
)

@Suppress("unused")
val newXForYouTopicFilterPatch =
    bytecodePatch(
        name = "NewX: Filter For You by topic",
        description = "Restricts the NewX For You timeline to selected topics.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
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
            val clearAndRefreshEvent = resolveSingletonTimelineEvent(CLEAR_AND_REFRESH_TIMELINE)
            val scrollToTopEvent = resolveSingletonTimelineEvent(REQUEST_SCROLL_TO_TOP)
            val tabHook = resolveForYouTabHook(scrollToTopEvent)
            val requestTarget = resolveForYouRequestTarget()

            newXInitHook.fingerprint.method.addInstruction(
                0,
                "invoke-static/range {p0 .. p0}, $FOR_YOU_TOPIC_FILTER_DESCRIPTOR->initialize(Landroid/content/Context;)V",
            )

            patchHomeFilterGroupConstructor()

            installForYouRefreshBridge(tabHook, clearAndRefreshEvent, scrollToTopEvent)
            val continuation = tabHook.method.instructions[tabHook.insertionIndex]
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
            if (pageRegister > 255) {
                throw PatchException(
                    "NewX For You page register is not encodable in a move-object/from16: " +
                        "v$pageRegister",
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
            val forYouIdentityGuard = """
                move-object/from16 v$forYouPageComparisonRegister, v$pageRegister
                sget-object v$forYouSingletonRegister, ${singletonField.smaliReference()}
                invoke-static/range {v$forYouPageComparisonRegister .. v$forYouSingletonRegister}, $INTRINSICS_DESCRIPTOR->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z
                move-result v$forYouPageComparisonRegister
                if-eqz v$forYouPageComparisonRegister, :piko_newx_for_you_topic_sheet_continue
            """.trimIndent()
            tabHook.method.addInstructionsWithLabels(
                hookEnabledRead.nextIndex,
                """
                    if-eqz v${hookEnabledRead.register}, :piko_newx_for_you_topic_sheet_continue
                    $forYouIdentityGuard
                    invoke-static/range {p0 .. p0}, $FOR_YOU_TOPIC_FILTER_DESCRIPTOR->showForYouTopicSheet($FOR_YOU_REFRESH_TARGET_DESCRIPTOR)Z
                    move-result v$sheetResultRegister
                    if-eqz v$sheetResultRegister, :piko_newx_for_you_topic_sheet_continue
                    return-void
                """.trimIndent(),
                ExternalLabel("piko_newx_for_you_topic_sheet_continue", continuation),
            )

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
            method.addInstructions(
                constructorIndex,
                """
                    invoke-static/range {v$topicRegister .. v$topicRegister}, $FOR_YOU_TOPIC_FILTER_DESCRIPTOR->resolveForYouTopicIds(Ljava/util/List;)Ljava/util/List;
                    move-result-object v$topicRegister
                """.trimIndent(),
            )
        }
    }

context(context: BytecodePatchContext)
private fun resolveForYouTabHook(
    currentPageRefreshEvent: ResolvedTimelineEvent,
): ResolvedForYouTabHook {
    val candidates = mutableListOf<ResolvedForYouTabHook>()
    val forYouPageTarget = resolveForYouPageTarget()
    val reselectedEventType = resolveForYouReselectedEventType()
    context.classDefForEach { classDef ->
        classDef.methods.toList().forEach { originalMethod ->
            if (originalMethod.returnType != "V" || originalMethod.parameterTypes.size != 1) return@forEach
            val modernHookCandidate = reselectedEventType?.let { eventType ->
                originalMethod.resolveModernForYouTabHook(forYouPageTarget, eventType)
            }
            if (modernHookCandidate != null) {
                val mutableClass = context.mutableClassDefBy(classDef.type)
                val mutableMethod = mutableClass.methods.singleOrNull { method ->
                    method.name == originalMethod.name &&
                        method.returnType == originalMethod.returnType &&
                        method.parameterTypes == originalMethod.parameterTypes
                } as? MutableMethod ?: throw PatchException(
                    "NewX modern For You tab event hook is not mutable: $originalMethod",
                )
                val refreshBridges = mutableClass.methods.mapNotNull { method ->
                    method.tryResolveForYouCurrentPageRefreshBridge(currentPageRefreshEvent)
                }.distinctBy { bridge -> bridge.toString() }
                if (refreshBridges.size != 1) {
                    throw PatchException(
                        "Expected one NewX modern For You refresh bridge for $originalMethod, " +
                            "found ${refreshBridges.size}: ${refreshBridges.joinToString()}",
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
                    refreshBridge = refreshBridges.single(),
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

context(context: BytecodePatchContext)
private fun resolveForYouReselectedEventType(): String? {
    val candidates = buildList {
        context.classDefForEach { classDef ->
            val matchingToStringMethods = classDef.methods.filter { method ->
                method.name == "toString" &&
                    method.returnType == STRING_DESCRIPTOR &&
                    method.parameterTypes.isEmpty() &&
                    method.containsStringFragment("OnTabReselected(tabIndex=")
            }
            if (matchingToStringMethods.size == 1) add(classDef.type)
        }
    }
    if (candidates.size > 1) {
        throw PatchException(
            "Expected one NewX OnTabReselected event class, found ${candidates.size}: " +
                candidates.joinToString(),
        )
    }
    return candidates.singleOrNull()
}

context(context: BytecodePatchContext)
private fun resolveForYouPageTarget(): ResolvedForYouPageTarget {
    val classDefs = buildList {
        context.classDefForEach { add(it) }
    }
    val classesByType = classDefs.associateBy { it.type }
    val candidates = classDefs.flatMap { classDef ->
        val toStringMethods = classDef.methods.filter { method ->
                method.name == "toString" &&
                method.returnType == STRING_DESCRIPTOR &&
                method.parameterTypes.isEmpty() &&
                method.containsExactString("ForYou")
        }
        if (toStringMethods.size != 1) return@flatMap emptyList()
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
        val definition = classesByType[type] ?: return
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
    val pageLookupCandidates = instructions.withIndex().filter { (_, instruction) ->
        val reference = instruction.getReference<MethodReference>() ?: return@filter false
        instruction.opcode == Opcode.INVOKE_STATIC &&
            reference.name == "getOrNull" &&
            reference.returnType == OBJECT_DESCRIPTOR &&
            reference.parameterTypes.map(CharSequence::toString) ==
                listOf(OBJECT_LIST_DESCRIPTOR, INTEGER_DESCRIPTOR)
    }

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
    val currentPageLookupCandidates = pageLookupCandidates.filter { (index, _) ->
        index < eventBranch.first
    }
    if (currentPageLookupCandidates.size != 1) {
        throw PatchException(
            "Expected one NewX modern For You current-page lookup before the reselection event, " +
                "found ${currentPageLookupCandidates.size}: " +
                "${currentPageLookupCandidates.joinToString()}",
        )
    }

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

context(context: BytecodePatchContext)
private fun installForYouRefreshBridge(
    tabHook: ResolvedForYouTabHook,
    clearAndRefreshEvent: ResolvedTimelineEvent,
    scrollToTopEvent: ResolvedTimelineEvent,
) {
    val classDef = context.mutableClassDefBy(tabHook.method.definingClass)
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

    val implementation = MethodImplementationBuilder(4).apply {
        addInstruction("return-void".toInstruction())
    }.methodImplementation
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

    val placeholderImplementation = bridgeMethod.implementation
        ?: throw PatchException("NewX For You refresh bridge has no implementation")
    placeholderImplementation.removeInstruction(placeholderImplementation.instructions.lastIndex)
    val bridgeSmali = tabHook.refreshBridge.toSmali(
        clearAndRefreshEvent.field,
        scrollToTopEvent.field,
    )
    bridgeMethod.addInstructionsWithLabels(
        0,
        bridgeSmali,
    )
}

context(context: BytecodePatchContext)
private fun resolveSingletonTimelineEvent(eventLabel: String): ResolvedTimelineEvent {
    val eventClasses = mutableListOf<com.android.tools.smali.dexlib2.iface.ClassDef>()
    context.classDefForEach { classDef ->
        val matchingToStringMethods = classDef.methods.filter { method ->
            method.name == "toString" &&
                method.returnType == STRING_DESCRIPTOR &&
                method.parameterTypes.isEmpty() &&
                method.containsStringFragment(eventLabel)
        }
        if (matchingToStringMethods.size == 1) eventClasses += classDef
    }
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

private fun Method.tryResolveForYouCurrentPageRefreshBridge(
    clearAndRefreshEvent: ResolvedTimelineEvent,
): ResolvedForYouCurrentPageRefreshBridge? {
    val instructions = implementation?.instructions?.toList() ?: return null

    fun fieldAt(index: Int): FieldReference? =
        instructions[index].getReference<FieldReference>()

    fun typeAt(index: Int): String? =
        instructions[index].getReference<TypeReference>()?.type

    val stateFields = instructions.withIndex()
        .filter { (_, instruction) -> instruction.opcode == Opcode.IGET_OBJECT }
        .mapNotNull { (index, _) -> fieldAt(index) }
        .filter { field ->
            field.definingClass == definingClass &&
                instructions.any { instruction ->
                    if (instruction.opcode != Opcode.INVOKE_VIRTUAL) return@any false
                    val reference = instruction.getReference<MethodReference>() ?: return@any false
                    reference.definingClass == field.type &&
                        reference.returnType == OBJECT_DESCRIPTOR &&
                        reference.parameterTypes.isEmpty()
                }
        }
        .distinctBy(FieldReference::toString)
    if (stateFields.size != 1) return null
    val stateField = stateFields[0]

    val stateGetters = instructions.withIndex()
        .filter { (_, instruction) -> instruction.opcode == Opcode.INVOKE_VIRTUAL }
        .mapNotNull { (index, _) ->
            val reference = instructions[index].getReference<MethodReference>() ?: return@mapNotNull null
            reference.takeIf {
                it.definingClass == stateField.type &&
                    it.returnType == OBJECT_DESCRIPTOR &&
                    it.parameterTypes.isEmpty()
            }
        }
        .distinctBy(MethodReference::toString)
    if (stateGetters.size != 1) return null
    val stateGetter = stateGetters[0]

    val pagesCastCandidates = instructions.withIndex()
        .filter { (index, instruction) ->
            instruction.opcode == Opcode.CHECK_CAST &&
                index > 1 &&
                instructions[index - 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
                instructions[index - 2].getReference<MethodReference>()?.matches(stateGetter) == true &&
                instructions.getOrNull(index + 1)?.opcode == Opcode.IGET_OBJECT &&
                instructions[index + 1].getReference<FieldReference>()?.let { field ->
                    field.definingClass == typeAt(index) && field.type == OBJECT_LIST_DESCRIPTOR
                } == true
        }
    if (pagesCastCandidates.size != 1) return null
    val pagesCastIndex = pagesCastCandidates[0].index
    val pagesType = typeAt(pagesCastIndex) ?: return null

    val pagesListFields = instructions.withIndex()
        .filter { (_, instruction) -> instruction.opcode == Opcode.IGET_OBJECT }
        .mapNotNull { (index, _) -> fieldAt(index) }
        .filter { field -> field.definingClass == pagesType && field.type == OBJECT_LIST_DESCRIPTOR }
        .distinctBy(FieldReference::toString)
    if (pagesListFields.size != 1) return null
    val pagesListField = pagesListFields[0]

    val pagesIndexFields = instructions.withIndex()
        .filter { (_, instruction) -> instruction.opcode == Opcode.IGET }
        .mapNotNull { (index, _) -> fieldAt(index) }
        .filter { field -> field.definingClass == pagesType && field.type == INTEGER_DESCRIPTOR }
        .distinctBy(FieldReference::toString)
    if (pagesIndexFields.size != 1) return null
    val pagesIndexField = pagesIndexFields[0]

    val pagesListRegisterCandidates = instructions.withIndex()
        .filter { (_, instruction) ->
            instruction.getReference<FieldReference>()?.toString() == pagesListField.toString()
        }
        .mapNotNull { (_, instruction) -> instruction as? TwoRegisterInstruction }
    if (pagesListRegisterCandidates.size != 1) return null
    val pagesListRegister = pagesListRegisterCandidates[0]
    val pageLookupCandidates = instructions.withIndex()
        .filter { (_, instruction) -> instruction.opcode == Opcode.INVOKE_INTERFACE }
        .mapNotNull { (index, instruction) ->
            val reference = instruction.getReference<MethodReference>() ?: return@mapNotNull null
            if (reference.definingClass != LIST_DESCRIPTOR ||
                reference.name != "get" ||
                reference.returnType != OBJECT_DESCRIPTOR ||
                reference.parameterTypes.map(CharSequence::toString) != listOf(INTEGER_DESCRIPTOR) ||
                instruction.registersUsed.size != 2 ||
                instruction.registersUsed.getOrNull(0) != pagesListRegister.registerA
            ) {
                return@mapNotNull null
            }
            index to reference
        }
    if (pageLookupCandidates.size != 1) return null
    val (pageLookupIndex, pageLookup) = pageLookupCandidates[0]

    val componentCastIndex = pageLookupIndex + 2
    if (componentCastIndex >= instructions.size ||
        instructions[pageLookupIndex + 1].opcode != Opcode.MOVE_RESULT_OBJECT ||
        instructions[componentCastIndex].opcode != Opcode.CHECK_CAST
    ) {
        return null
    }
    val componentCast = instructions[componentCastIndex] as? OneRegisterInstruction
        ?: return null
    val componentType = typeAt(componentCastIndex) ?: return null
    val componentGetterIndex = componentCastIndex + 1
    if (componentGetterIndex >= instructions.size ||
        instructions[componentGetterIndex].opcode != Opcode.INVOKE_VIRTUAL
    ) {
        return null
    }
    val componentGetter = instructions[componentGetterIndex]
        .getReference<MethodReference>() ?: return null
    if (componentGetter.definingClass != componentType ||
        componentGetter.returnType != OBJECT_DESCRIPTOR ||
        componentGetter.parameterTypes.isNotEmpty() ||
        instructions[componentGetterIndex].registersUsed != listOf(componentCast.registerA)
    ) {
        return null
    }

    val getterResultIndex = componentGetterIndex + 1
    val forYouInstanceOfIndex = getterResultIndex + 1
    if (forYouInstanceOfIndex >= instructions.size ||
        instructions[getterResultIndex].opcode != Opcode.MOVE_RESULT_OBJECT ||
        instructions[forYouInstanceOfIndex].opcode != Opcode.INSTANCE_OF
    ) {
        return null
    }
    val forYouInstanceOf = instructions[forYouInstanceOfIndex] as? TwoRegisterInstruction
        ?: return null
    val forYouComponentType = typeAt(forYouInstanceOfIndex) ?: return null
    val forYouControllerFields = instructions.withIndex()
        .drop(forYouInstanceOfIndex + 1)
        .filter { (_, instruction) -> instruction.opcode == Opcode.IGET_OBJECT }
        .mapNotNull { (index, _) -> fieldAt(index) }
        .filter { field -> field.definingClass == forYouComponentType }
        .distinctBy(FieldReference::toString)
    if (forYouControllerFields.size != 1) return null
    val forYouControllerField = forYouControllerFields[0]

    val refreshDispatches = instructions.withIndex()
        .drop(forYouInstanceOfIndex + 1)
        .filter { (_, instruction) -> instruction.opcode == Opcode.INVOKE_INTERFACE }
        .mapNotNull { (index, _) ->
            val reference = instructions[index].getReference<MethodReference>() ?: return@mapNotNull null
            reference.takeIf {
                it.definingClass == forYouControllerField.type &&
                    it.returnType == "V" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes.single().toString() in
                        clearAndRefreshEvent.dispatchParameterTypes
            }
        }
        .distinctBy(MethodReference::toString)
    if (refreshDispatches.size != 1) return null

    return ResolvedForYouCurrentPageRefreshBridge(
        stateField = stateField,
        stateGetter = stateGetter,
        pagesType = pagesType,
        pagesListField = pagesListField,
        pagesIndexField = pagesIndexField,
        pageLookup = pageLookup,
        componentType = componentType,
        componentGetter = componentGetter,
        forYouComponentType = forYouComponentType,
        forYouControllerField = forYouControllerField,
        refreshDispatch = refreshDispatches.single(),
    )
}

private fun ResolvedForYouCurrentPageRefreshBridge.toSmali(
    clearAndRefreshField: FieldReference,
    scrollToTopField: FieldReference,
): String =
    """
        iget-object v0, p0, ${stateField.smaliReference()}
        invoke-virtual {v0}, ${stateGetter.smaliReference()}
        move-result-object v1
        check-cast v1, $pagesType
        iget-object v1, v1, ${pagesListField.smaliReference()}
        invoke-virtual {v0}, ${stateGetter.smaliReference()}
        move-result-object v0
        check-cast v0, $pagesType
        iget v0, v0, ${pagesIndexField.smaliReference()}
        invoke-interface {v1, v0}, ${pageLookup.smaliReference()}
        move-result-object v0
        check-cast v0, $componentType
        invoke-virtual {v0}, ${componentGetter.smaliReference()}
        move-result-object v0
        instance-of v1, v0, $forYouComponentType
        if-eqz v1, :piko_for_you_refresh_current_page_done
        check-cast v0, $forYouComponentType
        iget-object v1, v0, ${forYouControllerField.smaliReference()}
        if-eqz v1, :piko_for_you_refresh_current_page_done
        invoke-static {}, $TIMELINE_REFRESH_GATE_DESCRIPTOR->markForYouFilterRefresh()V
        sget-object v0, ${clearAndRefreshField.smaliReference()}
        invoke-interface {v1, v0}, ${refreshDispatch.smaliReference()}
        sget-object v0, ${scrollToTopField.smaliReference()}
        invoke-interface {v1, v0}, ${refreshDispatch.smaliReference()}

        :piko_for_you_refresh_current_page_done
        return-void
    """.trimIndent()

context(context: BytecodePatchContext)
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

    val constructor = constructors.single() as? MutableMethod
        ?: throw PatchException("NewX HomeTimelineFilters group constructor is not mutable")
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

    constructor.addInstructions(
        superIndex + 1,
        "invoke-static {p1, p4}, $FOR_YOU_TOPIC_FILTER_DESCRIPTOR->captureTopicOptions(Ljava/lang/Object;Ljava/lang/Object;)V",
    )
}

context(context: BytecodePatchContext)
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
    val requestFingerprint =
        Fingerprint(
            parameters = listOf(OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR),
            returnType = OBJECT_DESCRIPTOR,
            filters =
                listOf(
                    string("requestType"),
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

private fun Method.containsExactString(value: String): Boolean =
    implementation?.instructions?.any { instruction ->
        instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.StringReference>()
            ?.string == value
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

private fun MethodReference.matches(reference: MethodReference): Boolean =
    definingClass == reference.definingClass &&
        name == reference.name &&
        returnType == reference.returnType &&
        parameterTypes.map(CharSequence::toString) == reference.parameterTypes.map(CharSequence::toString)

private fun FieldReference.matches(reference: FieldReference): Boolean =
    definingClass == reference.definingClass &&
        name == reference.name &&
        type == reference.type

private fun Method.smaliReference(): String =
    "$definingClass->$name(${parameterTypes.joinToString(separator = "") { it.toString() }})$returnType"

private fun MethodReference.smaliReference(): String =
    "$definingClass->$name(${parameterTypes.joinToString(separator = "") { it.toString() }})$returnType"

private fun FieldReference.smaliReference(): String =
    "$definingClass->$name:$type"
