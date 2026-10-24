package app.crimera.patches.newx.misc.postoptions

import app.crimera.patches.newx.misc.extension.newXInitHook
import app.crimera.patches.newx.models.resolvedNewXInlineActionModels
import app.crimera.patches.newx.models.newXInlineActionModelResolutionPatch
import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.settings.newXSettingsPatch
import app.crimera.patches.newx.utils.Constants.POST_OPTIONS_FILTER_DESCRIPTOR
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.isObjectDescriptor
import app.crimera.patches.common.isAssignableTo
import app.crimera.patches.common.parameterDescriptors
import app.crimera.patches.common.requireExactlyOne
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.util.cloneParameters
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resource.resourceId
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.numberOfParameterRegisters
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.IdentityHashMap
import java.util.LinkedHashMap

private const val POST_OPTIONS_STATE_PREFIX = "PostOptionsState(showOptionsDialog="
private const val POST_OPTIONS_LIST_PREFIX = ", options="
private const val CONTEXT = "Landroid/content/Context;"
private const val NEWX_UTILS = "Lapp/morphe/extension/newx/utils/NewXUtils;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val ADD_OPTION_SIGNATURE = "addOption($LIST_DESCRIPTOR)$LIST_DESCRIPTOR"
private const val LABEL_FOR_SIGNATURE = "labelFor($OBJECT_DESCRIPTOR$OBJECT_DESCRIPTOR)$STRING_DESCRIPTOR"
private const val USES_ICON_SIGNATURE = "usesIcon($OBJECT_DESCRIPTOR)Z"
private const val HANDLE_OPTION_ACTION_SIGNATURE = "handleOptionAction($OBJECT_DESCRIPTOR$OBJECT_DESCRIPTOR)Z"

internal const val BROWSE_OBJECT_ACTION = "None"
internal const val FILTERED_REPLIES_ACTION = "ServerFeedbackAction"

/** NewX icon resource initialization emits the field assignment within this small block. */
private const val ICON_FIELD_INITIALIZATION_WINDOW = 4

private val OBJECT_MOVE_OPCODES =
    setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)

private data class PostOptionContribution(
    val handlerDescriptor: String,
    val actionName: String,
    val iconResourceName: String,
    val order: Int,
)

private data class IconAssignment(
    val instruction: TwoRegisterInstruction,
    val field: FieldReference,
)

private object PostOptionContributionIndex {
    private val contributions =
        IdentityHashMap<BytecodePatchContext, LinkedHashMap<String, PostOptionContribution>>()

    @Synchronized
    fun register(context: BytecodePatchContext, contribution: PostOptionContribution) {
        val contextContributions = contributions.getOrPut(context) { linkedMapOf() }
        val existing = contextContributions[contribution.handlerDescriptor]
        check(existing == null || existing == contribution) {
            "Conflicting NewX post-option contribution: ${contribution.handlerDescriptor}"
        }
        contextContributions[contribution.handlerDescriptor] = contribution
    }

    @Synchronized
    fun takeSnapshot(context: BytecodePatchContext): List<PostOptionContribution> {
        val contextContributions = contributions.remove(context) ?: return emptyList()
        return contextContributions.values.sortedBy(PostOptionContribution::order)
    }

    @Synchronized
    fun clear(context: BytecodePatchContext) {
        contributions.remove(context)
    }
}

private object PostOptionsStateFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    filters =
        listOf(
            app.morphe.patcher.string(POST_OPTIONS_STATE_PREFIX),
            app.morphe.patcher.string(POST_OPTIONS_LIST_PREFIX),
        ),
)

private object PostOptionsPresenterFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                definingClass = "this",
                type = "Lcom/x/models/timelines/items/",
            ),
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                definingClass = "this",
                type = CONTEXT,
            ),
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                definingClass = "this",
                type = "Lkotlinx/coroutines/channels/",
            ),
        ),
)

internal fun BytecodePatchBuilder.newXPostOption(
    handlerDescriptor: String,
    actionName: String,
    iconResourceName: String,
    order: Int,
) {
    dependsOn(
        newXPostOptionContributionPatch(
            PostOptionContribution(
                handlerDescriptor = handlerDescriptor,
                actionName = actionName,
                iconResourceName = iconResourceName,
                order = order,
            ),
        ),
    )
}

