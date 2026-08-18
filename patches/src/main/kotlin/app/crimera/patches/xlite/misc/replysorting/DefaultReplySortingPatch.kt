/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.xlite.misc.replysorting

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.Groups
import app.crimera.patches.xlite.settings.choice
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.singleChoice
import app.crimera.patches.xlite.settings.toggle
import app.crimera.patches.xlite.settings.xLiteSettings
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.REPLY_SORTING_RESOLVER_DESCRIPTOR
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.opcode
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
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private fun isRelevanceSget(instruction: Instruction): Boolean {
    if (instruction.opcode != Opcode.SGET_OBJECT) return false
    val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: return false
    return field.name == "Relevance" && field.type == field.definingClass
}

/**
 * Targets the X-Lite Compose post detail timeline repository initialization, where
 * the initial ranking mode enum value is fetched for the factory call.
 */
// ALPHA PATH: explicit rankingMode label in the repository initializer.
// TODO: Remove this fingerprint when alpha compatibility is deprecated.
private object XLiteComposeReplySortingFingerprint : Fingerprint(
    definingClass = "Lcom/x/postdetail/",
    filters =
        listOf(
            string("rankingMode"),
            string("timelineRepository"),
        ),
)

/** Supports the beta path where the ranking parameter name is optimized away. */
// BETA PATH: ranking parameter name optimized away.
private object XLiteComposeReplySortingEnumFingerprint : Fingerprint(
    definingClass = "Lcom/x/postdetail/",
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
 * Targets the X-Lite Compose reply sorting selection handler callback where user changes
 * the reply sorting mode from the bottom sheet dialog.
 */
// ALPHA PATH: selection callback under the photo-editor owner.
// TODO: Remove this owner fingerprint when alpha compatibility is deprecated.
private object XLiteComposeReplySortingSelectionFingerprint : Fingerprint(
    // R8 places this synthetic callback in the preserved photo-editor owner package.
    // Do not search every FunctionReferenceImpl in the APK: those are unrelated callbacks.
    definingClass = "Lcom/x/photoeditor/",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    custom = { _, classDef ->
        classDef.superclass == "Lkotlin/jvm/internal/FunctionReferenceImpl;" &&
            classDef.interfaces.contains("Lkotlin/jvm/functions/Function1;")
    },
    filters =
        listOf(
            string("defaultUrtTimelineComponent"),
            string("timelineRepository"),
        ),
)

/** The beta moves the same callback into the post-detail owner package. */
// BETA PATH: selection callback moved under the post-detail owner.
private object XLiteComposeReplySortingPostDetailSelectionFingerprint : Fingerprint(
    definingClass = "Lcom/x/postdetail/",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    custom = { _, classDef ->
        classDef.superclass == "Lkotlin/jvm/internal/FunctionReferenceImpl;" &&
            classDef.interfaces.contains("Lkotlin/jvm/functions/Function1;")
    },
    filters =
        listOf(
            string("defaultUrtTimelineComponent"),
            string("timelineRepository"),
        ),
)

/**
 * Targets the Compose reply sorting UI state initializer (the lambda passed to
 * rememberSaveable that seeds the button label and sheet selection with
 * `mutableStateOf(TimelineRankingMode.Relevance)`). Must be seeded with the
 * configured default so the UI matches the repository's ranking mode.
 */
// ALPHA PATH: separate Relevance-seeded Compose state lambda.
// TODO: Remove this fingerprint when alpha compatibility is deprecated.
private object XLiteComposeReplySortingUiStateFingerprint : Fingerprint(
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
            // mutableStateOf: androidx.compose.runtime static factory taking Object and
            // returning a runtime state type. Matched on the stable descriptor/package
            // parts because the short class names (p5, k3) are R8-obfuscated and can
            // shift between Compose runtime versions.
            instructions?.any { ins ->
                ins.opcode == Opcode.INVOKE_STATIC &&
                    ((ins as? ReferenceInstruction)?.reference as? MethodReference)
                        ?.definingClass?.startsWith("Landroidx/compose/runtime/") == true
            } == true
    },
)

/**
 * Beta keeps the reply-sort selection state in a shared Function0 used by the post action row.
 * Its default branch creates mutableStateOf(TimelineRankingMode.Relevance) through a synthetic
 * selector in the preserved Twitter model-core package.
 */
// BETA PATH: shared synthetic state initializer used by the post action row.
private object XLiteComposeReplySortingBetaUiStateFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/model/core/",
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                name = "Relevance",
            ),
            opcode(Opcode.INVOKE_STATIC, MatchAfterImmediately()),
            opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
            opcode(Opcode.RETURN_OBJECT, MatchAfterImmediately()),
        ),
    custom = { _, classDef ->
        classDef.interfaces.contains("Lkotlin/jvm/functions/Function0;") &&
            classDef.methods.any { method ->
                method.name == "<init>" && method.parameterTypes == listOf("I")
            }
    },
)

