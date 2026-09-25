/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.replysorting

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.singleChoice
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.REPLY_SORTING_RESOLVER_DESCRIPTOR
import app.crimera.patches.newx.utils.requireAtMostOne
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private fun isRelevanceSget(instruction: Instruction): Boolean {
    if (instruction.opcode != Opcode.SGET_OBJECT) return false
    val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: return false
    return field.name == "Relevance" && field.type == field.definingClass
}

private fun isComposeStateCall(instruction: Instruction, inputRegister: Int): Boolean {
    if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
        return false
    }
    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return reference.definingClass.startsWith("Landroidx/compose/runtime/") &&
        reference.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;") &&
        reference.returnType.toString().startsWith("Landroidx/compose/runtime/") &&
        instruction.registersUsed == listOf(inputRegister)
}

private fun isComposeStateInitializer(instructions: List<Instruction>, sgetIndex: Int): Boolean {
    if (sgetIndex < 0 || sgetIndex + 3 >= instructions.size) return false
    val sget = instructions[sgetIndex] as? OneRegisterInstruction ?: return false
    if (!isRelevanceSget(sget)) return false

    val inputRegister = sget.registerA
    if (!isComposeStateCall(instructions[sgetIndex + 1], inputRegister)) return false

    val moveResult = instructions[sgetIndex + 2] as? OneRegisterInstruction ?: return false
    if (moveResult.opcode != Opcode.MOVE_RESULT_OBJECT || moveResult.registerA != inputRegister) {
        return false
    }

    val returnInstruction = instructions[sgetIndex + 3] as? OneRegisterInstruction ?: return false
    return returnInstruction.opcode == Opcode.RETURN_OBJECT &&
        returnInstruction.registerA == inputRegister
}

private const val ENUM_DESCRIPTOR = "Ljava/lang/Enum;"

/**
 * Replaces the resolved `Relevance` seed in [register] with the configured default ranking mode
 * after [sgetIndex]. The register keeps its original type, so the consuming instruction range is
 * unchanged.
 */
private fun MutableMethod.insertReplySortingDefault(
    sgetIndex: Int,
    register: Int,
    enumClass: String,
) {
    addInstructions(
        sgetIndex + 1,
        """
            const-class v$register, $enumClass
            invoke-static/range {v$register .. v$register}, $REPLY_SORTING_RESOLVER_DESCRIPTOR->getEnumDefault(Ljava/lang/Class;)Ljava/lang/Object;
            move-result-object v$register
            check-cast v$register, $enumClass
        """.trimIndent(),
    )
}

/**
 * Targets the NewX Compose post-detail timeline repository initialization that seeds
 * TimelineRankingMode.Relevance before the repository factory call.
 */
private object NewXComposeReplySortingFingerprint : Fingerprint(
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    custom = { method, classDef ->
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
            classDef.superclass == "Lkotlin/coroutines/jvm/internal/SuspendLambda;" &&
            classDef.interfaces.contains("Lkotlin/jvm/functions/Function2;")
    },
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                name = "Relevance",
            ),
            string("timelineRepository"),
        ),
)

/**
 * Targets the synthetic FunctionReference that handles a reply-sorting choice from the sheet.
 * The callback owner and package are release-specific; resolve it from the stable Kotlin
 * function-reference shape and the semantic branch strings instead.
 */
private object NewXComposeReplySortingSelectionFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    custom = { method, classDef ->
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
            classDef.superclass == "Lkotlin/jvm/internal/FunctionReferenceImpl;" &&
            classDef.interfaces.contains("Lkotlin/jvm/functions/Function1;") &&
            method.implementation?.instructions?.any { instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference
                reference is StringReference && reference.string == "timelineRepository"
            } == true
    },
    filters =
        listOf(
            string("defaultUrtTimelineComponent"),
        ),
)

/**
 * Targets the Compose state initializer that seeds the reply-sorting sheet with
 * `mutableStateOf(TimelineRankingMode.Relevance)`.
 */
private object NewXComposeReplySortingUiStateFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                name = "Relevance",
            ),
        ),
    custom = { method, classDef ->
        val instructions = method.implementation?.instructions?.toList()
        classDef.interfaces.contains("Lkotlin/jvm/functions/Function0;") &&
            instructions?.withIndex()?.any { (index, _) ->
                isComposeStateInitializer(instructions, index)
            } == true
    },
)

/**
 * Targets the post-detail conversation prefetch seed added in 12.29. The ViewModel constructor
 * issues the initial conversation request with `TimelineRankingMode.Relevance` before the timeline
 * repository exists, so the repository seed is not authoritative on the first load. The capability
 * is identified by its feature-switch name rather than by an obfuscated owner.
 */
private object NewXComposeReplySortingPrefetchFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters =
        listOf(
            string("x_android_conversation_prefetch_enabled"),
        ),
)

@Suppress("unused")
val newXDefaultReplySortingPatch =
    bytecodePatch(
        name = "NewX: Set default reply sorting",
        description = "Lets you choose the default reply sorting order for NewX post detail.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        newXSettings {
            category(Categories.POST_ACTIONS_MEDIA) {
                group(Groups.REPLY_SORTING) {
                    singleChoice(
                        id = "newx.timeline.default_reply_sorting",
                        strings = settingStrings("piko_newx_default_reply_sorting"),
                        order = 100,
                        defaultValue = "Relevance",
                        options =
                            listOf(
                                choice("Relevance", "piko_newx_reply_sort_relevance"),
                                choice("Recency", "piko_newx_reply_sort_recency"),
                                choice("Likes", "piko_newx_reply_sort_likes"),
                            ),
                    )

                    toggle(
                        id = "newx.timeline.remember_reply_sorting",
                        strings = settingStrings("piko_newx_remember_reply_sorting"),
                        order = 200,
                        defaultValue = false,
                    )
                }
            }
        }

        execute {
            // Patch the Compose post-detail timeline repository initialization.
            val match =
                requireExactlyOne(
                    label = "NewX Compose reply sorting initializer",
                    candidates = NewXComposeReplySortingFingerprint.scopedMatchAllOrNull().orEmpty(),
                )
            val method = match.method
            val targetSgetCandidates = match.instructionMatches.filter { matchedInstruction ->
                isRelevanceSget(matchedInstruction.instruction)
            }
            val targetSget =
                requireExactlyOne(
                    label = "reply sorting initializer Relevance sget-object",
                    candidates = targetSgetCandidates,
                )
            val targetSgetIndex = targetSget.index
            val sgetInstruction = targetSget.instruction as? OneRegisterInstruction
                ?: throw PatchException("Reply sorting initializer Relevance sget-object has no register")
            val sortRegister = sgetInstruction.registerA
            val fieldRef = sgetInstruction.getReference<FieldReference>()
                ?: throw PatchException("Missing field reference in reply sorting sget-object")
            val enumClass = fieldRef.definingClass
            val enumDefinition =
                runCatching { mutableClassDefBy(enumClass) }.getOrNull()
                    ?: throw PatchException("Resolved reply sorting enum is missing: $enumClass")
            if (enumDefinition.superclass != ENUM_DESCRIPTOR) {
                throw PatchException(
                    "Resolved reply sorting type $enumClass is not an enum: " +
                        "superclass=${enumDefinition.superclass}",
                )
            }

            method.insertReplySortingDefault(targetSgetIndex, sortRegister, enumClass)

            // Patch the 12.29 conversation prefetch seed when the capability is present. Older
            // releases issue the initial conversation request through the repository seed alone.
            val prefetchMatch =
                requireAtMostOne(
                    label = "NewX Compose reply sorting conversation prefetch method",
                    candidates = NewXComposeReplySortingPrefetchFingerprint
                        .scopedMatchAllOrNull()
                        .orEmpty(),
                )
            if (prefetchMatch != null) {
                val prefetchMethod = prefetchMatch.method
                val prefetchSeeds =
                    prefetchMethod.instructions.withIndex().filter { (_, instruction) ->
                        val field = instruction.getReference<FieldReference>()
                        field?.definingClass == enumClass && isRelevanceSget(instruction)
                    }
                val prefetchSeed =
                    requireExactlyOne(
                        label = "NewX Compose reply sorting conversation prefetch seed for $enumClass",
                        candidates = prefetchSeeds,
                    )
                val prefetchSget = prefetchSeed.value as? OneRegisterInstruction
                    ?: throw PatchException("Reply sorting conversation prefetch seed has no register")
                val prefetchRegister = prefetchSget.registerA
                val feedsPrefetchRequest =
                    prefetchMethod.instructions.any { instruction ->
                        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@any false
                        reference.parameterTypes.contains(enumClass) &&
                            instruction.registersUsed.contains(prefetchRegister)
                    }
                if (!feedsPrefetchRequest) {
                    throw PatchException(
                        "Reply sorting conversation prefetch seed v$prefetchRegister does not feed a " +
                            "request whose parameters include $enumClass",
                    )
                }

                prefetchMethod.insertReplySortingDefault(
                    prefetchSeed.index,
                    prefetchRegister,
                    enumClass,
                )
            }

            // Patch the Compose reply-sorting selection handler to remember the last choice.
            val selectionMatch =
                requireExactlyOne(
                    label = "NewX Compose reply sorting selection handler",
                    candidates = NewXComposeReplySortingSelectionFingerprint
                        .scopedMatchAllOrNull()
                        .orEmpty(),
                )
            val selectionMethod = selectionMatch.method
            val selectionImplementation =
                selectionMethod.implementation
                    ?: throw PatchException("Reply sorting selection handler has no implementation")
            val defaultUrtCandidates = selectionMatch.instructionMatches.filter { matchedInstruction ->
                matchedInstruction.instruction.getReference<StringReference>()?.string ==
                    "defaultUrtTimelineComponent"
            }
            val defaultUrtAnchor =
                requireExactlyOne(
                    label = "selection handler defaultUrtTimelineComponent anchor",
                    candidates = defaultUrtCandidates,
                )
            val defaultUrtIndex = defaultUrtAnchor.index
            val selectionParameter =
                requireExactlyOne(
                    label = "reply sorting selection handler parameter",
                    candidates = selectionMethod.parameterTypes,
                )
            if (selectionParameter != "Ljava/lang/Object;") {
                throw PatchException(
                    "Unexpected reply sorting selection handler parameters: " +
                        selectionMethod.parameterTypes,
                )
            }
            val parameterRegisterCount = selectionMethod.parameterTypes.sumOf { type ->
                if (type == "J" || type == "D") 2 else 1
            }
            val selectedParameterRegister = selectionImplementation.registerCount - parameterRegisterCount
            if (selectedParameterRegister < 0) {
                throw PatchException("Invalid reply sorting selection handler register layout")
            }

            // The merged callback may move p1 into a local before casting it. Resolve the cast
            // from parameter data flow rather than the receiver cast.
            val selectionInstructions = selectionMethod.instructions
            val directParameterCheckCasts =
                selectionInstructions.withIndex()
                    .filter { (index, instruction) ->
                        index < defaultUrtIndex &&
                            instruction.opcode == Opcode.CHECK_CAST &&
                            (instruction as? OneRegisterInstruction)?.registerA == selectedParameterRegister
                    }
                    .map { it.index }
            val objectMoveOpcodes =
                setOf(
                    Opcode.MOVE_OBJECT,
                    Opcode.MOVE_OBJECT_FROM16,
                    Opcode.MOVE_OBJECT_16,
                )
            val movedParameterCheckCasts =
                selectionInstructions.withIndex().mapNotNull { (index, instruction) ->
                    if (index >= defaultUrtIndex || instruction.opcode !in objectMoveOpcodes) return@mapNotNull null
                    val move = instruction as? TwoRegisterInstruction ?: return@mapNotNull null
                    if (move.registerB != selectedParameterRegister) return@mapNotNull null
                    val nextInstruction = selectionInstructions.getOrNull(index + 1) ?: return@mapNotNull null
                    if (nextInstruction.opcode != Opcode.CHECK_CAST) return@mapNotNull null
                    val checkCast = nextInstruction as? OneRegisterInstruction ?: return@mapNotNull null
                    if (checkCast.registerA != move.registerA) return@mapNotNull null
                    index + 1
                }
            val parameterCheckCastIndices =
                (directParameterCheckCasts + movedParameterCheckCasts).distinct()
            val rankingModeCheckCastIndices = parameterCheckCastIndices.filter { index ->
                selectionInstructions[index]
                    .getReference<TypeReference>()
                    ?.type == enumClass
            }
            val checkCastIndex =
                requireExactlyOne(
                    label = "selection-parameter cast matching resolved reply ranking enum $enumClass",
                    candidates = rankingModeCheckCastIndices,
                )
            val checkCastInstruction =
                selectionInstructions[checkCastIndex] as? OneRegisterInstruction
                    ?: throw PatchException("Reply sorting selection check-cast has no register")
            val checkedType = checkCastInstruction.getReference<TypeReference>()?.type
            if (checkedType != enumClass) {
                throw PatchException(
                    "Reply sorting selection parameter cast type $checkedType does not match " +
                        "resolved reply ranking enum $enumClass",
                )
            }
            val selectedRegister = checkCastInstruction.registerA

            selectionMethod.addInstructions(
                checkCastIndex + 1,
                """
                    invoke-static/range {v$selectedRegister .. v$selectedRegister}, $REPLY_SORTING_RESOLVER_DESCRIPTOR->remember(Ljava/lang/Object;)V
                """.trimIndent(),
            )

            // Patch the Compose reply sorting UI state initializer so the button label
            // and sheet selection reflect the configured default instead of Relevance.
            val uiStateMatches = NewXComposeReplySortingUiStateFingerprint
                .scopedMatchAllOrNull()
                .orEmpty()
            val uiStateCandidates =
                uiStateMatches.flatMap { candidate ->
                    candidate.instructionMatches
                        .filter { matchedInstruction ->
                            val field = matchedInstruction.instruction.getReference<FieldReference>()
                            field?.definingClass == enumClass &&
                                isComposeStateInitializer(
                                    candidate.method.instructions,
                                    matchedInstruction.index,
                                )
                        }
                        .map { matchedInstruction -> candidate to matchedInstruction }
                }
            val uiStateCandidate =
                requireExactlyOne(
                    label = "NewX Compose reply sorting UI state Relevance initializer for $enumClass",
                    candidates = uiStateCandidates,
                )
            val uiStateMatch = uiStateCandidate.first
            val uiStateMethod = uiStateMatch.method
            val uiStateIndex = uiStateCandidate.second.index
            val uiStateInstruction = uiStateCandidate.second.instruction as? OneRegisterInstruction
                ?: throw PatchException("Reply sorting UI state Relevance sget-object has no register")
            val uiStateRegister = uiStateInstruction.registerA
            val uiStateField = uiStateInstruction.getReference<FieldReference>()
                ?: throw PatchException("Missing field reference in reply sorting UI state sget-object")
            if (uiStateField.definingClass != enumClass || !isRelevanceSget(uiStateInstruction)) {
                throw PatchException(
                    "Reply sorting UI state field ${uiStateField} does not match resolved " +
                        "reply ranking enum $enumClass",
                )
            }

            uiStateMethod.insertReplySortingDefault(uiStateIndex, uiStateRegister, enumClass)
        }
    }
