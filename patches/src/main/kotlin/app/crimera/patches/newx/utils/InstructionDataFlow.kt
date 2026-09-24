package app.crimera.patches.newx.utils

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

internal val INTEGER_MOVE_OPCODES =
    setOf(Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16)

internal val INTEGER_LITERAL_OPCODES =
    setOf(Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST, Opcode.CONST_HIGH16)

internal val REGISTER_WRITE_OPCODES =
    setOf(
        Opcode.CHECK_CAST,
        Opcode.CONST_STRING,
        Opcode.CONST_STRING_JUMBO,
        Opcode.IGET_OBJECT,
        Opcode.INSTANCE_OF,
        Opcode.MOVE_RESULT,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.NEW_ARRAY,
        Opcode.NEW_INSTANCE,
        Opcode.SGET_OBJECT,
    )

internal val OBJECT_MOVE_OPCODES =
    setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)

internal fun Instruction.destinationRegisterOrNull(): Int? {
    if (opcode in OBJECT_MOVE_OPCODES || opcode in INTEGER_MOVE_OPCODES) {
        return (this as? TwoRegisterInstruction)?.registerA
    }
    if (opcode !in REGISTER_WRITE_OPCODES && opcode !in INTEGER_LITERAL_OPCODES) return null
    return (this as? OneRegisterInstruction)?.registerA
}

/** Collects every integer literal that can reach [register] before [instructionIndex]. */
internal fun List<Instruction>.resolveIntegerLiterals(
    instructionIndex: Int,
    register: Int,
): Set<Int> {
    var trackedRegister = register
    val literals = linkedSetOf<Int>()
    for (index in instructionIndex - 1 downTo 0) {
        val instruction = this[index]
        if (instruction.opcode in INTEGER_MOVE_OPCODES) {
            val move = instruction as? TwoRegisterInstruction ?: continue
            if (move.registerA != trackedRegister) continue
            trackedRegister = move.registerB
            continue
        }
        if (instruction.opcode in INTEGER_LITERAL_OPCODES) {
            if ((instruction as? OneRegisterInstruction)?.registerA != trackedRegister) continue
            (instruction as? NarrowLiteralInstruction)?.narrowLiteral?.let(literals::add)
            continue
        }
    }
    return literals
}

/** Traces the single integer literal that reaches [register] on the current path. */
internal fun List<Instruction>.resolveIntegerLiteralOnCurrentPath(
    instructionIndex: Int,
    register: Int,
): Int? {
    var trackedRegister = register
    for (index in instructionIndex - 1 downTo 0) {
        val instruction = this[index]
        if (instruction.opcode in INTEGER_MOVE_OPCODES) {
            val move = instruction as? TwoRegisterInstruction ?: return null
            if (move.registerA != trackedRegister) continue
            trackedRegister = move.registerB
            continue
        }
        if (instruction.opcode in INTEGER_LITERAL_OPCODES) {
            if ((instruction as? OneRegisterInstruction)?.registerA != trackedRegister) continue
            return (instruction as? NarrowLiteralInstruction)?.narrowLiteral
        }
        if (instruction.destinationRegisterOrNull() == trackedRegister) return null
    }
    return null
}

/**
 * Resolves the single constant reaching [register] on the current path, following integer and
 * object moves alike. Kotlin lowers a null reference argument to a zero constant that reaches the
 * call through `move-object` aliases, so object moves must be followed to prove a zero/null value.
 */
internal fun List<Instruction>.resolveConstantOnCurrentPath(
    instructionIndex: Int,
    register: Int,
): Int? {
    var trackedRegister = register
    for (index in instructionIndex - 1 downTo 0) {
        val instruction = this[index]
        if (instruction.opcode in INTEGER_MOVE_OPCODES || instruction.opcode in OBJECT_MOVE_OPCODES) {
            val move = instruction as? TwoRegisterInstruction ?: return null
            if (move.registerA != trackedRegister) continue
            trackedRegister = move.registerB
            continue
        }
        if (instruction.opcode in INTEGER_LITERAL_OPCODES) {
            if ((instruction as? OneRegisterInstruction)?.registerA != trackedRegister) continue
            return (instruction as? NarrowLiteralInstruction)?.narrowLiteral
        }
        if (instruction.destinationRegisterOrNull() == trackedRegister) return null
    }
    return null
}

/** Follows object moves to prove which value finally reaches [targetRegister]. */
internal fun List<Instruction>.valueReachesRegister(
    valueIndex: Int,
    valueRegister: Int,
    targetIndex: Int,
    targetRegister: Int,
): Boolean {
    val aliases = linkedSetOf(valueRegister)
    for (index in valueIndex + 1 until targetIndex) {
        val instruction = this[index]
        if (instruction.opcode in OBJECT_MOVE_OPCODES) {
            val move = instruction as? TwoRegisterInstruction ?: return false
            if (move.registerA in aliases) aliases.remove(move.registerA)
            if (move.registerB in aliases) aliases.add(move.registerA)
            continue
        }
        instruction.destinationRegisterOrNull()?.let(aliases::remove)
    }
    return targetRegister in aliases
}

internal fun Instruction.writesObjectRegister(register: Int): Boolean {
    if (opcode in OBJECT_MOVE_OPCODES) {
        return (this as? TwoRegisterInstruction)?.registerA == register
    }
    if (opcode == Opcode.SGET_OBJECT) {
        return (this as? OneRegisterInstruction)?.registerA == register
    }
    return false
}
