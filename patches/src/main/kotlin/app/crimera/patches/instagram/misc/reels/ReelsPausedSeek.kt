/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.reels

import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal fun syncPausedReelSeek(
    eligible: MethodReference,
    stateStore: FieldReference,
    reader: ReelStateReader,
    playerItem: MethodReference,
    pausedField: FieldReference,
    pauseSetter: MethodReference,
) {
    val itemType = eligible.parameterTypes.single().toString()
    val match = Fingerprint(
        definingClass = eligible.definingClass,
        parameters = listOf("I", "Z", "Z"), returnType = "Z",
        filters = listOf(
            methodCall(definingClass = reader.provider.returnType, parameters = emptyList(), returnType = itemType),
            fieldAccess(definingClass = itemType, type = "I", opcode = Opcode.IPUT),
            methodCall(parameters = listOf("I", "Z", "Z"), returnType = "V", opcode = Opcode.INVOKE_INTERFACE),
            fieldAccess(definingClass = eligible.definingClass, opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        ),
    ).match()
    val (currentItem, write, seek, next) = match.instructionMatches
    val (player, target) = seek.instruction.registersUsed
    val viewer = next.instruction.registersUsed.last()
    require(seek.methodReference.definingClass == playerItem.definingClass &&
        target == write.instruction.registersUsed.first() && listOf(viewer, player, target).all { it in 0..15 } &&
        pauseSetter.definingClass == stateStore.type && pauseSetter.parameterTypes == listOf(itemType, pausedField.type))

    // Seeking while paused updates the custom start, but leaves the original pause time in the snapshot.
    val sync = addReelMethod(eligible.definingClass, "pikoUpdatePausedSeek", listOf(playerItem.definingClass, "I"),
        "V", registers = 6, instructions = """
            $PREF_CALL_DESCRIPTOR->resumeReelsAfterScrolling()Z
            move-result v2
            if-eqz v2, :done
            invoke-virtual {p0}, ${reader.provider}
            move-result-object v0
            invoke-interface {v0}, ${currentItem.methodReference}
            move-result-object v1
            if-eqz v1, :done
            invoke-interface {p1}, $playerItem
            move-result-object v2
            if-ne v1, v2, :done
            invoke-direct {p0, v1}, $eligible
            move-result v2
            if-eqz v2, :done
            invoke-interface {v0, v1}, ${reader.snapshot}
            move-result-object v2
            iget-object v2, v2, $pausedField
            if-eqz v2, :done
            invoke-static {p2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
            move-result-object v2
            iget-object v0, p0, $stateStore
            invoke-interface {v0, v1, v2}, $pauseSetter
            :done
            return-void
        """)
    match.method.addInstruction(next.index, "invoke-direct {v$viewer, v$player, v$target}, $sync")
}
