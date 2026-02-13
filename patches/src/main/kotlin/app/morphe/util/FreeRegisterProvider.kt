/*
 * Copyright 2025 Morphe.
 * https://github.com/MorpheApp/morphe-patches/blob/95e285b9aaa3195fe49fe5326a416043348989e6/patches/src/main/kotlin/app/morphe/util/FreeRegisterProvider.kt
 *
 * File-Specific License Notice (GPLv3 Section 7 Additional Permission).
 *
 * This file is part of the Morphe patches project and is licensed under
 * the GNU General Public License version 3 (GPLv3), with the Additional
 * Terms under Section 7 described in the Morphe patches LICENSE file.
 *
 * https://www.gnu.org/licenses/gpl-3.0.html
 *
 * File-Specific Exception to Section 7b:
 * -------------------------------------
 * Section 7b (Attribution Requirement) of the Morphe patches LICENSE
 * does not apply to THIS FILE. Use of this file does NOT require any
 * user-facing, in-application, or UI-visible attribution.
 *
 * For this file only, attribution under Section 7b is satisfied by
 * retaining this comment block in the source code of this file.
 *
 * Distribution and Derivative Works:
 * ----------------------------------
 * This comment block MUST be preserved in all copies, distributions,
 * and derivative works of this file, whether in source or modified
 * form.
 *
 * All other terms of the Morphe Patches LICENSE, including Section 7c
 * (Project Name Restriction) and the GPLv3 itself, remain fully
 * applicable to this file.
 */

package app.morphe.util

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.util.FreeRegisterProvider.Companion.conditionalBranchOpcodes
import app.morphe.util.FreeRegisterProvider.Companion.logFreeRegisterSearch
import app.morphe.util.FreeRegisterProvider.Companion.returnOpcodes
import app.morphe.util.FreeRegisterProvider.Companion.switchOpcodes
import app.morphe.util.FreeRegisterProvider.Companion.unconditionalBranchOpcodes
import app.morphe.util.FreeRegisterProvider.Companion.writeOpcodes
import com.android.tools.smali.dexlib2.Format
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcode.*
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import java.util.EnumSet
import java.util.LinkedList
import kotlin.collections.count
import kotlin.collections.distinct
import kotlin.collections.first
import kotlin.collections.indexOfFirst
import kotlin.collections.intersect
import kotlin.collections.isNotEmpty
import kotlin.collections.minus
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.collections.sortedWith
import kotlin.collections.toList
import kotlin.collections.toMutableSet
import kotlin.collections.toSet
import kotlin.let
import kotlin.ranges.until

/**
 * Finds free registers at a specific index in a method.
 * Allows allocating multiple free registers for a given index.
 *
 * If you only need a single free register, instead use [findFreeRegister].
 *
 * @param index Index you need a use a free register at.
 * @param numberOfFreeRegistersNeeded The maximum number of free registers you may get using
 *                                    [FreeRegisterProvider.getFreeRegister].
 * @param registersToExclude Registers to exclude, and consider as used. For most use cases,
 *                           all registers used in injected code should be specified.
 *
 * @throws IllegalArgumentException If no free registers can be found at the given index.
 *                                  This includes unusual method indexes that read from every register
 *                                  before any registers are wrote to, or if a switch statement is
 *                                  encountered before any free registers are found.
 */
fun Method.getFreeRegisterProvider(index: Int, numberOfFreeRegistersNeeded: Int, registersToExclude: List<Int>) =
    FreeRegisterProvider(this, index, numberOfFreeRegistersNeeded, registersToExclude)

/**
 * Finds free registers at a specific index in a method.
 * Allows allocating multiple free registers for a given index.
 *
 * If you only need a single free register, instead use [findFreeRegister].
 *
 * @param index Index you need a use a free register at.
 * @param numberOfFreeRegistersNeeded The minimum free registers to find.
 * @param registersToExclude Registers to exclude, and consider as used. For most use cases,
 *                           all registers used in injected code should be specified.
 *
 * @throws IllegalArgumentException If no free registers can be found at the given index.
 *                                  This includes unusual method indexes that read from every register
 *                                  before any registers are wrote to, or if a switch statement is
 *                                  encountered before any free registers are found.
 */