private fun newXPostOptionContributionPatch(contribution: PostOptionContribution) =
    bytecodePatch(default = false) {
        dependsOn(newXPostOptionsPatch)

        execute {
            PostOptionContributionIndex.register(this, contribution)
        }
    }

private val newXPostOptionsPatch =
    bytecodePatch(default = false) {
        dependsOn(newXSettingsPatch, newXInlineActionModelResolutionPatch)

        execute {
            PostOptionContributionIndex.clear(this)
            val applicationOnCreate = newXInitHook.fingerprint.method
            // The Application is a Context, so its receiver is the only argument.
            applicationOnCreate.insertHook(0, relocateBranchTargets = false) {
                invokeStatic(
                    methodReference("$NEWX_UTILS->initialize($CONTEXT)V"),
                    applicationOnCreate.p0Register,
                )
            }
        }

        finalize {
            val contributions = PostOptionContributionIndex.takeSnapshot(this)
            if (contributions.isEmpty()) throw PatchException("No NewX post-menu options were registered")

            validateActionCarriers(contributions)
            injectOptionList(contributions)
            injectLabelsAndIcons(contributions)
            injectActionHandlers(contributions)
        }
    }

context(context: BytecodePatchContext)
private fun validateActionCarriers(contributions: List<PostOptionContribution>) {
    val postActionType = resolvedNewXInlineActionModels().postActionTypeDescriptor
    val actionType = context.mutableClassDefBy(postActionType)
    val missing =
        contributions
            .map(PostOptionContribution::actionName)
            .distinct()
            .filter { actionName ->
                actionType.fields.none { field -> field.name == actionName && field.type == postActionType }
            }
    if (missing.isNotEmpty()) {
        throw PatchException("Missing NewX post-menu action carriers: ${missing.joinToString()}")
    }

    val duplicates =
        contributions
            .groupingBy(PostOptionContribution::actionName)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
    if (duplicates.isNotEmpty()) {
        throw PatchException("Duplicate NewX post-menu action carriers: ${duplicates.joinToString()}")
    }
}

context(_: BytecodePatchContext)
private fun injectOptionList(contributions: List<PostOptionContribution>) {
    val state = resolvePostOptionsStateConstructor()
    val stateConstructor = state.match.method
    // Handler calls are chained on the options-list parameter, in registration order.
    val listRegister = stateConstructor.p0Register + state.optionsParameterOffset
    stateConstructor.insertHook(0, relocateBranchTargets = false) {
        contributions.forEach { contribution ->
            invokeStatic(
                methodReference("${contribution.handlerDescriptor}->$ADD_OPTION_SIGNATURE"),
                listRegister,
            )
            moveResult(listRegister, LIST_DESCRIPTOR)
        }
    }
}

internal data class PostOptionsStateConstructor(
    val match: Match,
    val optionsField: FieldReference,
    val optionsParameterOffset: Int,
)

context(_: BytecodePatchContext)
internal fun resolvePostOptionsStateConstructor(): PostOptionsStateConstructor {
    val stateMatch = requireSingleMatch("NewX post-options state", PostOptionsStateFingerprint.matchAll())
    val optionsField = stateMatch.fieldForToStringLabel(POST_OPTIONS_LIST_PREFIX)
    // The hooks replace this field's value with a List, not an arbitrary concrete collection.
    if (optionsField.type != LIST_DESCRIPTOR) {
        throw PatchException("NewX post-options field no longer accepts List: $optionsField")
    }
    val constructor = requireSingleMatch(
        "NewX post-options state constructor",
        Fingerprint(
            classFingerprint = PostOptionsStateFingerprint,
            name = "<init>",
            returnType = "V",
            custom = { method, _ ->
                method.instructions.any { instruction ->
                    instruction.opcode == Opcode.IPUT_OBJECT &&
                        instruction.getReference<FieldReference>()?.toString() == optionsField.toString()
                }
            },
        ).matchAll(),
    )
    return PostOptionsStateConstructor(
        constructor,
        optionsField,
        postOptionsParameterOffset(constructor.originalMethod, optionsField),
    )
}