@Suppress("unused")
val xLiteDefaultReplySortingPatch =
    bytecodePatch(
        name = "X-Lite: Customize default reply sorting",
        description = "Lets you choose the default reply sorting order for X-Lite post detail.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        xLiteSettings {
            category(Categories.POST_ACTIONS_MEDIA) {
                group(Groups.REPLY_SORTING) {
                    singleChoice(
                        id = "xlite.timeline.default_reply_sorting",
                        strings = settingStrings("piko_xlite_default_reply_sorting"),
                        order = 100,
                        defaultValue = "Relevance",
                        options =
                            listOf(
                                choice("Relevance", "piko_xlite_reply_sort_relevance"),
                                choice("Recency", "piko_xlite_reply_sort_recency"),
                                choice("Likes", "piko_xlite_reply_sort_likes"),
                            ),
                    )

                    toggle(
                        id = "xlite.timeline.remember_reply_sorting",
                        strings = settingStrings("piko_xlite_remember_reply_sorting"),
                        order = 200,
                        defaultValue = false,
                    )
                }
            }
        }

        execute {
            // Patch the Compose post detail timeline repository initialization
            val matches =
                listOf(
                    // ALPHA PATH: explicit rankingMode initializer.
                    XLiteComposeReplySortingFingerprint.scopedMatchAllOrNull().orEmpty(),
                    // BETA PATH: optimized-away ranking parameter initializer.
                    XLiteComposeReplySortingEnumFingerprint.scopedMatchAllOrNull().orEmpty(),
                ).flatten()
                    .distinctBy { it.originalMethod.toString() }
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite Compose reply sorting initializer across known shapes, " +
                        "found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            val match = matches.single()
            val method = match.method
            val instructions = method.instructions
            val firstMatchIndex =
                match.instructionMatches.firstOrNull()?.index
                    ?: throw PatchException("Missing reply sorting initializer fingerprint instruction")
            val firstMatchedInstruction = instructions.getOrNull(firstMatchIndex)
            if (firstMatchedInstruction?.opcode == Opcode.SGET_OBJECT &&
                !isRelevanceSget(firstMatchedInstruction)
            ) {
                throw PatchException(
                    "Reply sorting initializer matched an unexpected sget-object: " +
                        firstMatchedInstruction,
                )
            }

            // Alpha matches the semantic label after the enum load; beta matches the exact
            // TimelineRankingMode.Relevance sget-object. Never fall back to an arbitrary prior
            // sget-object: beta has a LimitedActionType.Reply load immediately before it.
            val rankingModeSgetIndices =
                if (firstMatchedInstruction?.let { isRelevanceSget(it) } == true) {
                    listOf(firstMatchIndex)
                } else {
                    (0 until firstMatchIndex).filter { index ->
                        isRelevanceSget(instructions[index])
                    }
                }
            if (rankingModeSgetIndices.size != 1) {
                throw PatchException(
                    "Expected one TimelineRankingMode.Relevance sget-object in reply sorting " +
                        "initializer, found ${rankingModeSgetIndices.size}: " +
                        rankingModeSgetIndices.joinToString(),
                )
            }

            val targetSgetIndex = rankingModeSgetIndices.single()
            val sgetInstruction = method.getInstruction<OneRegisterInstruction>(targetSgetIndex)
            val sortRegister = sgetInstruction.registerA
            val fieldRef = sgetInstruction.getReference<FieldReference>()
                ?: throw PatchException("Missing field reference in reply sorting sget-object")
            if (fieldRef.name != "Relevance" || fieldRef.type != fieldRef.definingClass) {
                throw PatchException("Unexpected reply sorting enum field: $fieldRef")
            }
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

            // Patch the Compose reply sorting selection handler to remember the last choice
            val selectionMatches =
                listOf(
                    // ALPHA PATH: photo-editor owner.
                    XLiteComposeReplySortingSelectionFingerprint.scopedMatchAllOrNull().orEmpty(),
                    // BETA PATH: post-detail owner.
                    XLiteComposeReplySortingPostDetailSelectionFingerprint
                        .scopedMatchAllOrNull()
                        .orEmpty(),
                ).flatten()
                    .distinctBy { it.originalMethod.toString() }
            if (selectionMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite Compose reply sorting selection handler across known " +
                        "owners, found ${selectionMatches.size}: " +
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

            // The beta callback casts p1 directly. Alpha moves p1 into a local before the cast
            // because the same Function1 method contains a packed switch of unrelated callbacks.
            // Resolve the cast from parameter data flow, not from the receiver cast before the
            // null-property guard.
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
            val uiStateMatches =
                listOf(
                    // ALPHA PATH: dedicated Compose state lambda.
                    XLiteComposeReplySortingUiStateFingerprint.scopedMatchAllOrNull().orEmpty(),
                    // BETA PATH: shared synthetic mutableStateOf initializer.
                    XLiteComposeReplySortingBetaUiStateFingerprint
                        .scopedMatchAllOrNull()
                        .orEmpty(),
                ).flatten()
                    .distinctBy { it.originalMethod.toString() }
            if (uiStateMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite Compose reply sorting UI state initializer across " +
                        "known shapes, found ${uiStateMatches.size}: " +
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