fun Method.getFreeRegisterProvider(index: Int, numberOfFreeRegistersNeeded: Int, vararg registersToExclude: Int) =
    FreeRegisterProvider(this, index, numberOfFreeRegistersNeeded, *registersToExclude)

class FreeRegisterProvider internal constructor(
    val method: Method,
    index: Int,
    numberOfFreeRegistersNeeded: Int,
    registersToExclude: List<Int>
) {
    internal constructor(
        method: Method,
        index: Int,
        numberOfFreeRegistersNeeded: Int,
        vararg registersToExclude: Int
    ) : this(method, index, numberOfFreeRegistersNeeded, registersToExclude.toList())

    private var freeRegisters: MutableList<Int> = LinkedList(
        method.findFreeRegisters(index, numberOfFreeRegistersNeeded, registersToExclude)
    )

    private val originallyExcludedRegisters = registersToExclude
    private val allocatedFreeRegisters = mutableListOf<Int>()

    /**
     * Returns a free register and removes it from the available list.
     *
     * @return A free register number
     * @throws IllegalStateException if no free registers are available
     */
    fun getFreeRegister(): Int {
        if (freeRegisters.isEmpty()) {
            throw kotlin.IllegalStateException("No free registers available")
        }
        val register = freeRegisters.removeFirst()
        allocatedFreeRegisters.add(register)
        return register
    }

    /**
     * Returns all registers that have been allocated via [getFreeRegister].
     * This does not include the originally excluded registers.
     *
     * @return List of registers that have been allocated, in allocation order
     */
    fun getAllocatedFreeRegisters(): List<Int> = allocatedFreeRegisters.toList()

    /**
     * Returns all registers that are considered "in use" or unsafe to use. This includes the
     * excluded registers originally passed in, all registers that are unsuitable to use
     * (registers are read from by the original code), and all free registers previously provided
     * by this class using [getFreeRegister].
     *
     * @return List of all registers that are un-safe to use at this time.
     */
    fun getUsedAndUnAvailableRegisters(): List<Int> {
        val allRegisters = 0 until method.implementation!!.registerCount
        return (allocatedFreeRegisters + originallyExcludedRegisters + (allRegisters - freeRegisters.toSet()))
            .distinct()
    }

    /**
     * @return The number of free registers still available.
     */
    fun availableCount(): Int = freeRegisters.size

    /**
     * Checks if there are any free registers available.
     */
    fun hasFreeRegisters(): Boolean = freeRegisters.isNotEmpty()

    internal companion object {
        val conditionalBranchOpcodes: EnumSet<Opcode> = EnumSet.of(
            IF_EQ, IF_NE, IF_LT, IF_GE, IF_GT, IF_LE,
            IF_EQZ, IF_NEZ, IF_LTZ, IF_GEZ, IF_GTZ, IF_LEZ
        )

        val unconditionalBranchOpcodes: EnumSet<Opcode> = EnumSet.of(
            GOTO, GOTO_16, GOTO_32
        )

        val switchOpcodes: EnumSet<Opcode> = EnumSet.of(
            PACKED_SWITCH, SPARSE_SWITCH
        )

        val returnOpcodes: EnumSet<Opcode> = EnumSet.of(
            RETURN_VOID, RETURN, RETURN_WIDE, RETURN_OBJECT, RETURN_VOID_NO_BARRIER,
            THROW
        )

        val writeOpcodes: EnumSet<Opcode> = EnumSet.of(
            ARRAY_LENGTH,
            INSTANCE_OF,
            NEW_INSTANCE, NEW_ARRAY,
            MOVE, MOVE_FROM16, MOVE_16, MOVE_WIDE, MOVE_WIDE_FROM16, MOVE_WIDE_16, MOVE_OBJECT,
            MOVE_OBJECT_FROM16, MOVE_OBJECT_16, MOVE_RESULT, MOVE_RESULT_WIDE, MOVE_RESULT_OBJECT, MOVE_EXCEPTION,
            CONST, CONST_4, CONST_16, CONST_HIGH16, CONST_WIDE_16, CONST_WIDE_32,
            CONST_WIDE, CONST_WIDE_HIGH16, CONST_STRING, CONST_STRING_JUMBO,
            IGET, IGET_WIDE, IGET_OBJECT, IGET_BOOLEAN, IGET_BYTE, IGET_CHAR, IGET_SHORT,
            IGET_VOLATILE, IGET_WIDE_VOLATILE, IGET_OBJECT_VOLATILE,
            SGET, SGET_WIDE, SGET_OBJECT, SGET_BOOLEAN, SGET_BYTE, SGET_CHAR, SGET_SHORT,
            SGET_VOLATILE, SGET_WIDE_VOLATILE, SGET_OBJECT_VOLATILE,
            AGET, AGET_WIDE, AGET_OBJECT, AGET_BOOLEAN, AGET_BYTE, AGET_CHAR, AGET_SHORT,
            // Arithmetic and logical operations.
            ADD_DOUBLE_2ADDR, ADD_DOUBLE, ADD_FLOAT_2ADDR, ADD_FLOAT, ADD_INT_2ADDR,
            ADD_INT_LIT8, ADD_INT, ADD_LONG_2ADDR, ADD_LONG, ADD_INT_LIT16,
            AND_INT_2ADDR, AND_INT_LIT8, AND_INT_LIT16, AND_INT, AND_LONG_2ADDR, AND_LONG,
            DIV_DOUBLE_2ADDR, DIV_DOUBLE, DIV_FLOAT_2ADDR, DIV_FLOAT, DIV_INT_2ADDR,
            DIV_INT_LIT16, DIV_INT_LIT8, DIV_INT, DIV_LONG_2ADDR, DIV_LONG,
            DOUBLE_TO_FLOAT, DOUBLE_TO_INT, DOUBLE_TO_LONG,
            FLOAT_TO_DOUBLE, FLOAT_TO_INT, FLOAT_TO_LONG,
            INT_TO_BYTE, INT_TO_CHAR, INT_TO_DOUBLE, INT_TO_FLOAT, INT_TO_LONG, INT_TO_SHORT,
            LONG_TO_DOUBLE, LONG_TO_FLOAT, LONG_TO_INT,
            MUL_DOUBLE_2ADDR, MUL_DOUBLE, MUL_FLOAT_2ADDR, MUL_FLOAT, MUL_INT_2ADDR,
            MUL_INT_LIT16, MUL_INT_LIT8, MUL_INT, MUL_LONG_2ADDR, MUL_LONG,
            NEG_DOUBLE, NEG_FLOAT, NEG_INT, NEG_LONG,
            NOT_INT, NOT_LONG,
            OR_INT_2ADDR, OR_INT_LIT16, OR_INT_LIT8, OR_INT, OR_LONG_2ADDR, OR_LONG,
            REM_DOUBLE_2ADDR, REM_DOUBLE, REM_FLOAT_2ADDR, REM_FLOAT, REM_INT_2ADDR,
            REM_INT_LIT16, REM_INT_LIT8, REM_INT, REM_LONG_2ADDR, REM_LONG,
            RSUB_INT_LIT8, RSUB_INT,
            SHL_INT_2ADDR, SHL_INT_LIT8, SHL_INT, SHL_LONG_2ADDR, SHL_LONG,
            SHR_INT_2ADDR, SHR_INT_LIT8, SHR_INT, SHR_LONG_2ADDR, SHR_LONG,
            SUB_DOUBLE_2ADDR, SUB_DOUBLE, SUB_FLOAT_2ADDR, SUB_FLOAT, SUB_INT_2ADDR,
            SUB_INT, SUB_LONG_2ADDR, SUB_LONG,
            USHR_INT_2ADDR, USHR_INT_LIT8, USHR_INT, USHR_LONG_2ADDR, USHR_LONG,
            XOR_INT_2ADDR, XOR_INT_LIT16, XOR_INT_LIT8, XOR_INT, XOR_LONG_2ADDR, XOR_LONG,
        )

        /**
         * For debugging and development.
         */
        internal const val logFreeRegisterSearch = false
    }
}

