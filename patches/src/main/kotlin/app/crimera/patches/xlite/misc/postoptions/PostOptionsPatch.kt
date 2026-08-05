package app.crimera.patches.xlite.misc.postoptions

import app.crimera.patches.xlite.misc.extension.xLiteInitHook
import app.crimera.patches.xlite.settings.xLiteSettingsPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.IdentityHashMap
import java.util.LinkedHashMap

private const val POST_ACTION_TYPE = "Lcom/x/models/PostActionType;"
private const val POST_OPTIONS_STATE_PREFIX = "PostOptionsState(showOptionsDialog="
private const val POST_OPTIONS_LIST_PREFIX = ", options="
private const val URT_POST = "Lcom/x/models/timelines/items/UrtTimelinePost;"
private const val CONTEXT = "Landroid/content/Context;"
private const val XLITE_UTILS = "Lapp/morphe/extension/xlite/utils/XLiteUtils;"

internal const val BROWSE_OBJECT_ACTION = "None"
internal const val SHARE_IMAGE_ACTION = "ViewDebugDialog"

/** The setter block around the confirmed action read is bounded to avoid unrelated state writes. */
private const val ACTION_STATE_LOOKBACK_INSTRUCTIONS = 12

/** X-Lite icon resource initialization emits the field assignment within this small block. */
private const val ICON_FIELD_INITIALIZATION_WINDOW = 4

private data class PostOptionContribution(
    val handlerDescriptor: String,
    val actionName: String,
    val iconResourceName: String,
    val order: Int,
)

private object PostOptionContributionIndex {
    private val contributions =
        IdentityHashMap<BytecodePatchContext, LinkedHashMap<String, PostOptionContribution>>()

