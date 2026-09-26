package app.crimera.patches.newx.misc.postoptions

import app.crimera.patches.newx.misc.extension.newXInitHook
import app.crimera.patches.newx.models.resolvedNewXInlineActionModels
import app.crimera.patches.newx.models.newXInlineActionModelResolutionPatch
import app.crimera.patches.newx.settings.newXSettingsPatch
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.utils.requireExactlyOne
import app.morphe.patcher.Fingerprint
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
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.util.getReference
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
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

/** The options list is the third declared parameter of the state constructor, so it lives in `p3`. */
private const val OPTIONS_LIST_PARAMETER_REGISTER_OFFSET = 3

internal const val BROWSE_OBJECT_ACTION = "None"
internal const val SHARE_IMAGE_ACTION = "ViewDebugDialog"
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
    val stateConstructor = resolveStateConstructor().method
    // Handler calls are chained on the options-list parameter, in registration order.
    val listRegister = stateConstructor.p0Register + OPTIONS_LIST_PARAMETER_REGISTER_OFFSET
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

context(_: BytecodePatchContext)
private fun resolveStateConstructor(): Match {
    val stateMatch = requireSingleMatch("NewX post-options state", PostOptionsStateFingerprint.matchAll())
    return requireSingleMatch(
        "NewX post-options state constructor",
        Fingerprint(
            classFingerprint = PostOptionsStateFingerprint,
            name = "<init>",
            returnType = "V",
            parameters =
                listOf(
                    "Z",
                    "L",
                    "Ljava/util/List;",
                    "Ljava/util/Map;",
                    "Lkotlinx/coroutines/flow/",
                    "Lkotlin/jvm/functions/Function1;",
                    "L",
                    "L",
                ),
        ).matchAll(),
    ).also {
        check(it.originalClassDef.type == stateMatch.originalClassDef.type)
    }
}

context(_: BytecodePatchContext)
private fun injectLabelsAndIcons(contributions: List<PostOptionContribution>) {
    val stateType =
        requireSingleMatch("NewX post-options state", PostOptionsStateFingerprint.matchAll())
            .originalClassDef.type
    val renderer =
        requireSingleMatch(
            "NewX post-options label renderer",
            Fingerprint(
                filters =
                    listOf(
                        fieldAccess(
                            opcode = Opcode.IGET_OBJECT,
                            definingClass = stateType,
                            type = "Ljava/util/Map;",
                        ),
                        methodCall(
                            opcode = Opcode.INVOKE_INTERFACE,
                            definingClass = "Ljava/util/Map;",
                            name = "get",
                            parameters = listOf("Ljava/lang/Object;"),
                            returnType = "Ljava/lang/Object;",
                            location = MatchAfterImmediately(),
                        ),
                        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
                    ),
            ).matchAll(),
        )
    val mapGet = renderer.instructionMatches[1].instruction
    val labelResult = renderer.instructionMatches[2].instruction as? OneRegisterInstruction
        ?: throw PatchException("NewX post-options Map.get has no object result register")
    val actionRegister = mapGet.registersUsed.getOrNull(1)
        ?: throw PatchException("NewX post-options Map.get has no action register")

    val iconAssignmentCandidates =
        renderer.method.instructions.withIndex().filter { (index, instruction) ->
            instruction.opcode in OBJECT_MOVE_OPCODES &&
                index < renderer.instructionMatches[1].index &&
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
    val insertionIndex = renderer.instructionMatches[2].index + 1
    renderer.method.insertHook(
        index = insertionIndex,
        excludedRegisters = listOf(actionRegister, labelResult.registerA, iconResultRegister),
        // A branch into the label lookup must run the rewrite too, or the custom options keep
        // their upstream label and icon.
        relocateBranchTargets = true,
    ) {
        contributions.forEach { contribution ->
            invokeStatic(
                methodReference("${contribution.handlerDescriptor}->$LABEL_FOR_SIGNATURE"),
                actionRegister,
                labelResult.registerA,
            )
            moveResult(labelResult.registerA, STRING_DESCRIPTOR)
        }

        // The icon checks run in registration order and share one dead low register; the
        // emitted formats (`if-eqz` 21t, `move-result` 11x, `sget-object` 21c) all encode it.
        val iconCheckRegister = scratchRegister()
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
                parameters = listOf(presenter.originalClassDef.type, "L", "L", "L", "L", "L"),
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
    val drawableId = getResourceId(ResourceType.DRAWABLE, resourceName)
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