/**
 * Starting from and including the instruction at index [index],
 * finds the next register that is written to and not read from. If a return instruction
 * is encountered, then the lowest unused register is returned.
 *
 * This method should work for all situations including inserting at a branch statement,
 * but this may not work if the index is at or just before a switch statement or if the branch
 * paths have no common free registers.
 *
 * If you need multiple free registers, then instead use [Method.getFreeRegisterProvider].
 *
 * @param index Index you need a use a free register at.
 * @param registersToExclude Registers to exclude, and consider as used. For most use cases,
 *                           all registers used in injected code should be specified.
 * @return The lowest register number (usually a 4-bit register) that is free at the given index.
 * @throws IllegalArgumentException If no free registers can be found at the given index.
 *                                  This includes unusual method indexes that read from every register
 *                                  before any registers are wrote to, or if a switch statement is
 *                                  encountered before any free registers are found, or if the index is
 *                                  at/before a branch statement and the method has an unusually high
 *                                  amount of branching where no common free registers exist in both branch paths.
 */
fun Method.findFreeRegister(
    index: Int,
    vararg registersToExclude: Int
) = findFreeRegisters(
    startIndex = index,
    numberOfFreeRegistersNeeded = 1,
    registersToExclude = registersToExclude.toList()
).first()

/**
 * Starting from and including the instruction at index [index],
 * finds the next register that is written to and not read from. If a return instruction
 * is encountered, then the lowest unused register is returned.
 *
 * This method should work for all situations including inserting at a branch statement,
 * but this may not work if the index is at or just before a switch statement or if the branch
 * paths have no common free registers.
 *
 * If you need multiple free registers, then instead use [Method.getFreeRegisterProvider].
 *
 * @param index Index you need a use a free register at.
 * @param registersToExclude Registers to exclude, and consider as used. For most use cases,
 *                           all registers used in injected code should be specified.
 * @return The lowest register number (usually a 4-bit register) that is free at the given index.
 * @throws IllegalArgumentException If no free registers can be found at the given index.
 *                                  This includes unusual method indexes that read from every register
 *                                  before any registers are wrote to, or if a switch statement is
 *                                  encountered before any free registers are found, or if the index is
 *                                  at/before a branch statement and the method has an unusually high
 *                                  amount of branching where no common free registers exist in both branch paths.
 */