/** Trace the actual options-field input; unrelated constructor parameters are not the hook ABI. */
internal fun postOptionsParameterOffset(method: Method, optionsField: FieldReference): Int {
    val code = method.instructions.toList()
    val storeIndex = postOptionsListStoreIndex(method, optionsField)
    if (method.implementation!!.tryBlocks.isNotEmpty() || code.take(storeIndex).any { it is OffsetInstruction }) {
        throw PatchException("NewX post-options constructor has non-linear options initialization: $method")
    }
    val receiver = method.implementation!!.registerCount - method.numberOfParameterRegisters
    val origins = mutableMapOf(receiver to 0)
    var offset = 1
    method.parameterTypes.forEach { type ->
        if (type.toString() == LIST_DESCRIPTOR) origins[receiver + offset] = offset
        offset += if (type.toString() in setOf("J", "D")) 2 else 1
    }
    var filteredInput: Int? = null
    for (instruction in code.take(storeIndex)) {
        val precedingFilterInput = filteredInput
        filteredInput = null
        val reference = instruction.getReference<MethodReference>()
        if (reference != null && instruction.opcode in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)) {
            // Customize-post-menu runs before contribution finalization. Its own filter replaces
            // the list but preserves which constructor parameter supplies it; arbitrary calls do not.
            filteredInput = if (reference.toString() ==
                "$POST_OPTIONS_FILTER_DESCRIPTOR->filter(Ljava/util/List;Ljava/util/Set;)Ljava/util/List;"
            ) origins[instruction.registersUsed.first()] else null
            continue
        }
        val destination = instruction as? OneRegisterInstruction ?: continue
        if (!instruction.opcode.setsRegister()) continue
        val origin = when {
            instruction.opcode in OBJECT_MOVE_OPCODES -> origins[(instruction as TwoRegisterInstruction).registerB]
            instruction.opcode == Opcode.MOVE_RESULT_OBJECT -> precedingFilterInput
            else -> null
        }
        origins.remove(destination.registerA)
        if (instruction.opcode.setsWideRegister()) origins.remove(destination.registerA + 1)
        if (origin != null) origins[destination.registerA] = origin
    }
    val store = code[storeIndex] as TwoRegisterInstruction
    val parameter = origins[store.registerA]
    if (origins[store.registerB] != 0 || parameter == null || parameter == 0) {
        throw PatchException("NewX post-options field is not initialized from a List parameter: $method")
    }
    return parameter
}

internal fun postOptionsListStoreIndex(method: Method, optionsField: FieldReference): Int =
    requireExactlyOne(
        "NewX post-options list store in $method",
        method.instructions.mapIndexedNotNull { index, instruction ->
            index.takeIf {
                instruction.opcode == Opcode.IPUT_OBJECT &&
                    instruction.getReference<FieldReference>()?.toString() == optionsField.toString()
            }
        },
    )

internal data class PostOptionsFeedbackLookup(
    val callIndex: Int,
    val resultIndex: Int,
    val actionRegister: Int,
    val labelRegister: Int,
)

internal fun postOptionsFeedbackLookups(
    method: Method,
    feedbackField: FieldReference,
    isMap: (String) -> Boolean,
): List<PostOptionsFeedbackLookup> {
    val code = method.instructions.toList()
    return code.indices.mapNotNull { fieldIndex ->
        val read = code[fieldIndex] as? TwoRegisterInstruction ?: return@mapNotNull null
        if (read.opcode != Opcode.IGET_OBJECT ||
            code[fieldIndex].getReference<FieldReference>()?.toString() != feedbackField.toString()
        ) return@mapNotNull null
        val call = code.getOrNull(fieldIndex + 1) ?: return@mapNotNull null
        val reference = call.getReference<MethodReference>() ?: return@mapNotNull null
        if (call.opcode !in setOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE,
                Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) ||
            reference.name != "get" || !isMap(reference.definingClass) ||
            reference.parameterTypes.map(CharSequence::toString) != listOf(OBJECT_DESCRIPTOR) ||
            reference.returnType != OBJECT_DESCRIPTOR
        ) return@mapNotNull null
        val arguments = call.registersUsed
        if (arguments.size != 2 || arguments[0] != read.registerA) return@mapNotNull null
        val result = code.getOrNull(fieldIndex + 2) as? OneRegisterInstruction ?: return@mapNotNull null
        if (result.opcode != Opcode.MOVE_RESULT_OBJECT) return@mapNotNull null
        PostOptionsFeedbackLookup(fieldIndex + 1, fieldIndex + 2, arguments[1], result.registerA)
    }
}

