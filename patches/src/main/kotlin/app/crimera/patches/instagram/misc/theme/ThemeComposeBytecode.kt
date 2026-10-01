/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.crimera.patches.shared.declaredParameterRegister
import app.crimera.patches.shared.parameterRegisterStart
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private data class SettingRowCalls(
    val dark: InvokeCall,
    val light: InvokeCall,
    val system: InvokeCall,
)

context(patchContext: BytecodePatchContext)
internal fun installComposeNativeThemeModeSync() {
    val method =
        DarkModeSectionFingerprint
            .matchAll(1..1)
            .single()
            .method
    val composerType =
        method.parameterTypes.firstOrNull()?.toString()
            ?: throw PatchException("Compose dark-mode section has no composer parameter")
    val composerRegister = parameterRegisterStart(method)
    val settingRows =
        findSettingRowCalls(
            method = method,
            composerType = composerType,
            composerRegister = composerRegister,
        )

    installComposeNativeThemeModeObservation(
        method = method,
        beforeIndex = settingRows.dark.index,
    )

    val callbackParameters =
        method.parameterTypes.withIndex().filter { (_, type) ->
            type.toString() == FUNCTION1_DESCRIPTOR
        }
    val callbackParameter =
        callbackParameters.singleOrNull()?.index
            ?: throw PatchException(
                "Expected one Compose native theme callback, found ${callbackParameters.size}",
            )
    val callbackRegister = declaredParameterRegister(method, callbackParameter)
    if (callbackRegister !in 0..0xf) {
        throw PatchException("Compose native theme callback requires a 4-bit register")
    }

    method.addInstructions(
        0,
        """
        invoke-static {v$callbackRegister}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->wrapNativeThemeCallback(Lkotlin/jvm/functions/Function1;)Lkotlin/jvm/functions/Function1;
        move-result-object v$callbackRegister
        """.trimIndent(),
    )
}

private fun installComposeNativeThemeModeObservation(
    method: MutableMethod,
    beforeIndex: Int,
) {
    val instructions = method.instructions
    val candidates =
        instructions.withIndex().mapNotNull { (index, instruction) ->
            if (
                index >= beforeIndex ||
                instruction.opcode !in
                setOf(
                    Opcode.INVOKE_INTERFACE,
                    Opcode.INVOKE_INTERFACE_RANGE,
                )
            ) {
                return@mapNotNull null
            }
            val reference =
                (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@mapNotNull null
            if (
                reference.name != "getInt" ||
                reference.parameterTypes.map(CharSequence::toString) !=
                listOf(STRING_DESCRIPTOR, "I") ||
                reference.returnType != "I"
            ) {
                return@mapNotNull null
            }
            val registers = instruction.registersUsed
            if (registers.size != 3 || index + 1 >= instructions.size) {
                return@mapNotNull null
            }
            val defaultRegister = registers[2]
            val defaultValue =
                instructions
                    .subList(maxOf(0, index - 8), index)
                    .asReversed()
                    .mapNotNull { previous ->
                        val register = (previous as? OneRegisterInstruction)?.registerA
                        val literal = previous as? NarrowLiteralInstruction
                        if (register == defaultRegister) literal?.narrowLiteral else null
                    }.firstOrNull()
            if (defaultValue != -1) {
                return@mapNotNull null
            }
            val result = instructions[index + 1]
            val resultRegister = (result as? OneRegisterInstruction)?.registerA
            if (result.opcode != Opcode.MOVE_RESULT || resultRegister !in 0..0xf) {
                return@mapNotNull null
            }
            index + 1 to resultRegister
        }
    val (resultIndex, resultRegister) =
        candidates.singleOrNull()
            ?: throw PatchException(
                "Expected one Compose native theme setting read, found ${candidates.size}",
            )
    method.addInstruction(
        resultIndex + 1,
        "invoke-static {v$resultRegister}, " +
            "Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->" +
            "observeComposeNativeThemeMode(I)V",
    )
}

private fun findSettingRowCalls(
    method: MutableMethod,
    composerType: String,
    composerRegister: Int,
): SettingRowCalls {
    val invokes = method.invokeCalls()
    val fourArgumentGroups =
        invokes
            .filter { call ->
                val parameterTypes = call.reference.parameterTypes.map(CharSequence::toString)
                call.reference.returnType == "V" &&
                    call.reference.definingClass.isObjectDescriptor() &&
                    parameterTypes.size == 4 &&
                    parameterTypes[0] == composerType &&
                    parameterTypes.last() == STRING_DESCRIPTOR &&
                    call.registers.size == 4 &&
                    call.registers[0] == composerRegister
            }.groupBy { it.reference.toString() }
            .values
            .filter { it.size == 2 }

    val candidates =
        fourArgumentGroups.mapNotNull { group ->
            val ordered = group.sortedBy(InvokeCall::index)
            val reference = ordered[0].reference
            val parameterTypes = reference.parameterTypes.map(CharSequence::toString)
            val fiveArgumentTypes = parameterTypes + STRING_DESCRIPTOR
            val fiveArgumentCalls =
                invokes.filter { call ->
                    call.reference.definingClass == reference.definingClass &&
                        call.reference.returnType == reference.returnType &&
                        call.reference.parameterTypes.map(CharSequence::toString) ==
                        fiveArgumentTypes &&
                        call.registers.size == 5 &&
                        call.registers[0] == composerRegister
                }
            if (
                fiveArgumentCalls.size != 1 ||
                ordered[0].index >= ordered[1].index ||
                ordered[1].index >= fiveArgumentCalls[0].index
            ) {
                return@mapNotNull null
            }

            SettingRowCalls(
                dark = ordered[0],
                light = ordered[1],
                system = fiveArgumentCalls[0],
            )
        }

    if (candidates.size != 1) {
        throw PatchException(
            "Expected one ordered setting-row family with two 4-argument calls " +
                "and one 5-argument call, found ${candidates.size}",
        )
    }

    return candidates.single()
}