fun Method.findFreeRegister(
    index: Int,
    registersToExclude: List<Int>
) = findFreeRegisters(
    startIndex = index,
    numberOfFreeRegistersNeeded = 1,
    registersToExclude = registersToExclude
).first()

private fun Method.findFreeRegisters(
    startIndex: Int,
    numberOfFreeRegistersNeeded: Int,
    registersToExclude: List<Int>
): List<Int> {
    if (logFreeRegisterSearch) println("Searching startIndex: $startIndex method: $this")

    val freeRegisters = findFreeRegistersInternal(
        startIndex = startIndex,
        numberOfFreeRegistersNeeded = numberOfFreeRegistersNeeded,
        currentDepth = 0,
        foundFreeRegistersAtIndex = mutableMapOf(),
        registersToExclude = registersToExclude,
        offsetArray = buildInstructionOffsetArray()
    )

    if (freeRegisters.isEmpty()) {
        // Should only happen if a switch statement is encountered before enough free registers are found.
        throw kotlin.IllegalArgumentException(
            "Could not find a free register from startIndex: " +
                    "$startIndex excluding: $registersToExclude"
        )
    }

    if (logFreeRegisterSearch) println("Final free registers found: $freeRegisters")

    // Use 4-bit registers first, but keep sorting stable among 4-bit vs not 4-bit.
    return freeRegisters.sortedWith { first, second ->
        val firstIsFourBit = first < 16
        val secondIsFourBit = second < 16
        if (firstIsFourBit == secondIsFourBit) {
            return@sortedWith 0
        }

        if (firstIsFourBit) -1 else 1
    }
}

