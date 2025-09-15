package app.revanced.util

import app.revanced.patcher.FingerprintBuilder
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.util.proxy.mutableTypes.MutableClass
import app.revanced.patcher.util.proxy.mutableTypes.MutableField
import app.revanced.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patches.shared.misc.mapping.get
import app.revanced.patches.shared.misc.mapping.resourceMappingPatch
import app.revanced.patches.shared.misc.mapping.resourceMappings
import app.revanced.util.InstructionUtils.Companion.branchOpcodes
import app.revanced.util.InstructionUtils.Companion.returnOpcodes
import app.revanced.util.InstructionUtils.Companion.writeOpcodes
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcode.*
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.util.MethodUtil
import java.util.EnumSet

/**
 * Starting from and including the instruction at index [startIndex],
 * finds the next register that is wrote to and not read from. If a return instruction
 * is encountered, then the lowest unused register is returned.
 *
 * This method can return a non 4-bit register, and the calling code may need to temporarily
 * swap register contents if a 4-bit register is required.
 *
 * @param startIndex Inclusive starting index.
 * @param registersToExclude Registers to exclude, and consider as used. For most use cases,
 *                           all registers used in injected code should be specified.
 * @throws IllegalArgumentException If a branch or conditional statement is encountered
 *                                  before a suitable register is found.
 */
fun Method.findFreeRegister(startIndex: Int, vararg registersToExclude: Int): Int {
    if (implementation == null) {
        throw IllegalArgumentException("Method has no implementation: $this")
    }
    if (startIndex < 0 || startIndex >= instructions.count()) {
        throw IllegalArgumentException("startIndex out of bounds: $startIndex")
    }

    // Highest 4-bit register available, exclusive. Ideally return a free register less than this.
    val maxRegister4Bits = 16
    var bestFreeRegisterFound: Int? = null
    val usedRegisters = registersToExclude.toMutableSet()

    for (i in startIndex until instructions.count()) {
        val instruction = getInstruction(i)
        val instructionRegisters = instruction.registersUsed

        val writeRegister = instruction.writeRegister
        if (writeRegister != null) {
            if (writeRegister !in usedRegisters) {
                // Verify the register is only used for write and not also as a parameter.
                // If the instruction uses the write register once then it's not also a read register.
                if (instructionRegisters.count { register -> register == writeRegister } == 1) {
                    if (writeRegister < maxRegister4Bits) {
                        // Found an ideal register.
                        return writeRegister
                    }

                    // Continue searching for a 4-bit register if available.
                    if (bestFreeRegisterFound == null || writeRegister < bestFreeRegisterFound) {
                        bestFreeRegisterFound = writeRegister
                    }
                }
            }
        }

        usedRegisters.addAll(instructionRegisters)

        if (instruction.isBranchInstruction) {
            if (bestFreeRegisterFound != null) {
                return bestFreeRegisterFound
            }
            // This method is simple and does not follow branching.
            throw IllegalArgumentException("Encountered a branch statement before a free register could be found")
        }

        if (instruction.isReturnInstruction) {
            // Use lowest register that hasn't been encountered.
            val freeRegister = (0 until implementation!!.registerCount).find {
                it !in usedRegisters
            }
            if (freeRegister != null) {
                return freeRegister
            }
            if (bestFreeRegisterFound != null) {
                return bestFreeRegisterFound
            }

            // Somehow every method register was read from before any register was wrote to.
            // In practice this never occurs.
            throw IllegalArgumentException("Could not find a free register from startIndex: " +
                    "$startIndex excluding: $registersToExclude")
        }
    }

    // Some methods can have array payloads at the end of the method after a return statement.
    // But in normal usage this cannot be reached since a branch or return statement
    // will be encountered before the end of the method.
    throw IllegalArgumentException("Start index is outside the range of normal control flow: $startIndex")
}

/**
 * @return The registers used by this instruction.
 */
internal val Instruction.registersUsed: List<Int>
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
internal val Instruction.writeRegister: Int?
    get() {
        if (this.opcode !in writeOpcodes) {
            return null
        }
        if (this !is OneRegisterInstruction) {
            throw IllegalStateException("Not a write instruction: $this")
        }
        return registerA
    }

/**
 * @return If this instruction is an unconditional or conditional branch opcode.
 */
internal val Instruction.isBranchInstruction: Boolean
    get() = this.opcode in branchOpcodes

/**
 * @return If this instruction returns or throws.
 */
internal val Instruction.isReturnInstruction: Boolean
    get() = this.opcode in returnOpcodes

/**
 * Find the instruction index used for a toString() StringBuilder write of a given String name.
 *
 * @param fieldName The name of the field to find.  Partial matches are allowed.
 */
