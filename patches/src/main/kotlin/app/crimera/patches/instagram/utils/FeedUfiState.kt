/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.utils

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

context(context: BytecodePatchContext)
internal fun hideFeedUfiStates(preferenceCall: String, vararg labels: String) {
    val feedMatch = Fingerprint(
        name = "toString",
        returnType = "Ljava/lang/String;",
        parameters = emptyList(),
        filters = listOf(string("MediaUfiUiState(mediaForActionHandler=")) + labels.map { string(it) },
    ).matchSingle()
    val stateInstructions = feedMatch.method.implementation!!.instructions
    val fields = feedMatch.instructionMatches.drop(1).map { label ->
        val appendIndex = ((label.index + 1) until stateInstructions.size)
            .takeWhile { stateInstructions[it].getReference<StringReference>() == null }
            .singleOrNull {
                stateInstructions[it].getReference<MethodReference>()?.toString() ==
                    "Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;"
            } ?: throw PatchException("Could not resolve the feed UFI state value")
        val appendRegisters = stateInstructions[appendIndex].registersUsed
        if (appendRegisters.size != 2) throw PatchException("Unexpected feed UFI append registers")
        var register = appendRegisters[1]
        var before = appendIndex
        var field: FieldReference? = null
        // Large state objects move field values through temporary registers in toString().
        while (field == null) {
            val readIndex = (before - 1 downTo 0).firstOrNull {
                stateInstructions[it].opcode.setsRegister() &&
                    stateInstructions[it].registersUsed.firstOrNull() == register
            } ?: throw PatchException("Could not trace the feed UFI state field")
            val read = stateInstructions[readIndex]
            when (read.opcode) {
                Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16 -> {
                    val registers = read.registersUsed
                    if (registers.size != 2) throw PatchException("Unexpected feed UFI move registers")
                    register = registers[1]
                    before = readIndex
                }
                Opcode.IGET_BOOLEAN -> {
                    field = read.getReference<FieldReference>()
                    if (field?.type != "Z" || field.definingClass != feedMatch.classDef.type) {
                        throw PatchException("Unexpected feed UFI state field")
                    }
                }
                else -> throw PatchException("Unexpected feed UFI state instruction")
            }
        }
        field
    }
    if (fields.distinctBy { it.toString() }.size != labels.size) {
        throw PatchException("Expected separate feed UFI button and count fields")
    }
    val stateClass = context.mutableClassDefBy(feedMatch.classDef.type)
    for (field in fields) {
        val writes = stateClass.methods.filter { it.name == "<init>" }.flatMap { constructor ->
            constructor.implementation?.instructions.orEmpty().mapIndexedNotNull { index, instruction ->
                if (instruction.opcode == Opcode.IPUT_BOOLEAN &&
                    instruction.getReference<FieldReference>()?.toString() == field.toString()
                ) constructor to index else null
            }
        }
        val (constructor, index) = writes.singleOrNull()
            ?: throw PatchException("Expected one feed UFI state initialization, found ${writes.size}")
        val write = constructor.getInstruction(index)
        val registers = write.registersUsed
        if (registers.size != 2) throw PatchException("Unexpected feed UFI field registers")
        val scratch = constructor.findFreeRegister(index, registers)
        if (scratch > 255) throw PatchException("No encodable register for the feed UFI setting")
        constructor.addInstructionsWithLabels(
            index,
            """
            $preferenceCall
            move-result v$scratch
            if-eqz v$scratch, :original
            const/4 v${registers[0]}, 0x0
            """.trimIndent(),
            ExternalLabel("original", write),
        )
    }
}
