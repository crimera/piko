/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.xlite.misc.mediatab

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.choice
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteSingleChoice
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.MEDIA_TAB_RESOLVER_DESCRIPTOR
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

/**
 * Targets the X-Lite combined profile timeline component constructor
 * (`com/x/profile/timeline/a` — used for the combined Posts+Highlights tab and the
 * combined Photos+Videos media tab). It seeds the selected sub-tab state with
 * `MutableStateFlow(primaryType)` — Videos for the combined media tab — which drives
 * both the header tab label/dropdown and the displayed media grid.
 *
 * The seed value is routed through [MediaTabResolver.getEnumDefault] so the configured
 * default (Photos) wins while every other tab keeps its stock primary type.
 */

/** True when [classDef] is a combined timeline component (implements the combined-timeline contract). */
private fun isCombinedTimelineComponent(classDef: ClassDef) =
    classDef.interfaces.any { it.startsWith("Lcom/x/profile/timeline/") }

/** True when a method takes `(primaryType, secondaryType, ...)` where both leading params share a type. */
private fun hasPrimarySecondaryPair(parameterTypes: List<CharSequence>) =
    parameterTypes.size >= 3 && parameterTypes[0] == parameterTypes[1]

/** True when [instruction] builds the `arrayOf(primaryType, secondaryType)` used for the grouped tab list. */
private fun isPairArray(instruction: Instruction, elementType: CharSequence) =
    (instruction.opcode == Opcode.FILLED_NEW_ARRAY || instruction.opcode == Opcode.FILLED_NEW_ARRAY_RANGE) &&
        ((instruction as? ReferenceInstruction)?.reference as? TypeReference)?.type == "[$elementType"

/** True when [instruction] is the `MutableStateFlow(seedValue)` factory call. */
private fun isFlowSeed(instruction: Instruction) =
    instruction.opcode == Opcode.INVOKE_STATIC &&
        ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let { reference ->
            reference.definingClass.startsWith("Lkotlinx/coroutines/flow/") &&
                reference.parameterTypes == listOf("Ljava/lang/Object;")
        } == true

private object XLiteCombinedProfileTimelineSeedFingerprint : Fingerprint(
    definingClass = "Lcom/x/profile/timeline/",
    custom = { method, classDef ->
        // morphe's Fingerprint `name` is only an identifier — it does NOT filter by
        // method name, so the constructor guard has to live here.
        method.name == "<init>" &&
            isCombinedTimelineComponent(classDef) &&
            hasPrimarySecondaryPair(method.parameterTypes) &&
            method.implementation?.instructions?.any {
                isPairArray(it, method.parameterTypes[0])
            } == true &&
            method.implementation?.instructions?.any { isFlowSeed(it) } == true
    },
)

@Suppress("unused")
val xLiteDefaultMediaTabPatch =
    bytecodePatch(
        name = "X-Lite: Customize default media tab",
        description = "Lets you choose the default sub-tab (Photos or Videos) for the X-Lite profile media tab.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        xLiteSingleChoice(
            id = "xlite.post_actions_media.media_tab_default",
            category = Categories.POST_ACTIONS_MEDIA,
            strings = settingStrings("piko_xlite_media_tab_default"),
            order = 100,
            defaultValue = "Photos",
            options =
                listOf(
                    choice("Photos", "piko_xlite_media_tab_photos"),
                    choice("Videos", "piko_xlite_media_tab_videos"),
                ),
        )

        execute {
            val match = XLiteCombinedProfileTimelineSeedFingerprint.scopedMatchAll()
            if (match.size != 1) {
                throw PatchException(
                    "Expected one combined profile timeline seed, found ${match.size}: " +
                        match.joinToString { it.originalMethod.toString() },
                )
            }

            val method = match.single().method
            val tabTypeDescriptor = method.parameterTypes[0]

            val methodInstructions = method.instructions
            val pairArrayIndex =
                methodInstructions.indexOfFirst { isPairArray(it, tabTypeDescriptor) }
            if (pairArrayIndex == -1) {
                throw PatchException("Missing combined profile tab array in the X-Lite media tab seed")
            }

            val seedInvokeIndex =
                (pairArrayIndex + 1 until methodInstructions.size).firstOrNull {
                    isFlowSeed(methodInstructions[it])
                } ?: -1
            if (seedInvokeIndex == -1) {
                throw PatchException("Missing MutableStateFlow seed in the X-Lite combined profile timeline component")
            }

            // invoke-static {vX}, ... -> format 35c; the first register holds the seed value.
            val seedValueRegister =
                method.getInstruction<FiveRegisterInstruction>(seedInvokeIndex).registerC

            // The seed value is replaced in place, so it must live in a scratch register — a
            // parameter register may be read again later in the constructor on other targets.
            val implementation = method.implementation
                ?: throw PatchException("Combined profile timeline component has no implementation")
            val parameterRegisterFloor = implementation.registerCount - method.parameterTypes.size
            if (seedValueRegister >= parameterRegisterFloor) {
                throw PatchException(
                    "Seed value lives in a parameter register (v$seedValueRegister); adjust the fingerprint for this target",
                )
            }

            method.addInstructions(
                seedInvokeIndex,
                """
                    invoke-static {v$seedValueRegister}, $MEDIA_TAB_RESOLVER_DESCRIPTOR->getEnumDefault(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v$seedValueRegister
                    check-cast v$seedValueRegister, $tabTypeDescriptor
                """.trimIndent(),
            )
        }
    }
