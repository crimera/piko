/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.reels

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal fun syncPausedReelSeek(
    eligible: MethodReference,
    stateStore: FieldReference,
    getState: MethodReference,
    pausedField: FieldReference,
    pauseSetter: MethodReference,
) {
    val itemType = eligible.parameterTypes.single().toString()
    val match = Fingerprint(
        definingClass = eligible.definingClass,
        parameters = listOf("I", "Z", "Z"), returnType = "Z",
        filters = listOf(
            string("android_purge_26_q3_ClipsVideoPlayerController_seekToCurrentPlayer"),
            string("android_purge_26_q2_ClipsStandaloneAdapterDataSource_setCustomStartTimeMs"),
            fieldAccess(definingClass = itemType, type = "I", opcode = Opcode.IPUT),
            methodCall(parameters = listOf("I", "Z", "Z"), returnType = "V", opcode = Opcode.INVOKE_INTERFACE),
            fieldAccess(definingClass = eligible.definingClass, opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        ),
    ).match()
    val (_, _, write, seek, next) = match.instructionMatches
    val (position, item) = write.instruction.registersUsed
    val (player, target, flag) = seek.instruction.registersUsed
    val (scratch, viewer) = next.instruction.registersUsed
    val instructions = match.method.implementation!!.instructions
    val registers = setOf(scratch, flag, viewer, item, target)
    // The seek's receiver is overwritten next, and its first flag is dead after this call.
    require(scratch == player && target == position && registers.size == 5 && registers.all { it in 0..15 } &&
        (write.index + 1 until seek.index).none { item in instructions[it].registersUsed } &&
        instructions.drop(seek.index + 1).none { flag in it.registersUsed } &&
        pauseSetter.definingClass == stateStore.type && pauseSetter.parameterTypes == listOf(itemType, pausedField.type))

    // Seeking while paused updates the custom start, but leaves the original pause time in the snapshot.
    match.method.addInstructionsWithLabels(next.index, """
        invoke-direct {v$viewer, v$item}, $eligible
        move-result v$scratch
        if-eqz v$scratch, :done
        iget-object v$flag, v$viewer, $stateStore
        invoke-virtual {v$flag, v$item}, $getState
        move-result-object v$scratch
        iget-object v$scratch, v$scratch, $pausedField
        if-eqz v$scratch, :done
        invoke-static {v$target}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
        move-result-object v$scratch
        invoke-virtual {v$flag, v$item, v$scratch}, $pauseSetter
    """, ExternalLabel("done", instructions[next.index]))
}