private fun Method.findInstructionIndexFromToString(fieldName: String) : Int {
    val stringIndex = indexOfFirstInstruction {
        val reference = getReference<StringReference>()
        reference?.string?.contains(fieldName) == true
    }
    if (stringIndex < 0) {
        throw IllegalArgumentException("Could not find usage of string: '$fieldName'")
    }
    val stringRegister = getInstruction<OneRegisterInstruction>(stringIndex).registerA

    // Find use of the string with a StringBuilder.
    val stringUsageIndex = indexOfFirstInstruction(stringIndex) {
        val reference = getReference<MethodReference>()
        reference?.definingClass == "Ljava/lang/StringBuilder;" &&
                (this as? FiveRegisterInstruction)?.registerD == stringRegister
    }
    if (stringUsageIndex < 0) {
        throw IllegalArgumentException("Could not find StringBuilder usage in: $this")
    }

    // Find the next usage of StringBuilder, which should be the desired field.
    val fieldUsageIndex = indexOfFirstInstruction(stringUsageIndex + 1) {
        val reference = getReference<MethodReference>()
        reference?.definingClass == "Ljava/lang/StringBuilder;" && reference.name == "append"
    }
    if (fieldUsageIndex < 0) {
        // Should never happen.
        throw IllegalArgumentException("Could not find StringBuilder append usage in: $this")
    }
    val fieldUsageRegister = getInstruction<FiveRegisterInstruction>(fieldUsageIndex).registerD

    // Look backwards up the method to find the instruction that sets the register.
    var fieldSetIndex = indexOfFirstInstructionReversedOrThrow(fieldUsageIndex - 1) {
        fieldUsageRegister == writeRegister
    }

    // If the field is a method call, then adjust from MOVE_RESULT to the method call.
    val fieldSetOpcode = getInstruction(fieldSetIndex).opcode
    if (fieldSetOpcode == MOVE_RESULT ||
        fieldSetOpcode == MOVE_RESULT_WIDE ||
        fieldSetOpcode == MOVE_RESULT_OBJECT) {
        fieldSetIndex--
    }

    return fieldSetIndex
}

/**
 * Find the method used for a toString() StringBuilder write of a given String name.
 *
 * @param fieldName The name of the field to find.  Partial matches are allowed.
 */
context(BytecodePatchContext)
internal fun Method.findMethodFromToString(fieldName: String) : MutableMethod {
    val methodUsageIndex = findInstructionIndexFromToString(fieldName)
    return navigate(this).to(methodUsageIndex).stop()
}

/**
 * Find the field used for a toString() StringBuilder write of a given String name.
 *
 * @param fieldName The name of the field to find.  Partial matches are allowed.
 */
internal fun Method.findFieldFromToString(fieldName: String) : FieldReference {
    val methodUsageIndex = findInstructionIndexFromToString(fieldName)
    return getInstruction<ReferenceInstruction>(methodUsageIndex).getReference<FieldReference>()!!
}

/**
 * Adds public [AccessFlags] and removes private and protected flags (if present).
 */
internal fun Int.toPublicAccessFlags(): Int {
    return this.or(AccessFlags.PUBLIC.value)
        .and(AccessFlags.PROTECTED.value.inv())
        .and(AccessFlags.PRIVATE.value.inv())
}

/**
 * Find the [MutableMethod] from a given [Method] in a [MutableClass].
 *
 * @param method The [Method] to find.
 * @return The [MutableMethod].
 */
fun MutableClass.findMutableMethodOf(method: Method) = this.methods.first {
    MethodUtil.methodSignaturesMatch(it, method)
}

/**
 * Apply a transform to all methods of the class.
 *
 * @param transform The transformation function. Accepts a [MutableMethod] and returns a transformed [MutableMethod].
 */
fun MutableClass.transformMethods(transform: MutableMethod.() -> MutableMethod) {
    val transformedMethods = methods.map { it.transform() }
    methods.clear()
    methods.addAll(transformedMethods)
}

/**
 * Inject a call to a method that hides a view.
 *
 * @param insertIndex The index to insert the call at.
 * @param viewRegister The register of the view to hide.
 * @param classDescriptor The descriptor of the class that contains the method.
 * @param targetMethod The name of the method to call.
 */
fun MutableMethod.injectHideViewCall(
    insertIndex: Int,
    viewRegister: Int,
    classDescriptor: String,
    targetMethod: String,
) = addInstruction(
    insertIndex,
    "invoke-static { v$viewRegister }, $classDescriptor->$targetMethod(Landroid/view/View;)V",
)


/**
 * Inserts instructions at a given index, using the existing control flow label at that index.
 * Inserted instructions can have it's own control flow labels as well.
 *
 * Effectively this changes the code from:
 * :label
 * (original code)
 *
 * Into:
 * :label
 * (patch code)
 * (original code)
 */
