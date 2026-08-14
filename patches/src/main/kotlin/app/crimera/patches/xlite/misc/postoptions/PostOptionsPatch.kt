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

private const val POST_OPTIONS_STATE_PREFIX = "PostOptionsState(showOptionsDialog="
private const val POST_OPTIONS_LIST_PREFIX = ", options="
private const val CONTEXT = "Landroid/content/Context;"
private const val XLITE_UTILS = "Lapp/morphe/extension/xlite/utils/XLiteUtils;"

internal const val BROWSE_OBJECT_ACTION = "None"
internal const val SHARE_IMAGE_ACTION = "ViewDebugDialog"

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

private object PostActionTypeFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    filters =
        listOf(
            app.morphe.patcher.string("ViewDebugDialog"),
            app.morphe.patcher.string("AddToBookmarks"),
        ),
)

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
    val postActionType =
        PostActionTypeFingerprint.matchOrNull()?.originalClassDef?.type
            ?: "Lcom/x/models/PostActionType;"
    val actionType = context.mutableClassDefBy(postActionType)
    val missing =
        contributions
            .map(PostOptionContribution::actionName)
            .distinct()
            .filter { actionName ->
                actionType.fields.none { field -> field.name == actionName && field.type == postActionType }
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

    val iconAssignmentInstruction =
        renderer.method.instructions.withIndex().lastOrNull { (index, instruction) ->
            instruction.opcode in setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16) &&
                index < renderer.instructionMatches[1].index &&
                renderer.method.instructions.getOrNull(index + 1)?.opcode in
                setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)
        }?.value ?: throw PatchException("X-Lite post-options icon assignment was not found")
    val iconAssignment = iconAssignmentInstruction as? TwoRegisterInstruction
        ?: throw PatchException("X-Lite post-options icon assignment has no registers")
    val iconResultRegister = iconAssignment.registerA

    val iconType =
        renderer.method.instructions
            .take(renderer.instructionMatches[1].index)
            .asReversed()
            .firstNotNullOfOrNull { instruction ->
                if (instruction.opcode != Opcode.SGET_OBJECT) return@firstNotNullOfOrNull null
                instruction.getReference<FieldReference>()?.type
            } ?: throw PatchException("X-Lite post-options icon type was not found")

    val tempRegister = if (actionRegister == 0) 1 else 0
    requireFourBitRegisters("icon", actionRegister, tempRegister)

    val iconFields = contributions.associateWith { resolveIconField(it.iconResourceName, iconType) }

    var insertionIndex = renderer.instructionMatches[2].index + 1
    contributions.forEach { contribution ->
        renderer.method.addInstructions(
            insertionIndex,
            """
                invoke-static {v$actionRegister, v${labelResult.registerA}}, ${contribution.handlerDescriptor}->labelFor(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;
                move-result-object v${labelResult.registerA}
            """.trimIndent(),
        )
        insertionIndex += 2
    }

    contributions.forEachIndexed { index, contribution ->
        val label = "piko_xlite_post_option_icon_$index"
        val continuationInstruction = renderer.method.instructions[insertionIndex]
        renderer.method.addInstructionsWithLabels(
            insertionIndex,
            """
                invoke-static {v$actionRegister}, ${contribution.handlerDescriptor}->usesIcon(Ljava/lang/Object;)Z
                move-result v$tempRegister
                if-eqz v$tempRegister, :$label
                sget-object v$iconResultRegister, ${iconFields.getValue(contribution)}
            """.trimIndent(),
            ExternalLabel(label, continuationInstruction),
        )
        insertionIndex = renderer.method.instructions.indexOf(continuationInstruction)
    }
}

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

    val ordinalIndex = eventHandler.method.instructions.indexOfFirst { instruction ->
        val methodRef = instruction.getReference<MethodReference>() ?: return@indexOfFirst false
        instruction.opcode == Opcode.INVOKE_VIRTUAL &&
            methodRef.definingClass == "Ljava/lang/Enum;" &&
            methodRef.name == "ordinal"
    }
    if (ordinalIndex == -1) {
        throw PatchException("X-Lite confirmed post-option action extraction was not found (no Enum.ordinal)")
    }
    val ordinalInstruction = eventHandler.method.instructions[ordinalIndex]
    val clickActionRegister = ordinalInstruction.registersUsed.firstOrNull()
        ?: throw PatchException("X-Lite confirmed post-option action has no register")

    val tempRegister = if (clickActionRegister == 0) 1 else 0
    requireFourBitRegisters("action", clickActionRegister, tempRegister)
    val unitField = resolveKotlinUnitField()

    var actionContinuation = ordinalInstruction
    contributions.asReversed().forEachIndexed { index, contribution ->
        val insertionIndex = eventHandler.method.instructions.indexOf(actionContinuation)
        val label = "piko_xlite_post_option_action_$index"
        eventHandler.method.addInstructionsWithLabels(
            insertionIndex,
            """
                move-object/from16 v$tempRegister, p0
                iget-object v$tempRegister, v$tempRegister, $presenterField
                invoke-static {v$tempRegister, v$clickActionRegister}, ${contribution.handlerDescriptor}->handleOptionAction(Ljava/lang/Object;Ljava/lang/Object;)Z
                move-result v$tempRegister
                if-eqz v$tempRegister, :$label
                sget-object v$tempRegister, $unitField
                return-object v$tempRegister
            """.trimIndent(),
            ExternalLabel(label, actionContinuation),
        )
        actionContinuation = eventHandler.method.instructions[insertionIndex]
    }
}

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