/**
 * Returns all free registers found starting from [startIndex].Follows branches up to [maxDepth].
 *
 * @param startIndex Inclusive starting index.
 * @param numberOfFreeRegistersNeeded The minimum free registers to ensure will be returned.
 * @param currentDepth Current branching depth. Value of zero means no branching has been followed yet.
 * @param foundFreeRegistersAtIndex Map of instruction index to list of free registers previously found.
 * @param registersToExclude Registers to exclude from consideration.
 * @param offsetArray Map from instruction index to code offset.
 * @return List of all free registers found.
 */
private fun Method.findFreeRegistersInternal(
    startIndex: Int,
    numberOfFreeRegistersNeeded: Int,
    currentDepth: Int,
    foundFreeRegistersAtIndex: MutableMap<Int, Set<Int>?>,
    registersToExclude: List<Int>,
    offsetArray: IntArray
): List<Int> {
    check(implementation != null) {
        "Method has no implementation: $this"
    }
    check(startIndex > 0 && startIndex < instructions.count()) {
        "startIndex out of bounds: $startIndex methodInstructionCount: ${instructions.count()}"
    }
    check(numberOfFreeRegistersNeeded > 0) {
        "numberOfFreeRegistersNeeded must be greater than zero: $numberOfFreeRegistersNeeded"
    }

    fun Collection<Int>.numberOf4BitRegisters() = this.count { it < 16 }

    foundFreeRegistersAtIndex[startIndex]?.let {
        // Recursive call to a branch index that has already been explored.
        return (it - registersToExclude.toSet()).toList()
    }

    val usedRegisters = registersToExclude.toMutableSet()
    val freeRegisters = mutableSetOf<Int>()
    foundFreeRegistersAtIndex[startIndex] = freeRegisters

    for (i in startIndex until instructions.count()) {
        val instruction = getInstruction(i)
        val instructionRegisters = instruction.registersUsed

        // Check for write-only register.
        val writeRegister = instruction.writeRegister
        if (writeRegister != null && writeRegister !in usedRegisters) {
            // Check if this register is ONLY written to (not also read)
            // Count occurrences of writeRegister in instructionRegisters.
            val occurrences = instructionRegisters.count { it == writeRegister }
            // If it appears only once, it's write-only (the write).
            // If it appears more than once, it's also read.
            if (occurrences <= 1) {
                if (logFreeRegisterSearch) println(
                    "Found free register at $i: $writeRegister " +
                            "opcode: " + instruction.opcode + " reference: " + (instruction.getReference())
                )
                freeRegisters.add(writeRegister)
                // If the requested number of free registers is found and this is not a branch,
                // then no additional searching is needed.
                // But if this is a branch, then this all free registers should be found
                // because the intersection of free registers from different branches may be
                // less than the requested number of registers.
                if (currentDepth == 0 && freeRegisters.numberOf4BitRegisters() >= numberOfFreeRegistersNeeded) {
                    return freeRegisters.toList()
                }
            }
        }

        // Mark all registers used by this instruction as "used".
        usedRegisters.addAll(instructionRegisters)

        // If we hit a return, all unused registers on this path are free.
        if (instruction.isReturnInstruction) {
            val allRegisters = (0 until implementation!!.registerCount).toList()
            val unusedRegisters = allRegisters - usedRegisters
            freeRegisters.addAll(unusedRegisters)
            if (logFreeRegisterSearch) println("Encountered return index: $i and found: $freeRegisters")
            return freeRegisters.toList()
        }

        if (instruction.isSwitchInstruction) {
            // For now, do not handle the complexity of a switch statement and handle as a leaf node.
            if (logFreeRegisterSearch) println("Encountered switch index: $i opcode: " + instruction.opcode)
            return freeRegisters.toList()
        }

        if (instruction.isUnconditionalBranchInstruction) {
            if (logFreeRegisterSearch) println("encountered unconditional branch index: $i opcode: " + instruction.opcode)

            // Continue searching from the goto index.
            return (freeRegisters + findFreeRegistersInternal(
                startIndex = getBranchTargetInstructionIndex(instruction, i, offsetArray),
                numberOfFreeRegistersNeeded = numberOfFreeRegistersNeeded,
                currentDepth = currentDepth, // Same depth since it's a continuation of single path.
                foundFreeRegistersAtIndex = foundFreeRegistersAtIndex,
                registersToExclude = usedRegisters.toList(),
                offsetArray = offsetArray
            )).toList()
        }

        if (instruction.isConditionalBranchInstruction) {
            if (logFreeRegisterSearch) println("encountered conditional branch index: $i opcode: " + instruction.opcode)
            val usedRegistersList = usedRegisters.toList()

            val branchFreeRegisters = findFreeRegistersInternal(
                startIndex = getBranchTargetInstructionIndex(instruction, i, offsetArray),
                numberOfFreeRegistersNeeded = numberOfFreeRegistersNeeded,
                currentDepth = currentDepth + 1,
                foundFreeRegistersAtIndex = foundFreeRegistersAtIndex,
                registersToExclude = usedRegistersList,
                offsetArray = offsetArray
            )
            if (logFreeRegisterSearch) println("branch registers: $branchFreeRegisters")

            val fallThruFreeRegisters = findFreeRegistersInternal(
                startIndex = i + 1,
                numberOfFreeRegistersNeeded = numberOfFreeRegistersNeeded,
                currentDepth = currentDepth + 1,
                foundFreeRegistersAtIndex = foundFreeRegistersAtIndex,
                registersToExclude = usedRegistersList,
                offsetArray = offsetArray
            )
            if (logFreeRegisterSearch) println("fall thru registers: $fallThruFreeRegisters")

            return (freeRegisters + branchFreeRegisters.intersect(fallThruFreeRegisters.toSet())).toList()
        }
    }

    // A return or branch instruction will be encountered before all instructions can be iterated.
    // Some methods have switch payload instructions after the last actual instruction,
    // but these cannot be reached thru normal control flow.
    throw kotlin.IllegalArgumentException("Start index is outside normal control flow: $startIndex")
}