context(context: BytecodePatchContext)
private fun injectLabelsAndIcons(contributions: List<PostOptionContribution>) {
    val state = requireSingleMatch("NewX post-options state", PostOptionsStateFingerprint.matchAll())
    val feedbackField = state.fieldForToStringLabel(", feedbackPrompts=")
    if (!context.isAssignableTo(feedbackField.type, "Ljava/util/Map;")) {
        throw PatchException("NewX post-options feedback prompts are not a Map: $feedbackField")
    }
    val isMap = { type: String -> context.isAssignableTo(type, "Ljava/util/Map;") }
    val rendererFingerprint =
            Fingerprint(
                filters =
                    listOf(
                        fieldAccess(
                            opcode = Opcode.IGET_OBJECT,
                            reference = feedbackField,
                        ),
                    ),
                custom = { method, _ -> postOptionsFeedbackLookups(method, feedbackField, isMap).size == 1 },
            )
    val originalRenderer = requireSingleMatch(
        "NewX post-options label renderer",
        rendererFingerprint.matchAll(),
    ).method
    val iconCheckRegister = originalRenderer.implementation?.registerCount
        ?: throw PatchException("NewX post-options label renderer has no implementation")
    if (iconCheckRegister > 255) {
        throw PatchException("NewX post-options icon check requires a byte-encodable register")
    }
    // cloneParameters copies the arguments into their old slots. The new parameter slots are
    // unused by the original body, so this hook does not depend on finding a dead live-frame local.
    originalRenderer.cloneParameters()
    rendererFingerprint.clearMatch()
    val renderer = requireSingleMatch(
        "NewX post-options label renderer after parameter preservation",
        rendererFingerprint.matchAll(),
    )
    val lookup = requireExactlyOne("NewX post-options feedback lookup",
        postOptionsFeedbackLookups(renderer.method, feedbackField, isMap))
    val actionRegister = lookup.actionRegister

    val iconAssignmentCandidates =
        renderer.method.instructions.withIndex().filter { (index, instruction) ->
            instruction.opcode in OBJECT_MOVE_OPCODES &&
                index < lookup.callIndex &&
                renderer.method.instructions.getOrNull(index + 1)?.opcode in
                setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)
        }
    if (iconAssignmentCandidates.isEmpty()) {
        throw PatchException(
            "Expected NewX post-options icon assignments, found none",
        )
    }
    val iconAssignments = iconAssignmentCandidates.mapNotNull { candidate ->
        val assignment = candidate.value as? TwoRegisterInstruction
            ?: throw PatchException(
                "NewX post-options icon assignment has no registers at instruction ${candidate.index}",
            )
        val field = renderer.method.instructions.resolveFieldReadOrNull(
            register = assignment.registerB,
            untilIndex = candidate.index,
        ) ?: return@mapNotNull null
        IconAssignment(instruction = assignment, field = field)
    }
    val iconResultRegister =
        requireExactlyOne(
            label = "NewX post-options icon result register",
            candidates = iconAssignments.map { assignment -> assignment.instruction.registerA }.distinct(),
        )
    val iconType =
        requireExactlyOne(
            label = "NewX post-options icon type",
            candidates = iconAssignments.map { assignment -> assignment.field.type }.distinct(),
        )

    val iconFields = contributions.associateWith { resolveIconField(it.iconResourceName, iconType) }

    // The label and icon rewrites land in front of the instruction that consumes the resolved
    // label, so they run with both the action and the original label live.
    val insertionIndex = lookup.resultIndex + 1
    renderer.method.insertHook(
        index = insertionIndex,
        excludedRegisters = listOf(actionRegister, lookup.labelRegister, iconResultRegister),
        // A branch into the label lookup must run the rewrite too, or the custom options keep
        // their upstream label and icon.
        relocateBranchTargets = true,
    ) {
        contributions.forEach { contribution ->
            invokeStatic(
                methodReference("${contribution.handlerDescriptor}->$LABEL_FOR_SIGNATURE"),
                actionRegister,
                lookup.labelRegister,
            )
            moveResult(lookup.labelRegister, STRING_DESCRIPTOR)
        }

        // The icon checks share the reserved byte register; if-eqz and move-result both encode it.
        contributions.forEachIndexed { index, contribution ->
            if (index > 0) label(iconContinuationLabel(index))
            invokeStatic(
                methodReference("${contribution.handlerDescriptor}->$USES_ICON_SIGNATURE"),
                actionRegister,
            )
            moveResult(iconCheckRegister, "Z")
            ifEqz(
                iconCheckRegister,
                if (index == contributions.lastIndex) {
                    Target.Original
                } else {
                    Target.Local(iconContinuationLabel(index + 1))
                },
            )
            sget(iconResultRegister, iconFields.getValue(contribution))
        }
    }
}