// TODO: delete this on next major version bump.
fun MutableMethod.addInstructionsAtControlFlowLabel(
    insertIndex: Int,
    instructions: String
) = addInstructionsAtControlFlowLabel(insertIndex, instructions, *arrayOf<ExternalLabel>())

/**
 * Inserts instructions at a given index, using the existing control flow label at that index.
 * Inserted instructions can have it's own control flow labels as well.
 *
 * Effectively this changes the code from:
 * :label
 * (original code)
 *
 * Into:
 * :label
 * (patch code)
 * (original code)
 */
fun MutableMethod.addInstructionsAtControlFlowLabel(
    insertIndex: Int,
    instructions: String,
    vararg externalLabels: ExternalLabel
) {
    // Duplicate original instruction and add to +1 index.
    addInstruction(insertIndex + 1, getInstruction(insertIndex))

    // Add patch code at same index as duplicated instruction,
    // so it uses the original instruction control flow label.
    addInstructionsWithLabels(insertIndex + 1, instructions, *externalLabels)

    // Remove original non duplicated instruction.
    removeInstruction(insertIndex)

    // Original instruction is now after the inserted patch instructions,
    // and the original control flow label is on the first instruction of the patch code.
}

/**
 * Get the index of the first instruction with the id of the given resource id name.
 *
 * Requires [resourceMappingPatch] as a dependency.
 *
 * @param resourceName the name of the resource to find the id for.
 * @return the index of the first instruction with the id of the given resource name, or -1 if not found.
 * @throws PatchException if the resource cannot be found.
 * @see [indexOfFirstResourceIdOrThrow], [indexOfFirstLiteralInstructionReversed]
 */
fun Method.indexOfFirstResourceId(resourceName: String): Int {
    val resourceId = resourceMappings["id", resourceName]
    return indexOfFirstLiteralInstruction(resourceId)
}

/**
 * Get the index of the first instruction with the id of the given resource name or throw a [PatchException].
 *
 * Requires [resourceMappingPatch] as a dependency.
 *
 * @throws [PatchException] if the resource is not found, or the method does not contain the resource id literal value.
 * @see [indexOfFirstResourceId], [indexOfFirstLiteralInstructionReversedOrThrow]
 */
fun Method.indexOfFirstResourceIdOrThrow(resourceName: String): Int {
    val index = indexOfFirstResourceId(resourceName)
    if (index < 0) {
        throw PatchException("Found resource id for: '$resourceName' but method does not contain the id: $this")
    }

    return index
}

/**
 * Find the index of the first literal instruction with the given long value.
 *
 * @return the first literal instruction with the value, or -1 if not found.
 * @see indexOfFirstLiteralInstructionOrThrow
 */
fun Method.indexOfFirstLiteralInstruction(literal: Long) = implementation?.let {
    it.instructions.indexOfFirst { instruction ->
        (instruction as? WideLiteralInstruction)?.wideLiteral == literal
    }
} ?: -1

/**
 * Find the index of the first literal instruction with the given long value,
 * or throw an exception if not found.
 *
 * @return the first literal instruction with the value, or throws [PatchException] if not found.
 */
fun Method.indexOfFirstLiteralInstructionOrThrow(literal: Long): Int {
    val index = indexOfFirstLiteralInstruction(literal)
    if (index < 0) throw PatchException("Could not find long literal: $literal")
    return index
}

/**
 * Find the index of the first literal instruction with the given float value.
 *
 * @return the first literal instruction with the value, or -1 if not found.
 * @see indexOfFirstLiteralInstructionOrThrow
 */
fun Method.indexOfFirstLiteralInstruction(literal: Float) =
    indexOfFirstLiteralInstruction(literal.toRawBits().toLong())

/**
 * Find the index of the first literal instruction with the given float value,
 * or throw an exception if not found.
 *
 * @return the first literal instruction with the value, or throws [PatchException] if not found.
 */
fun Method.indexOfFirstLiteralInstructionOrThrow(literal: Float): Int {
    val index = indexOfFirstLiteralInstruction(literal)
    if (index < 0) throw PatchException("Could not find float literal: $literal")
    return index
}

/**
 * Find the index of the first literal instruction with the given double value.
 *
 * @return the first literal instruction with the value, or -1 if not found.
 * @see indexOfFirstLiteralInstructionOrThrow
 */
fun Method.indexOfFirstLiteralInstruction(literal: Double) =
    indexOfFirstLiteralInstruction(literal.toRawBits())

/**
 * Find the index of the first literal instruction with the given double value,
 * or throw an exception if not found.
 *
 * @return the first literal instruction with the value, or throws [PatchException] if not found.
 */
fun Method.indexOfFirstLiteralInstructionOrThrow(literal: Double): Int {
    val index = indexOfFirstLiteralInstruction(literal)
    if (index < 0) throw PatchException("Could not find double literal: $literal")
    return index
}