private fun Method.buildInstructionOffsetArray(): IntArray {
    val instructionCount = instructions.count()
    val offsetArray = IntArray(instructionCount) { -1 }
    var currentOffset = 0

    for (i in 0 until instructionCount) {
        val instruction = getInstruction(i)
        val format = instruction.opcode.format

        if (!format.isPayloadFormat) {
            offsetArray[i] = currentOffset

            // Get size in bytes from format.
            val sizeInBytes = format.size
            val sizeInCodeUnits = when {
                sizeInBytes > 0 -> sizeInBytes / 2  // Normal instruction
                format == Format.UnresolvedOdexInstruction -> 1  // Default size
                else -> 1  // Fallback for any other edge case
            }

            currentOffset += sizeInCodeUnits
        }
        // Skip payloads
    }

    return offsetArray
}

/**
 * Returns a instruction index for a given branch instruction.
 *
 * @param instruction The branch instruction
 * @param index Current instruction index
 * @param offsetArray Array mapping instruction index to code offset.
 */
private fun Method.getBranchTargetInstructionIndex(
    instruction: Instruction,
    index: Int,
    offsetArray: IntArray
): Int {
    check(index > 0 && index < offsetArray.size) {
        "Invalid index: $index"
    }
    val currentOffset = offsetArray[index]

    return when (instruction.opcode) {
        GOTO, GOTO_16, GOTO_32,
        IF_EQ, IF_NE, IF_LT, IF_GE, IF_GT, IF_LE,
        IF_EQZ, IF_NEZ, IF_LTZ, IF_GEZ, IF_GTZ, IF_LEZ -> {
            val offset = (instruction as OffsetInstruction).codeOffset
            val targetOffset = currentOffset + offset
            // Find the instruction index at this offset.
            findInstructionIndexByOffset(targetOffset, offsetArray)
        }
        // These need special handling - they jump to payloads
        // which then have their own target lists.
        // PACKED_SWITCH, SPARSE_SWITCH -> // TODO?
        else -> throw kotlin.IllegalStateException("Unsupported opcode: ${instruction.opcode}")
    }
}