    @Synchronized
    fun register(context: BytecodePatchContext, contribution: PostOptionContribution) {
        val contextContributions = contributions.getOrPut(context) { linkedMapOf() }
        val existing = contextContributions[contribution.handlerDescriptor]
        check(existing == null || existing == contribution) {
            "Conflicting X-Lite post-option contribution: ${contribution.handlerDescriptor}"
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
                type = URT_POST,
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

internal fun BytecodePatchBuilder.xLitePostOption(
    handlerDescriptor: String,
    actionName: String,
    iconResourceName: String,
    order: Int,
) {
    dependsOn(
        xLitePostOptionContributionPatch(
            PostOptionContribution(
                handlerDescriptor = handlerDescriptor,
                actionName = actionName,
                iconResourceName = iconResourceName,
                order = order,
            ),
        ),
    )
}

private fun xLitePostOptionContributionPatch(contribution: PostOptionContribution) =
    bytecodePatch(default = false) {
        dependsOn(xLitePostOptionsPatch)

        execute {
            PostOptionContributionIndex.register(this, contribution)
        }
    }

private val xLitePostOptionsPatch =
    bytecodePatch(default = false) {
        dependsOn(xLiteSettingsPatch)

        execute {
            PostOptionContributionIndex.clear(this)
            xLiteInitHook.fingerprint.method.addInstruction(
                0,
                "invoke-static/range {p0 .. p0}, $XLITE_UTILS->initialize(Landroid/content/Context;)V",
            )
        }

        finalize {
            val contributions = PostOptionContributionIndex.takeSnapshot(this)
            if (contributions.isEmpty()) throw PatchException("No X-Lite post-menu options were registered")

            validateActionCarriers(contributions)
            injectOptionList(contributions)
            injectLabelsAndIcons(contributions)
            injectActionHandlers(contributions)
        }
    }

context(context: BytecodePatchContext)
private fun validateActionCarriers(contributions: List<PostOptionContribution>) {
    val actionType = context.mutableClassDefBy(POST_ACTION_TYPE)
    val missing =
        contributions
            .map(PostOptionContribution::actionName)
            .distinct()
            .filter { actionName ->
                actionType.fields.none { field -> field.name == actionName && field.type == POST_ACTION_TYPE }
            }
    if (missing.isNotEmpty()) {
        throw PatchException("Missing X-Lite post-menu action carriers: ${missing.joinToString()}")
    }

    val duplicates =
        contributions
            .groupingBy(PostOptionContribution::actionName)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
    if (duplicates.isNotEmpty()) {
        throw PatchException("Duplicate X-Lite post-menu action carriers: ${duplicates.joinToString()}")
    }
}

context(_: BytecodePatchContext)
private fun injectOptionList(contributions: List<PostOptionContribution>) {
    val stateConstructor = resolveStateConstructor()
    val instructions =
        contributions.joinToString("\n") { contribution ->
            """
            invoke-static {p3}, ${contribution.handlerDescriptor}->addOption(Ljava/util/List;)Ljava/util/List;
            move-result-object p3
            """.trimIndent()
        }
    stateConstructor.method.addInstructions(0, instructions)
}

context(_: BytecodePatchContext)
private fun resolveStateConstructor(): Match {
    val stateMatch = requireSingleMatch("X-Lite post-options state", PostOptionsStateFingerprint.matchAll())
    return requireSingleMatch(
        "X-Lite post-options state constructor",
        Fingerprint(
            classFingerprint = PostOptionsStateFingerprint,
            name = "<init>",
            returnType = "V",
            parameters =
                listOf(
                    "Z",
                    "Lcom/x/models/UserResult;",
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
        requireSingleMatch("X-Lite post-options state", PostOptionsStateFingerprint.matchAll())
            .originalClassDef.type
    val renderer =
        requireSingleMatch(
            "X-Lite post-options label renderer",
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
        ?: throw PatchException("X-Lite post-options Map.get has no object result register")
    val actionRegister = mapGet.registersUsed.getOrNull(1)
        ?: throw PatchException("X-Lite post-options Map.get has no action register")
    requireFourBitRegisters("label", actionRegister, labelResult.registerA)

    renderer.method.addInstructions(
        renderer.instructionMatches[2].index + 1,
        contributions.joinToString("\n") { contribution ->
            """
            invoke-static {v$actionRegister, v${labelResult.registerA}}, ${contribution.handlerDescriptor}->labelFor(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;
            move-result-object v${labelResult.registerA}
            """.trimIndent()
        },
    )

    val iconAssignmentInstruction =
        renderer.method.instructions.withIndex().lastOrNull { (index, instruction) ->
            instruction.opcode == Opcode.MOVE_OBJECT_FROM16 &&
                index < renderer.instructionMatches[1].index &&
                renderer.method.instructions.getOrNull(index + 1)?.opcode in
                setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)
        }?.value ?: throw PatchException("X-Lite post-options icon assignment was not found")
    val iconAssignment = iconAssignmentInstruction as? TwoRegisterInstruction
        ?: throw PatchException("X-Lite post-options icon assignment has no registers")
    val iconAssignmentIndex = renderer.method.instructions.indexOf(iconAssignmentInstruction)
    val iconSourceRegister = iconAssignment.registerB
    val iconResultRegister = iconAssignment.registerA
    val iconType = resolveIconType(renderer, iconAssignmentIndex, iconSourceRegister)
    requireFourBitRegisters("icon", actionRegister, iconSourceRegister)

    val iconFields = contributions.associateWith { resolveIconField(it.iconResourceName, iconType) }
    var iconContinuation = renderer.method.instructions[iconAssignmentIndex + 1]
    contributions.asReversed().forEachIndexed { index, contribution ->
        val insertionIndex = renderer.method.instructions.indexOf(iconContinuation)
        val label = "piko_xlite_post_option_icon_$index"
        renderer.method.addInstructionsWithLabels(
            insertionIndex,
            """
                invoke-static {v$actionRegister}, ${contribution.handlerDescriptor}->usesIcon(Ljava/lang/Object;)Z
                move-result v$iconSourceRegister
                if-eqz v$iconSourceRegister, :$label
                sget-object v$iconResultRegister, ${iconFields.getValue(contribution)}
            """.trimIndent(),
            ExternalLabel(label, iconContinuation),
        )
        iconContinuation = renderer.method.instructions[insertionIndex]
    }
}

private fun resolveIconType(
    renderer: Match,
    iconAssignmentIndex: Int,
    iconSourceRegister: Int,
): String =
    renderer.method.instructions
        .take(iconAssignmentIndex)
        .asReversed()
        .firstNotNullOfOrNull { instruction ->
            if (instruction.opcode != Opcode.SGET_OBJECT) return@firstNotNullOfOrNull null
            if ((instruction as? OneRegisterInstruction)?.registerA != iconSourceRegister) {
                return@firstNotNullOfOrNull null
            }
            instruction.getReference<FieldReference>()?.type
        } ?: throw PatchException("X-Lite post-options icon type was not found")

context(_: BytecodePatchContext)
private fun injectActionHandlers(contributions: List<PostOptionContribution>) {
    val presenter = requireSingleMatch("X-Lite post-options presenter", PostOptionsPresenterFingerprint.matchAll())
    val eventHandlerClass =
        requireSingleMatch(
            "X-Lite post-options event handler class",
            Fingerprint(
                name = "<init>",
                returnType = "V",
                parameters = listOf(presenter.originalClassDef.type, "L", "L", "L", "L", "L"),
            ).matchAll(),
        )
    val eventHandler =
        requireSingleMatch(
            "X-Lite post-options event handler",
            Fingerprint(
                classFingerprint = Fingerprint(definingClass = eventHandlerClass.originalClassDef.type),
                parameters = listOf("Ljava/lang/Object;"),
                returnType = "Ljava/lang/Object;",
            ).matchAll(),
        )
    val presenterField =
        eventHandler.originalClassDef.fields.singleOrNull { it.type == presenter.originalClassDef.type }
            ?: throw PatchException("X-Lite post-options event handler has no unique presenter field")
    val actionFieldInstruction = findActionFieldInstruction(eventHandler)
    val clickActionRegister =
        (actionFieldInstruction.value as? OneRegisterInstruction)?.registerA
            ?: throw PatchException("X-Lite confirmed post-option action has no register")
    val ordinalResultRegister =
        eventHandler.method.instructions.getOrNull(actionFieldInstruction.index + 2)
            ?.let { it as? OneRegisterInstruction }
            ?.registerA
            ?: throw PatchException("X-Lite post-option ordinal result has no register")
    requireFourBitRegisters("action", clickActionRegister, ordinalResultRegister)
    val unitField = resolveKotlinUnitField()

    var actionContinuation = eventHandler.method.instructions[actionFieldInstruction.index + 1]
    contributions.asReversed().forEachIndexed { index, contribution ->
        val insertionIndex = eventHandler.method.instructions.indexOf(actionContinuation)
        val label = "piko_xlite_post_option_action_$index"
        eventHandler.method.addInstructionsWithLabels(
            insertionIndex,
            """
                move-object/from16 v$ordinalResultRegister, p0
                iget-object v$ordinalResultRegister, v$ordinalResultRegister, $presenterField
                invoke-static {v$ordinalResultRegister, v$clickActionRegister}, ${contribution.handlerDescriptor}->handleOptionAction(Ljava/lang/Object;Ljava/lang/Object;)Z
                move-result v$ordinalResultRegister
                if-eqz v$ordinalResultRegister, :$label
                sget-object v$ordinalResultRegister, $unitField
                return-object v$ordinalResultRegister
            """.trimIndent(),
            ExternalLabel(label, actionContinuation),
        )
        actionContinuation = eventHandler.method.instructions[insertionIndex]
    }
}

private fun findActionFieldInstruction(eventHandler: Match) =
    eventHandler.method.instructions.withIndex().singleOrNull { (index, instruction) ->
        val field = instruction.getReference<FieldReference>()
        if (instruction.opcode != Opcode.IGET_OBJECT || field?.type != POST_ACTION_TYPE) {
            return@singleOrNull false
        }
        val ordinal = eventHandler.method.instructions.getOrNull(index + 1)?.getReference<MethodReference>()
        if (ordinal?.definingClass != "Ljava/lang/Enum;" || ordinal.name != "ordinal") {
            return@singleOrNull false
        }
        eventHandler.method.instructions
            .subList(maxOf(0, index - ACTION_STATE_LOOKBACK_INSTRUCTIONS), index)
            .any { previous ->
                previous.getReference<MethodReference>()?.let { reference ->
                    reference.name == "setValue" &&
                        reference.parameterTypes.map { it.toString() } == listOf("Ljava/lang/Object;") &&
                        reference.returnType == "V"
                } == true
            }
    } ?: throw PatchException("X-Lite confirmed post-option action extraction was not found uniquely")

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
            match.method.instructions
                .drop(literalIndex + 1)
                .take(ICON_FIELD_INITIALIZATION_WINDOW)
                .firstOrNull { instruction ->
                    instruction.opcode == Opcode.SPUT_OBJECT &&
                        instruction.getReference<FieldReference>()?.type == iconType
                }?.getReference<FieldReference>()
        }.distinctBy(FieldReference::toString)
    if (fields.size == 1) return fields.single()

    throw PatchException(
        "Expected one X-Lite $resourceName icon field, found ${fields.size}: ${fields.joinToString()}",
    )
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

private fun requireFourBitRegisters(label: String, vararg registers: Int) {
    if (registers.all { it in 0..15 }) return
    throw PatchException("X-Lite post-options $label registers exceed 4-bit encoding: ${registers.joinToString()}")
}
