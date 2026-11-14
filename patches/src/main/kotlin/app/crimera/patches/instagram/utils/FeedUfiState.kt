/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.utils

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.writeRegister
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/**
 * State toString(), matching the boolean value appended after [label].
 */
private fun booleanStateToStringFingerprint(marker: String, label: String) = Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = buildList {
        add(string(marker))
        if (label != marker) add(string(label))
        // Large state objects first move the value out of a temporary register.
        add(methodCall(smali = "Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;", location = MatchAfterWithin(2)))
    },
)

/**
 * Feed UFI state boolean field printed after [label] in the state toString().
 */
context(_: BytecodePatchContext)
internal fun feedUfiStateField(label: String): FieldReference {
    return booleanStateField("MediaUfiUiState(mediaForActionHandler=", label)
}

context(_: BytecodePatchContext)
internal fun booleanStateField(marker: String, label: String): FieldReference {
    val toStringMatch = booleanStateToStringFingerprint(marker, label).match()
    val toString = toStringMatch.method
    var index = toStringMatch.instructionMatches.last().index
    var register = toStringMatch.instructionMatches.last().instruction.registersUsed[1]

    // Large state objects move field values through temporary registers in toString().
    while (true) {
        index = toString.indexOfFirstInstructionReversedOrThrow(index - 1) { writeRegister == register }
        val read = toString.getInstruction(index)
        when (read.opcode) {
            Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16 -> register = (read as TwoRegisterInstruction).registerB
            Opcode.IGET_BOOLEAN -> return read.getReference<FieldReference>()!!
            else -> throw PatchException("Unexpected boolean state instruction: ${read.opcode}")
        }
    }
}

/**
 * Forces the feed UFI state boolean fields printed after [labels] to false when [preferenceCall] returns true.
 */
context(_: BytecodePatchContext)
internal fun hideFeedUfiStates(preferenceCall: String, vararg labels: String) {
    labels.forEach { label ->
        val field = feedUfiStateField(label)
        val stateBuilderMatch = Fingerprint(
            returnType = field.definingClass,
            filters = listOf(fieldAccess(reference = field, opcode = Opcode.IPUT_BOOLEAN)),
        ).match()
        val stateBuilder = stateBuilderMatch.method
        val writeIndex = stateBuilderMatch.instructionMatches.first().index
        val write = stateBuilder.getInstruction(writeIndex)
        val registers = write.registersUsed
        val scratch = stateBuilder.findFreeRegister(writeIndex, registers)
        stateBuilder.addInstructionsWithLabels(
            writeIndex,
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
