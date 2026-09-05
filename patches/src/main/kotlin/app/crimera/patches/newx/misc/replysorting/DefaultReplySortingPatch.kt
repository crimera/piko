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
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.getReference
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

/**
 * Targets the NewX Compose post-detail timeline repository initialization that seeds
 * TimelineRankingMode.Relevance before the repository factory call.
 */
private object NewXComposeReplySortingFingerprint : Fingerprint(
    definingClass = "Lcom/x/postdetail/",
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                name = "Relevance",
            ),
            string("rankingMode"),
            string("timelineRepository"),
        ),
)

/**
 * Targets the synthetic FunctionReference that handles a reply-sorting choice from the sheet.
 * Both supported builds place this callback in the payments transaction package.
 */
private object NewXComposeReplySortingSelectionFingerprint : Fingerprint(
    definingClass = "Lcom/x/payments/transaction/",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    custom = { method, classDef ->
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
    definingClass = "Lcom/x/ui/common/",
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                name = "Relevance",
            ),
        ),
    custom = { method, classDef ->
        val instructions = method.implementation?.instructions
        classDef.interfaces.contains("Lkotlin/jvm/functions/Function0;") &&
            instructions?.any { ins ->
                ins.opcode == Opcode.INVOKE_STATIC &&
                    ((ins as? ReferenceInstruction)?.reference as? MethodReference)
                        ?.definingClass?.startsWith("Landroidx/compose/runtime/") == true
            } == true
    },
)

@Suppress("unused")
val newXDefaultReplySortingPatch =
    bytecodePatch(
        name = "NewX: Customize default reply sorting",
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
            val matches = NewXComposeReplySortingFingerprint.scopedMatchAllOrNull().orEmpty()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX Compose reply sorting initializer, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            val match = matches.single()
            val method = match.method
            val targetSgetIndex =
                match.instructionMatches.firstOrNull()?.index
                    ?: throw PatchException("Missing reply sorting initializer fingerprint instruction")
            val sgetInstruction = method.getInstruction<OneRegisterInstruction>(targetSgetIndex)
            if (!isRelevanceSget(sgetInstruction)) {
                throw PatchException("Reply sorting initializer did not match Relevance sget-object")
            }
            val sortRegister = sgetInstruction.registerA
            val fieldRef = sgetInstruction.getReference<FieldReference>()
                ?: throw PatchException("Missing field reference in reply sorting sget-object")
            val enumClass = fieldRef.definingClass

            method.addInstructions(
                targetSgetIndex + 1,
                """
                    const-class v$sortRegister, $enumClass
                    invoke-static/range {v$sortRegister .. v$sortRegister}, $REPLY_SORTING_RESOLVER_DESCRIPTOR->getEnumDefault(Ljava/lang/Class;)Ljava/lang/Object;
                    move-result-object v$sortRegister
                    check-cast v$sortRegister, $enumClass
                """.trimIndent(),
            )

            // Patch the Compose reply-sorting selection handler to remember the last choice.
            val selectionMatches = NewXComposeReplySortingSelectionFingerprint
                .scopedMatchAllOrNull()
                .orEmpty()
            if (selectionMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX Compose reply sorting selection handler, found " +
                        "${selectionMatches.size}: " +
                        selectionMatches.joinToString { it.originalMethod.toString() },
                )
            }
            val selectionMatch = selectionMatches.single()
            val selectionMethod = selectionMatch.method
            val selectionImplementation =
                selectionMethod.implementation
                    ?: throw PatchException("Reply sorting selection handler has no implementation")
            val defaultUrtIndex =
                selectionMatch.instructionMatches.firstOrNull()?.index
                    ?: throw PatchException("Missing selection handler semantic anchor")
            if (selectionMethod.parameterTypes.singleOrNull() != "Ljava/lang/Object;") {
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
            if (parameterCheckCastIndices.isEmpty()) {
                throw PatchException(
                    "Missing selection-parameter check-cast before reply sorting handler guard",
                )
            }

            // The semantic anchor bounds the branch. The last parameter-derived cast in that
            // branch is the selected TimelineRankingMode cast; receiver casts are excluded.
            val checkCastIndex = parameterCheckCastIndices.maxOrNull()
                ?: throw PatchException("Missing selection-parameter check-cast")
            val checkCastInstruction =
                selectionInstructions.getOrNull(checkCastIndex) as? OneRegisterInstruction
                    ?: throw PatchException("Reply sorting selection check-cast has no register")
            val checkedType = checkCastInstruction.getReference<TypeReference>()?.type
            if (checkedType == null || checkedType == "Ljava/lang/Object;") {
                throw PatchException("Reply sorting selection parameter has no concrete enum cast")
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
            if (uiStateMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX Compose reply sorting UI state initializer, found " +
                        "${uiStateMatches.size}: " +
                        uiStateMatches.joinToString { it.originalMethod.toString() },
                )
            }

            val uiStateMatch = uiStateMatches.single()
            val uiStateMethod = uiStateMatch.method
            val uiStateIndex =
                uiStateMatch.instructionMatches.firstOrNull()?.index
                    ?: throw PatchException("Missing reply sorting UI state fingerprint instruction")
            val uiStateInstruction = uiStateMethod.getInstruction<OneRegisterInstruction>(uiStateIndex)
            if (!isRelevanceSget(uiStateInstruction)) {
                throw PatchException("Reply sorting UI state did not match Relevance sget-object")
            }
            val uiStateRegister = uiStateInstruction.registerA
            val uiStateField = uiStateInstruction.getReference<FieldReference>()
                ?: throw PatchException("Missing field reference in reply sorting UI state sget-object")
            val uiStateEnumClass = uiStateField.definingClass

            uiStateMethod.addInstructions(
                uiStateIndex + 1,
                """
                    const-class v$uiStateRegister, $uiStateEnumClass
                    invoke-static/range {v$uiStateRegister .. v$uiStateRegister}, $REPLY_SORTING_RESOLVER_DESCRIPTOR->getEnumDefault(Ljava/lang/Class;)Ljava/lang/Object;
                    move-result-object v$uiStateRegister
                    check-cast v$uiStateRegister, $uiStateEnumClass
                """.trimIndent(),
            )
        }
    }