/**
 * Find the index of the last literal instruction with the given value.
 *
 * @return the last literal instruction with the value, or -1 if not found.
 * @see indexOfFirstLiteralInstructionOrThrow
 */
fun Method.indexOfFirstLiteralInstructionReversed(literal: Long) = implementation?.let {
    it.instructions.indexOfLast { instruction ->
        (instruction as? WideLiteralInstruction)?.wideLiteral == literal
    }
} ?: -1

/**
 * Find the index of the last wide literal instruction with the given long value,
 * or throw an exception if not found.
 *
 * @return the last literal instruction with the value, or throws [PatchException] if not found.
 */
fun Method.indexOfFirstLiteralInstructionReversedOrThrow(literal: Long): Int {
    val index = indexOfFirstLiteralInstructionReversed(literal)
    if (index < 0) throw PatchException("Could not find long literal: $literal")
    return index
}

/**
 * Find the index of the last literal instruction with the given float value.
 *
 * @return the last literal instruction with the value, or -1 if not found.
 * @see indexOfFirstLiteralInstructionOrThrow
 */
fun Method.indexOfFirstLiteralInstructionReversed(literal: Float) =
    indexOfFirstLiteralInstructionReversed(literal.toRawBits().toLong())

/**
 * Find the index of the last wide literal instruction with the given float value,
 * or throw an exception if not found.
 *
 * @return the last literal instruction with the value, or throws [PatchException] if not found.
 */
fun Method.indexOfFirstLiteralInstructionReversedOrThrow(literal: Float): Int {
    val index = indexOfFirstLiteralInstructionReversed(literal)
    if (index < 0) throw PatchException("Could not find float literal: $literal")
    return index
}

/**
 * Find the index of the last literal instruction with the given double value.
 *
 * @return the last literal instruction with the value, or -1 if not found.
 * @see indexOfFirstLiteralInstructionOrThrow
 */
fun Method.indexOfFirstLiteralInstructionReversed(literal: Double) =
    indexOfFirstLiteralInstructionReversed(literal.toRawBits())

/**
 * Find the index of the last wide literal instruction with the given double value,
 * or throw an exception if not found.
 *
 * @return the last literal instruction with the value, or throws [PatchException] if not found.
 */
fun Method.indexOfFirstLiteralInstructionReversedOrThrow(literal: Double): Int {
    val index = indexOfFirstLiteralInstructionReversed(literal)
    if (index < 0) throw PatchException("Could not find double literal: $literal")
    return index
}

/**
 * Check if the method contains a literal with the given long value.
 *
 * @return if the method contains a literal with the given value.
 */
fun Method.containsLiteralInstruction(literal: Long) = indexOfFirstLiteralInstruction(literal) >= 0

/**
 * Check if the method contains a literal with the given float value.
 *
 * @return if the method contains a literal with the given value.
 */
fun Method.containsLiteralInstruction(literal: Float) = indexOfFirstLiteralInstruction(literal) >= 0

/**
 * Check if the method contains a literal with the given double value.
 *
 * @return if the method contains a literal with the given value.
 */
fun Method.containsLiteralInstruction(literal: Double) = indexOfFirstLiteralInstruction(literal) >= 0

/**
 * Traverse the class hierarchy starting from the given root class.
 *
 * @param targetClass the class to start traversing the class hierarchy from.
 * @param callback function that is called for every class in the hierarchy.
 */
fun BytecodePatchContext.traverseClassHierarchy(targetClass: MutableClass, callback: MutableClass.() -> Unit) {
    callback(targetClass)

    targetClass.superclass ?: return

    classBy { targetClass.superclass == it.type }?.mutableClass?.let {
        traverseClassHierarchy(it, callback)
    }
}

/**
 * Get the [Reference] of an [Instruction] as [T].
 *
 * @param T The type of [Reference] to cast to.
 * @return The [Reference] as [T] or null
 * if the [Instruction] is not a [ReferenceInstruction] or the [Reference] is not of type [T].
 * @see ReferenceInstruction
 */
inline fun <reified T : Reference> Instruction.getReference() = (this as? ReferenceInstruction)?.reference as? T

/**
 * @return The index of the first opcode specified, or -1 if not found.
 * @see indexOfFirstInstructionOrThrow
 */
fun Method.indexOfFirstInstruction(targetOpcode: Opcode): Int = indexOfFirstInstruction(0, targetOpcode)

/**
 * @param startIndex Optional starting index to start searching from.
 * @return The index of the first opcode specified, or -1 if not found.
 * @see indexOfFirstInstructionOrThrow
 */
fun Method.indexOfFirstInstruction(startIndex: Int = 0, targetOpcode: Opcode): Int =
    indexOfFirstInstruction(startIndex) {
        opcode == targetOpcode
    }

