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
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Targets the X-Lite Compose post detail timeline repository initialization, where
 * the initial ranking mode enum value is fetched for the factory call.
 */
private object XLiteComposeReplySortingFingerprint : Fingerprint(
    filters =
        listOf(
            methodCall(smali = "Lcom/x/models/PostIdentifier;->getValue()J"),
            string("timelineRepository"),
        ),
)

/**
 * Targets the X-Lite Compose reply sorting selection handler callback where user changes
 * the reply sorting mode from the bottom sheet dialog.
 */
private object XLiteComposeReplySortingSelectionFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
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
private object XLiteComposeReplySortingUiStateFingerprint : Fingerprint(
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

@Suppress("unused")
val xLiteDefaultReplySortingPatch =
    bytecodePatch(
        name = "X-Lite: Customize default reply sorting",
        description = "Lets you choose the default reply sorting order for X-Lite post detail.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        xLiteSettings {
            category(Categories.TIMELINE) {
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
            val matches = XLiteComposeReplySortingFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite Compose reply sorting initializer, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            val match = matches.single()
            val method = match.method
            val getValueIndex = match.instructionMatches.first().index
            val instructions = method.instructions
            val relativeIndex = instructions.drop(getValueIndex).indexOfFirst { inst ->
                if (inst.opcode != Opcode.SGET_OBJECT) false
                else {
                    val ref = (inst as? ReferenceInstruction)?.reference as? FieldReference
                    ref != null && !ref.definingClass.startsWith("Lkotlin/coroutines/")
                }
            }
            val targetSgetIndex = if (relativeIndex != -1) getValueIndex + relativeIndex else method.indexOfFirstInstructionOrThrow(getValueIndex, Opcode.SGET_OBJECT)

            val sgetInstruction = method.getInstruction<OneRegisterInstruction>(targetSgetIndex)
            val sortRegister = sgetInstruction.registerA
            val fieldRef = sgetInstruction.getReference<FieldReference>()
                ?: throw PatchException("Missing field reference in reply sorting sget-object")
            val enumClass = fieldRef.definingClass

            method.addInstructions(
                targetSgetIndex + 1,
                """
                    const-class v$sortRegister, $enumClass
                    invoke-static {v$sortRegister}, $REPLY_SORTING_RESOLVER_DESCRIPTOR->getEnumDefault(Ljava/lang/Class;)Ljava/lang/Object;
                    move-result-object v$sortRegister
                    check-cast v$sortRegister, $enumClass
                """.trimIndent(),
            )

            // Patch the Compose reply sorting selection handler to remember the last choice
            val selectionMatches = XLiteComposeReplySortingSelectionFingerprint.matchAll()
            if (selectionMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite Compose reply sorting selection handler, found ${selectionMatches.size}: " +
                        selectionMatches.joinToString { it.originalMethod.toString() },
                )
            }
            val selectionMethod = selectionMatches.single().method
            val checkCastIndex = selectionMethod.indexOfFirstInstructionOrThrow(Opcode.CHECK_CAST)

            selectionMethod.addInstructions(
                checkCastIndex + 1,
                """
                    invoke-static {p1}, $REPLY_SORTING_RESOLVER_DESCRIPTOR->remember(Ljava/lang/Object;)V
                """.trimIndent(),
            )

            // Patch the Compose reply sorting UI state initializer so the button label
            // and sheet selection reflect the configured default instead of Relevance.
            val uiStateMatches = XLiteComposeReplySortingUiStateFingerprint.matchAll()
            if (uiStateMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite Compose reply sorting UI state initializer, found ${uiStateMatches.size}: " +
                        uiStateMatches.joinToString { it.originalMethod.toString() },
                )
            }
            val uiStateMethod = uiStateMatches.single().method
            val uiStateIndex = uiStateMethod.instructions.indexOfFirst { ins ->
                ins.opcode == Opcode.SGET_OBJECT &&
                    ((ins as? ReferenceInstruction)?.reference as? FieldReference)?.name == "Relevance"
            }
            if (uiStateIndex == -1) {
                throw PatchException("Missing relevance sget-object in X-Lite reply sorting UI state initializer")
            }

            val uiStateInstruction = uiStateMethod.getInstruction<OneRegisterInstruction>(uiStateIndex)
            val uiStateRegister = uiStateInstruction.registerA
            val uiStateEnumClass = uiStateInstruction.getReference<FieldReference>()
                ?.definingClass
                ?: throw PatchException("Missing field reference in reply sorting UI state sget-object")

            uiStateMethod.addInstructions(
                uiStateIndex + 1,
                """
                    const-class v$uiStateRegister, $uiStateEnumClass
                    invoke-static {v$uiStateRegister}, $REPLY_SORTING_RESOLVER_DESCRIPTOR->getEnumDefault(Ljava/lang/Class;)Ljava/lang/Object;
                    move-result-object v$uiStateRegister
                    check-cast v$uiStateRegister, $uiStateEnumClass
                """.trimIndent(),
            )
        }
    }

