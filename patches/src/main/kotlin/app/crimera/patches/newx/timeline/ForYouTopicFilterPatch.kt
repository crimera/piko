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
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.getFreeRegisterProvider
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
private const val FLOW_PREFIX = "Lkotlinx/coroutines/flow/"
private const val INTRINSICS_DESCRIPTOR = "Lkotlin/jvm/internal/Intrinsics;"
private const val OBJECT_LIST_DESCRIPTOR = "Ljava/util/List;"
private const val INTEGER_DESCRIPTOR = "I"
private const val FOR_YOU_REFRESH_TARGET_DESCRIPTOR =
    "Lapp/morphe/extension/newx/timeline/ForYouTopicFilter\$RefreshTarget;"
private const val FOR_YOU_REFRESH_BRIDGE_NAME = "pikoRefreshForYouTopicFilter"

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
    val refreshMethod: Method,
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
            newXInitHook.fingerprint.method.addInstruction(
                0,
                "invoke-static/range {p0 .. p0}, $FOR_YOU_TOPIC_FILTER_DESCRIPTOR->initialize(Landroid/content/Context;)V",
            )

            patchHomeFilterGroupConstructor()

            val tabHook = resolveForYouTabHook()
            installForYouRefreshBridge(tabHook)
            val continuation = tabHook.method.instructions[tabHook.insertionIndex]
            val hookEnabledRead =
                forYouTabHookEnabled.injectRead(
                    method = tabHook.method,
                    index = tabHook.insertionIndex,
                    registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                )
            val sheetResultRegister =
                tabHook.method
                    .getFreeRegisterProvider(
                        hookEnabledRead.nextIndex,
                        1,
                        hookEnabledRead.register,
                    ).getFreeRegister4Bit()
            tabHook.method.addInstructionsWithLabels(
                hookEnabledRead.nextIndex,
                """
                    if-eqz v${hookEnabledRead.register}, :piko_newx_for_you_topic_sheet_continue
                    invoke-static/range {p0 .. p0}, $FOR_YOU_TOPIC_FILTER_DESCRIPTOR->showForYouTopicSheet($FOR_YOU_REFRESH_TARGET_DESCRIPTOR)Z
                    move-result v$sheetResultRegister
                    if-eqz v$sheetResultRegister, :piko_newx_for_you_topic_sheet_continue
                    return-void
                """.trimIndent(),
                ExternalLabel("piko_newx_for_you_topic_sheet_continue", continuation),
            )

            val target = resolveForYouRequestTarget()
            val matches = target.requestFingerprint.scopedMatchAll()
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
            if (!constructorReference.matches(target.queryConstructor)) {
                throw PatchException(
                    "NewX For You topic request constructor changed: $constructorReference",
                )
            }

            val topicRegister = constructorInstruction.topicArgumentRegister(
                target.queryConstructor,
                target.topicParameterIndex,
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
private fun resolveForYouTabHook(): ResolvedForYouTabHook {
    val candidates = mutableListOf<ResolvedForYouTabHook>()
    context.classDefForEach { classDef ->
        classDef.methods.forEach { originalMethod ->
            if (originalMethod.returnType != "V" || originalMethod.parameterTypes.size != 1) return@forEach
            val implementation = originalMethod.implementation ?: return@forEach
            val instructions = implementation.instructions.toList()

            val pageLookupIndex = instructions.indexOfFirst { instruction ->
                val reference = instruction.getReference<MethodReference>() ?: return@indexOfFirst false
                reference.name == "getOrNull" &&
                    reference.returnType == OBJECT_DESCRIPTOR &&
                    reference.parameterTypes.map(CharSequence::toString) ==
                        listOf(OBJECT_LIST_DESCRIPTOR, INTEGER_DESCRIPTOR)
            }
            if (pageLookupIndex < 0) return@forEach

            val pageEqualityBranch = instructions.withIndex()
                .drop(pageLookupIndex + 1)
                .firstNotNullOfOrNull { (index, instruction) ->
                    val reference = instruction.getReference<MethodReference>() ?: return@firstNotNullOfOrNull null
                    if (instruction.opcode != Opcode.INVOKE_STATIC ||
                        reference.definingClass != INTRINSICS_DESCRIPTOR ||
                        reference.name != "areEqual" ||
                        reference.returnType != "Z" ||
                        reference.parameterTypes.map(CharSequence::toString) !=
                            listOf(OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR)) {
                        return@firstNotNullOfOrNull null
                    }
                    val resultIndex = index + 1
                    val branchIndex = resultIndex + 1
                    if (branchIndex >= instructions.size ||
                        instructions[resultIndex].opcode != Opcode.MOVE_RESULT ||
                        instructions[branchIndex].opcode != Opcode.IF_EQZ) {
                        return@firstNotNullOfOrNull null
                    }
                    branchIndex
                } ?: return@forEach

            val hookIndex = instructions.withIndex()
                .drop(pageEqualityBranch + 1)
                .firstNotNullOfOrNull { (castIndex, castInstruction) ->
                    if (castInstruction.opcode != Opcode.CHECK_CAST) return@firstNotNullOfOrNull null
                    val castRegister =
                        (castInstruction as? OneRegisterInstruction)?.registerA
                            ?: return@firstNotNullOfOrNull null
                    val castType =
                        castInstruction.getReference<TypeReference>()?.type
                            ?: return@firstNotNullOfOrNull null
                    val guardIndex = castIndex + 1
                    val fieldIndex = guardIndex + 1
                    if (fieldIndex >= instructions.size ||
                        instructions[guardIndex].opcode != Opcode.IF_EQZ ||
                        (instructions[guardIndex] as? OneRegisterInstruction)?.registerA != castRegister ||
                        instructions[fieldIndex].opcode != Opcode.IGET_OBJECT) {
                        return@firstNotNullOfOrNull null
                    }
                    val fieldInstruction = instructions[fieldIndex] as? TwoRegisterInstruction
                        ?: return@firstNotNullOfOrNull null
                    if (fieldInstruction.registerB != castRegister) return@firstNotNullOfOrNull null
                    val field = fieldInstruction.getReference<FieldReference>()
                        ?: return@firstNotNullOfOrNull null
                    if (field.definingClass != castType || !field.type.startsWith(FLOW_PREFIX)) {
                        return@firstNotNullOfOrNull null
                    }
                    guardIndex + 1
                } ?: return@forEach

            val mutableClass = context.mutableClassDefBy(classDef.type)
            val mutableMethod = mutableClass.methods.singleOrNull { method ->
                method.name == originalMethod.name &&
                    method.returnType == originalMethod.returnType &&
                    method.parameterTypes == originalMethod.parameterTypes
            } as? MutableMethod ?: return@forEach
            val refreshMethods = mutableClass.methods.filter { method ->
                method.name != "<init>" &&
                    method.returnType == "V" &&
                    method.parameterTypes.isEmpty() &&
                    method.isCurrentTimelineRefreshMethod()
            }
            if (refreshMethods.size != 1) return@forEach
            candidates += ResolvedForYouTabHook(mutableMethod, hookIndex, refreshMethods.single())
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

private fun Method.isCurrentTimelineRefreshMethod(): Boolean {
    val implementation = implementation ?: return false
    val instructions = implementation.instructions.toList()
    val hasCurrentPageLookup = instructions.any { instruction ->
        val reference = instruction.getReference<MethodReference>() ?: return@any false
        instruction.opcode == Opcode.INVOKE_STATIC &&
            reference.name == "getOrNull" &&
            reference.returnType == OBJECT_DESCRIPTOR &&
            reference.parameterTypes.map(CharSequence::toString) ==
                listOf(OBJECT_LIST_DESCRIPTOR, INTEGER_DESCRIPTOR)
    }
    val hasTimelineRefreshEvent = instructions.any { instruction ->
        if (instruction.opcode != Opcode.SGET_OBJECT) return@any false
        val reference = instruction.getReference<FieldReference>() ?: return@any false
        reference.name == "a" &&
            reference.definingClass.startsWith("Lcom/x/urt/") &&
            reference.type.startsWith("Lcom/x/urt/")
    }
    val hasTimelineRefreshDispatch = instructions.any { instruction ->
        if (instruction.opcode != Opcode.INVOKE_INTERFACE) return@any false
        val reference = instruction.getReference<MethodReference>() ?: return@any false
        reference.name == "i" &&
            reference.returnType == "V" &&
            reference.parameterTypes.size == 1 &&
            reference.parameterTypes.single().toString().startsWith("Lcom/x/urt/")
    }
    return hasCurrentPageLookup && hasTimelineRefreshEvent && hasTimelineRefreshDispatch
}

context(context: BytecodePatchContext)
private fun installForYouRefreshBridge(tabHook: ResolvedForYouTabHook) {
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

    val refreshReference = tabHook.refreshMethod.smaliReference()
    val implementation = MethodImplementationBuilder(1).apply {
        addInstruction("invoke-virtual {v0}, $refreshReference".toInstruction())
        addInstruction("return-void".toInstruction())
    }.methodImplementation
    classDef.methods.add(
        MutableMethod(
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
        ),
    )
}

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

    val superIndex = constructor.instructions.withIndex().firstOrNull { (_, instruction) ->
        if (instruction.opcode != Opcode.INVOKE_DIRECT) return@firstOrNull false
        val reference = instruction.getReference<MethodReference>() ?: return@firstOrNull false
        reference.name == "<init>" && reference.definingClass != constructor.definingClass
    }?.index ?: throw PatchException(
        "NewX HomeTimelineFilters group constructor has no super call: $constructor",
    )

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
    if (writes.size != 1) return null

    val write = writes.single().value as? TwoRegisterInstruction ?: return null
    val sourceRegisters = if (write.registerB == thisRegister) {
        listOf(
            write.registerA,
            instructions.resolveObjectOriginRegister(writes.single().index, write.registerA),
        )
    } else {
        listOf(instructions.resolveObjectOriginRegister(writes.single().index, write.registerA))
    }
    return sourceRegisters.asSequence()
        .mapNotNull { sourceRegister ->
            parameterTypes.indexOfRegister(sourceRegister, thisRegister)
        }
        .distinct()
        .firstOrNull { parameterIndex ->
            expectedParameterTypes.isEmpty() ||
                parameterTypes[parameterIndex].toString() in expectedParameterTypes
        }
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
    if (fields.size == 1) return fields.single()
    throw PatchException(
        "Expected one NewX serialized field for '$argumentName' in $this, found " +
            "${fields.size}: ${fields.joinToString()}",
    )
}

private fun MethodReference.matches(method: Method): Boolean =
    definingClass == method.definingClass &&
        name == method.name &&
        returnType == method.returnType &&
        parameterTypes.map(CharSequence::toString) == method.parameterTypes.map(CharSequence::toString)

private fun Method.smaliReference(): String =
    "$definingClass->$name(${parameterTypes.joinToString(separator = "") { it.toString() }})$returnType"