/**
 * Get the index of the first [Instruction] that matches the predicate, starting from [startIndex].
 *
 * @param startIndex Optional starting index to start searching from.
 * @return -1 if the instruction is not found.
 * @see indexOfFirstInstructionOrThrow
 */
fun Method.indexOfFirstInstruction(startIndex: Int = 0, filter: Instruction.() -> Boolean): Int {
    var instructions = this.implementation?.instructions ?: return -1
    if (startIndex != 0) {
        instructions = instructions.drop(startIndex)
    }
    val index = instructions.indexOfFirst(filter)

    return if (index >= 0) {
        startIndex + index
    } else {
        -1
    }
}

/**
 * @return The index of the first opcode specified
 * @throws PatchException
 * @see indexOfFirstInstruction
 */
fun Method.indexOfFirstInstructionOrThrow(targetOpcode: Opcode): Int = indexOfFirstInstructionOrThrow(0, targetOpcode)

/**
 * @return The index of the first opcode specified, starting from the index specified.
 * @throws PatchException
 * @see indexOfFirstInstruction
 */
fun Method.indexOfFirstInstructionOrThrow(startIndex: Int = 0, targetOpcode: Opcode): Int =
    indexOfFirstInstructionOrThrow(startIndex) {
        opcode == targetOpcode
    }

/**
 * Get the index of the first [Instruction] that matches the predicate, starting from [startIndex].
 *
 * @return The index of the instruction.
 * @throws PatchException
 * @see indexOfFirstInstruction
 */
fun Method.indexOfFirstInstructionOrThrow(startIndex: Int = 0, filter: Instruction.() -> Boolean): Int {
    val index = indexOfFirstInstruction(startIndex, filter)
    if (index < 0) {
        throw PatchException("Could not find instruction index")
    }

    return index
}

/**
 * Get the index of matching instruction,
 * starting from and [startIndex] and searching down.
 *
 * @param startIndex Optional starting index to search down from. Searching includes the start index.
 * @return -1 if the instruction is not found.
 * @see indexOfFirstInstructionReversedOrThrow
 */
fun Method.indexOfFirstInstructionReversed(startIndex: Int? = null, targetOpcode: Opcode): Int =
    indexOfFirstInstructionReversed(startIndex) {
        opcode == targetOpcode
    }

/**
 * Get the index of matching instruction,
 * starting from and [startIndex] and searching down.
 *
 * @param startIndex Optional starting index to search down from. Searching includes the start index.
 * @return -1 if the instruction is not found.
 * @see indexOfFirstInstructionReversedOrThrow
 */
fun Method.indexOfFirstInstructionReversed(startIndex: Int? = null, filter: Instruction.() -> Boolean): Int {
    var instructions = this.implementation?.instructions ?: return -1
    if (startIndex != null) {
        instructions = instructions.take(startIndex + 1)
    }

    return instructions.indexOfLast(filter)
}

/**
 * Get the index of matching instruction,
 * starting from the end of the method and searching down.
 *
 * @return -1 if the instruction is not found.
 */
fun Method.indexOfFirstInstructionReversed(targetOpcode: Opcode): Int = indexOfFirstInstructionReversed {
    opcode == targetOpcode
}

/**
 * Get the index of matching instruction,
 * starting from [startIndex] and searching down.
 *
 * @param startIndex Optional starting index to search down from. Searching includes the start index.
 * @return The index of the instruction.
 * @see indexOfFirstInstructionReversed
 */
fun Method.indexOfFirstInstructionReversedOrThrow(startIndex: Int? = null, targetOpcode: Opcode): Int =
    indexOfFirstInstructionReversedOrThrow(startIndex) {
        opcode == targetOpcode
    }

/**
 * Get the index of matching instruction,
 * starting from the end of the method and searching down.
 *
 * @return -1 if the instruction is not found.
 */
fun Method.indexOfFirstInstructionReversedOrThrow(targetOpcode: Opcode): Int = indexOfFirstInstructionReversedOrThrow {
    opcode == targetOpcode
}

/**
 * Get the index of matching instruction,
 * starting from [startIndex] and searching down.
 *
 * @param startIndex Optional starting index to search down from. Searching includes the start index.
 * @return The index of the instruction.
 * @see indexOfFirstInstructionReversed
 */
fun Method.indexOfFirstInstructionReversedOrThrow(startIndex: Int? = null, filter: Instruction.() -> Boolean): Int {
    val index = indexOfFirstInstructionReversed(startIndex, filter)

    if (index < 0) {
        throw PatchException("Could not find instruction index")
    }

    return index
}

/**
 * @return An immutable list of indices of the instructions in reverse order.
 *  _Returns an empty list if no indices are found_
 *  @see findInstructionIndicesReversedOrThrow
 */