/** Name of the label that starts the icon check of the contribution at [index]. */
private fun iconContinuationLabel(index: Int): String = "piko_newx_post_option_icon_$index"

context(_: BytecodePatchContext)
private fun injectActionHandlers(contributions: List<PostOptionContribution>) {
    val presenter = requireSingleMatch("NewX post-options presenter", PostOptionsPresenterFingerprint.matchAll())
    val eventHandlerClass =
        requireSingleMatch(
            "NewX post-options event handler class",
            Fingerprint(
                name = "<init>",
                returnType = "V",
                custom = { method, _ ->
                    val parameters = method.parameterDescriptors()
                    parameters.size == 6 &&
                        parameters.firstOrNull() == presenter.originalClassDef.type &&
                        parameters.drop(1).all(String::isObjectDescriptor)
                },
            ).matchAll(),
        )
    val eventHandler =
        requireSingleMatch(
            "NewX post-options event handler",
            Fingerprint(
                classFingerprint = Fingerprint(definingClass = eventHandlerClass.originalClassDef.type),
                parameters = listOf("Ljava/lang/Object;"),
                returnType = "Ljava/lang/Object;",
            ).matchAll(),
        )
    val presenterField =
        eventHandler.originalClassDef.fields.singleOrNull { it.type == presenter.originalClassDef.type }
            ?: throw PatchException("NewX post-options event handler has no unique presenter field")

    val ordinalCandidates = eventHandler.method.instructions.withIndex().filter { (_, instruction) ->
        val methodRef = instruction.getReference<MethodReference>() ?: return@filter false
        instruction.opcode == Opcode.INVOKE_VIRTUAL &&
            methodRef.definingClass == "Ljava/lang/Enum;" &&
            methodRef.name == "ordinal" &&
            methodRef.parameterTypes.isEmpty() &&
            methodRef.returnType == "I" &&
            instruction.registersUsed.size == 1
    }
    if (ordinalCandidates.size != 1) {
        throw PatchException(
            "Expected one NewX confirmed post-option Enum.ordinal, found " +
                "${ordinalCandidates.size}: ${ordinalCandidates.joinToString()}",
        )
    }
    val ordinalIndex = ordinalCandidates.single().index
    val ordinalInstruction = eventHandler.method.instructions[ordinalIndex]
    val clickActionRegister = ordinalInstruction.registersUsed.singleOrNull()
        ?: throw PatchException("NewX confirmed post-option action has no register")
    val unitField = resolveKotlinUnitField()
    val handler = eventHandler.method
    val receiverRegister = handler.p0Register

    handler.insertHook(
        index = ordinalIndex,
        excludedRegisters = listOf(clickActionRegister),
        // A branch that lands on the action dispatch must run the check too, or the custom
        // options fall through to the original handler and do nothing.
        relocateBranchTargets = true,
    ) {
        // One dead register carries the presenter, the Boolean result and the Unit return value.
        val checkRegister = scratchRegister()
        contributions.forEachIndexed { index, contribution ->
            if (index > 0) label(actionContinuationLabel(index))
            move(checkRegister, receiverRegister, OBJECT_DESCRIPTOR)
            iget(checkRegister, checkRegister, presenterField)
            invokeStatic(
                methodReference("${contribution.handlerDescriptor}->$HANDLE_OPTION_ACTION_SIGNATURE"),
                checkRegister,
                clickActionRegister,
            )
            moveResult(checkRegister, "Z")
            ifEqz(
                checkRegister,
                if (index == contributions.lastIndex) {
                    Target.Original
                } else {
                    Target.Local(actionContinuationLabel(index + 1))
                },
            )
            sget(checkRegister, unitField)
            returnObject(checkRegister)
        }
    }
}

