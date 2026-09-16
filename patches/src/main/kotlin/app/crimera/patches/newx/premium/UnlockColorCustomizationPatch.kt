/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.premium

import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val DISPLAY_SETTINGS_SCOPE = "Lcom/x/settings/accessibility/display/"
private const val FUNCTION6_DESCRIPTOR = "Lkotlin/jvm/functions/Function6;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val BOOLEAN_DESCRIPTOR = "Ljava/lang/Boolean;"
private const val CONTINUATION_DESCRIPTOR = "Lkotlin/coroutines/Continuation;"

/**
 * The display settings state combines the premium flow with the theme preferences before the
 * Compose screen consumes it. The fifth Function6 argument is the all-tier premium result; it is
 * copied into DisplaySettingsState and controls whether the color selector is enabled.
 */
private fun premiumResultIndices(
    instructions: List<Instruction>,
    constructedType: String,
): List<Int> =
    instructions.indices.filter { invokeIndex ->
        val invoke = instructions[invokeIndex]
        if (invoke.opcode != Opcode.INVOKE_VIRTUAL) return@filter false

        val reference = invoke.getReference<MethodReference>() ?: return@filter false
        if (
            reference.definingClass != BOOLEAN_DESCRIPTOR ||
                reference.name != "booleanValue" ||
                reference.returnType != "Z" ||
                reference.parameterTypes.isNotEmpty()
        ) {
            return@filter false
        }

        val booleanCast = instructions.getOrNull(invokeIndex - 1)
        if (
            booleanCast?.opcode != Opcode.CHECK_CAST ||
                booleanCast.getReference<TypeReference>()?.type != BOOLEAN_DESCRIPTOR
        ) {
            return@filter false
        }

        val castRegister = booleanCast.registersUsed.firstOrNull()
        val invokeRegister = invoke.registersUsed.firstOrNull()
        if (castRegister == null || castRegister != invokeRegister) return@filter false

        val resultIndex = invokeIndex + 1
        val result = instructions.getOrNull(resultIndex)
        if (result?.opcode != Opcode.MOVE_RESULT) return@filter false
        if (result.registersUsed.firstOrNull() == null) return@filter false

        val continuationCast = instructions.getOrNull(invokeIndex + 2)
        if (
            continuationCast?.opcode != Opcode.CHECK_CAST ||
                continuationCast.getReference<TypeReference>()?.type != CONTINUATION_DESCRIPTOR
        ) {
            return@filter false
        }

        val newLambda = instructions.getOrNull(invokeIndex + 3)
        newLambda?.opcode == Opcode.NEW_INSTANCE &&
            newLambda.getReference<TypeReference>()?.type == constructedType
    }

private fun isDisplaySettingsStateCombiner(method: Method, classDef: ClassDef): Boolean {
    if (!classDef.interfaces.contains(FUNCTION6_DESCRIPTOR)) return false

    val instructions = method.implementation?.instructions?.toList().orEmpty()
    return premiumResultIndices(instructions, classDef.type).size == 1
}

private object NewXDisplaySettingsPremiumStateFingerprint : Fingerprint(
    definingClass = DISPLAY_SETTINGS_SCOPE,
    returnType = OBJECT_DESCRIPTOR,
    parameters = List(6) { OBJECT_DESCRIPTOR },
    custom = { method, classDef -> isDisplaySettingsStateCombiner(method, classDef) },
)

private fun unlockColorCustomization(match: Match) {
    val instructions = match.method.instructions.toList()
    val resultIndices = premiumResultIndices(instructions, match.originalClassDef.type)
    if (resultIndices.size != 1) {
        throw PatchException(
            "Expected one NewX display-settings premium result, found ${resultIndices.size}: " +
                match.originalMethod,
        )
    }

    val resultIndex = resultIndices.single()
    val resultRegister =
        instructions[resultIndex].registersUsed.firstOrNull()
            ?: throw PatchException("NewX display-settings premium result has no destination register")
    val stateWriteIndices =
        instructions.indices.filter { index ->
            index > resultIndex && instructions[index].opcode == Opcode.IPUT_BOOLEAN
        }
    if (stateWriteIndices.size != 1) {
        throw PatchException(
            "Expected one NewX display-settings premium state write, found " +
                "${stateWriteIndices.size}: ${match.originalMethod}",
        )
    }

    val stateWriteIndex = stateWriteIndices.single()
    val constantOpcode = if (resultRegister <= 15) "const/4" else "const/16"

    // Override the value immediately before it is stored; never split invoke/move-result.
    match.method.addInstruction(
        stateWriteIndex,
        "$constantOpcode v$resultRegister, 0x1",
    )
}

@Suppress("unused")
val newXUnlockColorCustomizationPatch =
    bytecodePatch(
        name = "NewX: Unlock color customization",
        description = "Unlocks the built-in app color selector for non-Premium users.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        execute {
            val matches = NewXDisplaySettingsPremiumStateFingerprint.scopedMatchAllOrNull().orEmpty()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX display-settings state combiner, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }
            unlockColorCustomization(matches.single())
        }
    }