fun Method.findInstructionIndicesReversed(filter: Instruction.() -> Boolean): List<Int> = instructions
    .withIndex()
    .filter { (_, instruction) -> filter(instruction) }
    .map { (index, _) -> index }
    .asReversed()

/**
 * @return An immutable list of indices of the instructions in reverse order.
 * @throws PatchException if no matching indices are found.
 */
fun Method.findInstructionIndicesReversedOrThrow(filter: Instruction.() -> Boolean): List<Int> {
    val indexes = findInstructionIndicesReversed(filter)
    if (indexes.isEmpty()) throw PatchException("No matching instructions found in: $this")

    return indexes
}

/**
 * @return An immutable list of indices of the opcode in reverse order.
 *  _Returns an empty list if no indices are found_
 * @see findInstructionIndicesReversedOrThrow
 */
fun Method.findInstructionIndicesReversed(opcode: Opcode): List<Int> =
    findInstructionIndicesReversed { this.opcode == opcode }

/**
 * @return An immutable list of indices of the opcode in reverse order.
 * @throws PatchException if no matching indices are found.
 */
fun Method.findInstructionIndicesReversedOrThrow(opcode: Opcode): List<Int> {
    val instructions = findInstructionIndicesReversed(opcode)
    if (instructions.isEmpty()) throw PatchException("Could not find opcode: $opcode in: $this")

    return instructions
}

/**
 * Overrides the first move result with an extension call.
 * Suitable for calls to extension code to override boolean and integer values.
 */
internal fun MutableMethod.insertLiteralOverride(literal: Long, extensionMethodDescriptor: String) {
    // TODO: make this work with objects and wide values.
    val literalIndex = indexOfFirstLiteralInstructionOrThrow(literal)
    val index = indexOfFirstInstructionOrThrow(literalIndex, MOVE_RESULT)
    val register = getInstruction<OneRegisterInstruction>(index).registerA

    val operation = if (register < 16) {
        "invoke-static { v$register }"
    } else {
        "invoke-static/range { v$register .. v$register }"
    }

    addInstructions(
        index + 1,
        """
            $operation, $extensionMethodDescriptor
            move-result v$register
        """
    )
}

/**
 * Overrides a literal value result with a constant value.
 */
internal fun MutableMethod.insertLiteralOverride(literal: Long, override: Boolean) {
    val literalIndex = indexOfFirstLiteralInstructionOrThrow(literal)
    val index = indexOfFirstInstructionOrThrow(literalIndex, MOVE_RESULT)
    val register = getInstruction<OneRegisterInstruction>(index).registerA
    val overrideValue = if (override) "0x1" else "0x0"

    addInstruction(
        index + 1,
        "const v$register, $overrideValue"
    )
}

/**
 * Called for _all_ methods with the given literal value.
 * Method indices are iterated from last to first.
 */
fun BytecodePatchContext.forEachLiteralValueInstruction(
    literal: Long,
    block: MutableMethod.(matchingIndex: Int) -> Unit,
) {
    val matchingIndexes = ArrayList<Int>()

    classes.forEach { classDef ->
        classDef.methods.forEach { method ->
            method.implementation?.instructions?.let { instructions ->
                matchingIndexes.clear()

                instructions.forEachIndexed { index, instruction ->
                    if ((instruction as? WideLiteralInstruction)?.wideLiteral == literal) {
                        matchingIndexes.add(index)
                    }
                }

                if (matchingIndexes.isNotEmpty()) {
                    val mutableMethod = proxy(classDef).mutableClass.findMutableMethodOf(method)

                    // FIXME: Until patcher V22 is merged, this workaround is needed
                    //        because if multiple patches modify the same class
                    //        then after modifying the method indexes of immutable classes
                    //        are no longer correct.
                    matchingIndexes.clear()
                    mutableMethod.instructions.forEachIndexed { index, instruction ->
                        if ((instruction as? WideLiteralInstruction)?.wideLiteral == literal) {
                            matchingIndexes.add(index)
                        }
                    }
                    if (matchingIndexes.isEmpty()) return@forEach
                    // FIXME Remove code above after V22 merge.

                    matchingIndexes.asReversed().forEach { index ->
                        block.invoke(mutableMethod, index)
                    }
                }
            }
        }
    }

}

private const val RETURN_TYPE_MISMATCH = "Mismatch between override type and Method return type"

/**
 * Overrides the first instruction of a method with a constant `Boolean` return value.
 * None of the method code will ever execute.
 *
 * For methods that return an object or any array type, calling this method with `false`
 * will force the method to return a `null` value.
 *
 * @see returnLate
 */
fun MutableMethod.returnEarly(value: Boolean = false) {
    val returnType = returnType.first()
    check(returnType == 'Z' || (!value && (returnType == 'V' || returnType == 'L' || returnType != '['))) {
        RETURN_TYPE_MISMATCH
    }
    overrideReturnValue(value.toHexString(), false)
}