/** Name of the label that starts the action check of the contribution at [index]. */
private fun actionContinuationLabel(index: Int): String = "piko_newx_post_option_action_$index"

context(_: BytecodePatchContext)
private fun resolveIconField(resourceName: String, iconType: String): FieldReference {
    val drawableId = resourceId(ResourceType.DRAWABLE, resourceName)
    val fields =
        Fingerprint(
            name = "<clinit>",
            returnType = "V",
            parameters = emptyList(),
            filters = listOf(literal(drawableId)),
        ).matchAll().mapNotNull { match ->
            val literalIndex = match.instructionMatches.single().index
            val fieldCandidates = match.method.instructions
                .drop(literalIndex + 1)
                .take(ICON_FIELD_INITIALIZATION_WINDOW)
                .mapNotNull { instruction ->
                    if (instruction.opcode != Opcode.SPUT_OBJECT) return@mapNotNull null
                    instruction.getReference<FieldReference>()?.takeIf { field -> field.type == iconType }
                }
            if (fieldCandidates.size != 1) {
                throw PatchException(
                    "Expected one $resourceName icon field after resource literal, found " +
                        "${fieldCandidates.size}: ${fieldCandidates.joinToString()}",
                )
            }
            fieldCandidates.single()
        }.distinctBy(FieldReference::toString)
    return requireExactlyOne("NewX $resourceName icon field", fields)
}

private fun List<Instruction>.resolveFieldRead(
    register: Int,
    untilIndex: Int,
): FieldReference {
    var trackedRegister = register
    for (index in untilIndex - 1 downTo 0) {
        val instruction = this[index]
        val field = instruction.getReference<FieldReference>()
        val destination = when (instruction.opcode) {
            Opcode.SGET_OBJECT -> (instruction as? OneRegisterInstruction)?.registerA
            Opcode.IGET_OBJECT -> (instruction as? TwoRegisterInstruction)?.registerA
            else -> null
        }
        if (destination == trackedRegister && field != null) return field

        if (instruction.opcode in OBJECT_MOVE_OPCODES) {
            val move = instruction as? TwoRegisterInstruction
                ?: throw PatchException("NewX post-options icon move has no registers at instruction $index")
            if (move.registerA == trackedRegister) {
                trackedRegister = move.registerB
                continue
            }
        }
        val writesTrackedRegister = when (instruction.opcode) {
            Opcode.SGET_OBJECT -> (instruction as? OneRegisterInstruction)?.registerA == trackedRegister
            Opcode.IGET_OBJECT,
            Opcode.MOVE_RESULT_OBJECT,
            -> (instruction as? OneRegisterInstruction)?.registerA == trackedRegister ||
                (instruction as? TwoRegisterInstruction)?.registerA == trackedRegister
            else -> false
        }
        if (writesTrackedRegister) break
    }
    throw PatchException(
        "NewX post-options icon assignment has no reaching field for v$register before instruction $untilIndex",
    )
}

private fun List<Instruction>.resolveFieldReadOrNull(
    register: Int,
    untilIndex: Int,
): FieldReference? =
    try {
        resolveFieldRead(register, untilIndex)
    } catch (_: PatchException) {
        null
    }

context(_: BytecodePatchContext)
private fun resolveKotlinUnitField(): FieldReference {
    val match =
        requireSingleMatch(
            "Kotlin Unit initializer",
            Fingerprint(
                definingClass = "Lkotlin/Unit;",
                name = "<clinit>",
                returnType = "V",
                parameters = emptyList(),
                filters =
                    listOf(
                        fieldAccess(
                            opcode = Opcode.SPUT_OBJECT,
                            definingClass = "Lkotlin/Unit;",
                            type = "Lkotlin/Unit;",
                        ),
                    ),
            ).matchAll(),
        )
    return match.instructionMatches.single().instruction.getReference<FieldReference>()
        ?: throw PatchException("Kotlin Unit singleton field was not found")
}

private fun requireSingleMatch(label: String, matches: Collection<Match>): Match {
    if (matches.size == 1) return matches.single()
    throw PatchException(
        "Expected one $label match, found ${matches.size}: " +
            matches.joinToString { it.originalMethod.toString() },
    )
}