/**
 * Finds the instruction index for a given code offset.
 *
 * @param targetOffset Target code offset in 16-bit units
 * @param offsetArray Array mapping instruction index to code offset (-1 for payloads)
 * @return Instruction index at the target offset, or null if not found
 */
private fun Method.findInstructionIndexByOffset(
    targetOffset: Int,
    offsetArray: IntArray
): Int {
    // Simple linear search using indexOfFirst
    val index = offsetArray.indexOfFirst { it == targetOffset }
    if (index >= 0) {
        return index
    }

    // Should never happen.
    // Code has been tested on hundreds of random methods on all instruction indices,
    // but maybe some weird code exists that this has overlooked.
    throw kotlin.IllegalArgumentException(
        "Could not find exact instruction offset for method: " +
                "$this at offset: $targetOffset. Please file a bug report in the Morphe patches repo"
    )
}

/**
 * @return The registers used by this instruction.
 */
val Instruction.registersUsed: List<Int>
    get() = when (this) {
        is FiveRegisterInstruction -> {
            when (registerCount) {
                0 -> listOf()
                1 -> listOf(registerC)
                2 -> listOf(registerC, registerD)
                3 -> listOf(registerC, registerD, registerE)
                4 -> listOf(registerC, registerD, registerE, registerF)
                else -> listOf(registerC, registerD, registerE, registerF, registerG)
            }
        }

        is ThreeRegisterInstruction -> listOf(registerA, registerB, registerC)
        is TwoRegisterInstruction -> listOf(registerA, registerB)
        is OneRegisterInstruction -> listOf(registerA)
        is RegisterRangeInstruction -> (startRegister until (startRegister + registerCount)).toList()
        else -> emptyList()
    }

/**
 * @return The register that is written to by this instruction,
 *         or NULL if this is not a write opcode.
 */
val Instruction.writeRegister: Int?
    get() {
        if (this.opcode !in writeOpcodes) {
            return null
        }
        if (this !is OneRegisterInstruction) {
            throw kotlin.IllegalStateException("Not a write instruction: $this")
        }
        return registerA
    }

/**
 * This differs from [isUnconditionalBranchInstruction] in that it does not include unconditional goto.
 *
 * @return If this instruction is a conditional branch (multiple branch paths).
 */
internal val Instruction.isConditionalBranchInstruction: Boolean
    get() = this.opcode in conditionalBranchOpcodes

/**
 * @return If this instruction is a GOTO opcode.
 */
internal val Instruction.isUnconditionalBranchInstruction: Boolean
    get() = this.opcode in unconditionalBranchOpcodes

/**
* @return If this instruction is a switch opcode.
*/
internal val Instruction.isSwitchInstruction: Boolean
    get() = this.opcode in switchOpcodes

/**
 * @return If this instruction returns or throws.
 */
internal val Instruction.isReturnInstruction: Boolean
    get() = this.opcode in returnOpcodes