/**
 * Overrides the first instruction of a method with a constant `Byte` return value.
 * None of the method code will ever execute.
 *
 * @see returnLate
 */
fun MutableMethod.returnEarly(value: Byte) {
    check(returnType.first() == 'B') { RETURN_TYPE_MISMATCH }
    overrideReturnValue(value.toString(), false)
}

/**
 * Overrides the first instruction of a method with a constant `Short` return value.
 * None of the method code will ever execute.
 *
 * @see returnLate
 */
fun MutableMethod.returnEarly(value: Short) {
    check(returnType.first() == 'S') { RETURN_TYPE_MISMATCH }
    overrideReturnValue(value.toString(), false)
}

/**
 * Overrides the first instruction of a method with a constant `Char` return value.
 * None of the method code will ever execute.
 *
 * @see returnLate
 */
fun MutableMethod.returnEarly(value: Char) {
    check(returnType.first() == 'C') { RETURN_TYPE_MISMATCH }
    overrideReturnValue(value.code.toString(), false)
}

/**
 * Overrides the first instruction of a method with a constant `Int` return value.
 * None of the method code will ever execute.
 *
 * @see returnLate
 */
fun MutableMethod.returnEarly(value: Int) {
    check(returnType.first() == 'I') { RETURN_TYPE_MISMATCH }
    overrideReturnValue(value.toString(), false)
}

/**
 * Overrides the first instruction of a method with a constant `Long` return value.
 * None of the method code will ever execute.
 *
 * @see returnLate
 */
fun MutableMethod.returnEarly(value: Long) {
    check(returnType.first() == 'J') { RETURN_TYPE_MISMATCH }
    overrideReturnValue(value.toString(), false)
}

/**
 * Overrides the first instruction of a method with a constant `Float` return value.
 * None of the method code will ever execute.
 *
 * @see returnLate
 */
fun MutableMethod.returnEarly(value: Float) {
    check(returnType.first() == 'F') { RETURN_TYPE_MISMATCH }
    overrideReturnValue(value.toString(), false)
}

/**
 * Overrides the first instruction of a method with a constant `Double` return value.
 * None of the method code will ever execute.
 *
 * @see returnLate
 */
fun MutableMethod.returnEarly(value: Double) {
    check(returnType.first() == 'J') { RETURN_TYPE_MISMATCH }
    overrideReturnValue(value.toString(), false)
}

/**
 * Overrides the first instruction of a method with a constant String return value.
 * None of the method code will ever execute.
 *
 * Target method must have return type
 * Ljava/lang/String; or Ljava/lang/CharSequence;
 *
 * @see returnLate
 */
fun MutableMethod.returnEarly(value: String) {
    check(returnType == "Ljava/lang/String;" || returnType == "Ljava/lang/CharSequence;") {
        RETURN_TYPE_MISMATCH
    }
    overrideReturnValue(value, false)
}

/**
 * Overrides all return statements with a constant `Boolean` value.
 * All method code is executed the same as unpatched.
 *
 * For methods that return an object or any array type, calling this method with `false`
 * will force the method to return a `null` value.
 *
 * @see returnEarly
 */
fun MutableMethod.returnLate(value: Boolean) {
    val returnType = returnType.first()
    if (returnType == 'V') {
        error("Cannot return late for Method of void type")
    }
    check(returnType == 'Z' || (!value && (returnType == 'L' || returnType == '['))) {
        RETURN_TYPE_MISMATCH
    }

    overrideReturnValue(value.toHexString(), true)
}

/**
 * Overrides all return statements with a constant `Byte` value.
 * All method code is executed the same as unpatched.
 *
 * @see returnEarly
 */
fun MutableMethod.returnLate(value: Byte) {
    check(returnType.first() == 'B') { RETURN_TYPE_MISMATCH }
    overrideReturnValue(value.toString(), true)
}

/**
 * Overrides all return statements with a constant `Short` value.
 * All method code is executed the same as unpatched.
 *
 * @see returnEarly
 */
fun MutableMethod.returnLate(value: Short) {
    check(returnType.first() == 'S') { RETURN_TYPE_MISMATCH }
    overrideReturnValue(value.toString(), true)
}

/**
 * Overrides all return statements with a constant `Char` value.
 * All method code is executed the same as unpatched.
 *
 * @see returnEarly
 */
fun MutableMethod.returnLate(value: Char) {
    check(returnType.first() == 'C') { RETURN_TYPE_MISMATCH }
    overrideReturnValue(value.code.toString(), true)
}

/**
 * Overrides all return statements with a constant `Int` value.
 * All method code is executed the same as unpatched.
 *
 * @see returnEarly
 */
fun MutableMethod.returnLate(value: Int) {
    check(returnType.first() == 'I') { RETURN_TYPE_MISMATCH }
    overrideReturnValue(value.toString(), true)
}

/**
 * Overrides all return statements with a constant `Long` value.
 * All method code is executed the same as unpatched.
 *
 * @see returnEarly
 */
fun MutableMethod.returnLate(value: Long) {
    check(returnType.first() == 'J') { RETURN_TYPE_MISMATCH }
    overrideReturnValue(value.toString(), true)
}

/**
 * Overrides all return statements with a constant `Float` value.
 * All method code is executed the same as unpatched.
 *
 * @see returnEarly
 */
fun MutableMethod.returnLate(value: Float) {
    check(returnType.first() == 'F') { RETURN_TYPE_MISMATCH }
    overrideReturnValue(value.toString(), true)
}

/**
 * Overrides all return statements with a constant `Double` value.
 * All method code is executed the same as unpatched.
 *
 * @see returnEarly
 */
fun MutableMethod.returnLate(value: Double) {
    check(returnType.first() == 'D') { RETURN_TYPE_MISMATCH }
    overrideReturnValue(value.toString(), true)
}

/**
 * Overrides all return statements with a constant String value.
 * All method code is executed the same as unpatched.
 *
 * Target method must have return type
 * Ljava/lang/String; or Ljava/lang/CharSequence;
 *
 * @see returnEarly
 */
fun MutableMethod.returnLate(value: String) {
    check(returnType == "Ljava/lang/String;" || returnType == "Ljava/lang/CharSequence;") {
        RETURN_TYPE_MISMATCH
    }
    overrideReturnValue(value, true)
}

private fun MutableMethod.overrideReturnValue(value: String, returnLate: Boolean) {
    val instructions = if (returnType == "Ljava/lang/String;" || returnType == "Ljava/lang/CharSequence;" ) {
        """
            const-string v0, "$value"
            return-object v0
        """
    } else when (returnType.first()) {
        // If return type is an object, always return null.
        'L', '[' -> {
            """
                const/4 v0, 0x0
                return-object v0
            """
        }

        'V' -> {
            "return-void"
        }

        'B', 'Z' -> {
            """
                const/4 v0, $value
                return v0
            """
        }

        'S', 'C' -> {
            """
                const/16 v0, $value
                return v0
            """
        }

        'I', 'F' -> {
            """
                const v0, $value
                return v0
            """
        }

        'J', 'D' -> {
            """
                const-wide v0, $value
                return-wide v0
            """
        }

        else -> throw Exception("Return type is not supported: $this")
    }

    if (returnLate) {
        findInstructionIndicesReversedOrThrow {
            opcode == RETURN || opcode == RETURN_WIDE || opcode == RETURN_OBJECT
        }.forEach { index ->
            addInstructionsAtControlFlowLabel(index, instructions)
        }
    } else {
        addInstructions(0, instructions)
    }
}

/**
 * Remove the given AccessFlags from the field.
 */
internal fun MutableField.removeFlags(vararg flags: AccessFlags) {
    val bitField = flags.map { it.value }.reduce { acc, flag -> acc and flag }
    this.accessFlags = this.accessFlags and bitField.inv()
}

internal fun BytecodePatchContext.addStaticFieldToExtension(
    className: String,
    methodName: String,
    fieldName: String,
    objectClass: String,
    smaliInstructions: String
) {
    val classDef = classes.find { classDef -> classDef.type == className }
        ?: throw PatchException("No matching methods found in: $className")
    val mutableClass = proxy(classDef).mutableClass

    val objectCall = "$mutableClass->$fieldName:$objectClass"

    mutableClass.apply {
        methods.first { method -> method.name == methodName }.apply {
            staticFields.add(
                ImmutableField(
                    definingClass,
                    fieldName,
                    objectClass,
                    AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                    null,
                    annotations,
                    null
                ).toMutable()
            )

            addInstructionsWithLabels(
                0,
                """
                    sget-object v0, $objectCall
                """ + smaliInstructions
            )
        }
    }
}

/**
 * Set the custom condition for this fingerprint to check for a literal value.
 *
 * @param literalSupplier The supplier for the literal value to check for.
 */
// TODO: add a way for subclasses to also use their own custom fingerprint.
fun FingerprintBuilder.literal(literalSupplier: () -> Long) {
    custom { method, _ ->
        method.containsLiteralInstruction(literalSupplier())
    }
}

private class InstructionUtils {
    companion object {
        val branchOpcodes: EnumSet<Opcode> = EnumSet.of(
            GOTO, GOTO_16, GOTO_32,
            IF_EQ, IF_NE, IF_LT, IF_GE, IF_GT, IF_LE,
            IF_EQZ, IF_NEZ, IF_LTZ, IF_GEZ, IF_GTZ, IF_LEZ,
            PACKED_SWITCH_PAYLOAD, SPARSE_SWITCH_PAYLOAD
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
    }
}
