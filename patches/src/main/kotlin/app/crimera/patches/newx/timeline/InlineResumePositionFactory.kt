/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.timeline

import app.crimera.bytecode.insertHook
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.valueReachesRegister
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.numberOfParameterRegisters
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x

internal data class InlineResumePositionFactory(
    val method: MutableMethod,
    val clock: MethodReference,
)

/** Copy only a closed, exception-free factory region; retain the APK's selection algorithm. */
internal fun resolveInlineResumePositionFactory(
    source: Method,
    parameterTypes: List<String>,
    resumeType: String,
    itemsField: FieldReference,
    holderRegister: Int,
): InlineResumePositionFactory {
    val code = source.instructions.toList()
    val constructorIndex = requireExactlyOne(
        "NewX inline resume-position constructor",
        code.indices.filter { index ->
            val reference = code[index].getReference<MethodReference>()
            code[index].opcode == Opcode.INVOKE_DIRECT_RANGE &&
                reference?.definingClass == resumeType && reference.name == "<init>" &&
                reference.parameterTypes.map(CharSequence::toString) ==
                listOf("Ljava/lang/String;", "J", "I", "I", "I", "Lkotlin/time/Instant;")
        },
    )
    val constructorRegisters = code[constructorIndex].registersUsed
    val instantRegister = constructorRegisters.last()
    val clockIndex = requireExactlyOne(
        "NewX inline resume-position clock",
        (0 until constructorIndex).filter { index ->
            val reference = code[index].getReference<MethodReference>() ?: return@filter false
            val result = code.getOrNull(index + 1) as? OneRegisterInstruction ?: return@filter false
            code[index].opcode == Opcode.INVOKE_INTERFACE && reference.name == "now" &&
                reference.parameterTypes.isEmpty() && reference.returnType == "Lkotlin/time/Instant;" &&
                result.opcode == Opcode.MOVE_RESULT_OBJECT &&
                code.valueReachesRegister(index + 1, result.registerA, constructorIndex, instantRegister)
        },
    )
    val start = clockIndex + 2
    val resultMove = code.getOrNull(constructorIndex + 1) as? TwoRegisterInstruction
        ?: throw PatchException("NewX inline resume-position constructor has no result alias")
    if (resultMove.opcode != Opcode.MOVE_OBJECT || resultMove.registerB != constructorRegisters.first()) {
        throw PatchException("NewX inline resume-position constructor result is not preserved")
    }
    val end = constructorIndex + 2
    val offsets = code.runningFold(0) { address, instruction -> address + instruction.codeUnits }
    if (source.implementation!!.tryBlocks.any { block ->
            block.startCodeAddress < offsets[end] &&
                block.startCodeAddress + block.codeUnitCount > offsets[start]
        }
    ) throw PatchException("NewX inline resume-position factory intersects a try block")

    val body = code.subList(start, end) + ImmutableInstruction11x(Opcode.RETURN_OBJECT, resultMove.registerA)
    val inputs = resumeFactoryInputs(body)
    fun fieldInput(fieldMatches: (FieldReference) -> Boolean): Int = requireExactlyOne(
        "NewX inline resume-position field input",
        inputs.filter { register ->
            (0 until start).any { index ->
                val instruction = code[index]
                val field = instruction.getReference<FieldReference>() ?: return@any false
                val read = instruction as? TwoRegisterInstruction ?: return@any false
                instruction.opcode == Opcode.IGET_OBJECT && fieldMatches(field) &&
                    code.valueReachesRegister(index, read.registerA, start, register)
            }
        },
    )
    val itemsRegister = fieldInput { it.toString() == itemsField.toString() }
    val rowsRegister = fieldInput { it.type == "Ljava/util/ArrayList;" }
    if (fieldInput { it.type == parameterTypes[2] } != holderRegister) {
        throw PatchException("NewX inline resume-position holder no longer reaches the factory")
    }
    val clockResult = (code[clockIndex + 1] as OneRegisterInstruction).registerA
    val arguments = listOf(itemsRegister, rowsRegister, holderRegister, clockResult)
    if (arguments.distinct().size != 4 || !inputs.containsAll(arguments)) {
        throw PatchException("NewX inline resume-position factory inputs disagree: $inputs / $arguments")
    }
    val constants = inputs - arguments.toSet()
    val firstParameter = source.implementation!!.registerCount - source.numberOfParameterRegisters
    if (code.any { it.opcode == Opcode.PACKED_SWITCH || it.opcode == Opcode.SPARSE_SWITCH }) {
        throw PatchException("NewX inline resume-position seed dominance crosses a switch")
    }
    val constantSeeds = constants.associateWith { register ->
            // newx-resolver-lint: allow instruction-order raw-last because the definition must dominate entry with no later write
            val definition = (0 until start).lastOrNull { index ->
                val instruction = code[index]
                val destination = (instruction as? OneRegisterInstruction)?.registerA
                instruction.opcode.setsRegister() &&
                    (destination == register || instruction.opcode.setsWideRegister() && destination == register - 1)
            } ?: throw PatchException("NewX inline resume-position input v$register has no definition in $source")
            val instruction = code[definition]
            if (register >= firstParameter ||
                instruction.opcode !in setOf(Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST, Opcode.CONST_HIGH16) ||
                code.indices.any { index ->
                    if (index in definition until start) return@any false
                    val branch = code[index] as? OffsetInstruction ?: return@any false
                    val target = offsets[index] + branch.codeOffset
                    target > offsets[definition] && target <= offsets[start]
                }) {
                throw PatchException(
                    "NewX inline resume-position input v$register is not a dominating narrow constant in $source",
                )
            }
            // Hoisted booleans, nulls and integer seeds retain their original value, not an assumed zero.
            (instruction as NarrowLiteralInstruction).narrowLiteral
    }
    val localCount = body.flatMap { instruction ->
        instruction.registersUsed + if (instruction.opcode.setsWideRegister()) {
            listOf((instruction as OneRegisterInstruction).registerA + 1)
        } else emptyList()
    }.max() + 1
    if (localCount + 4 > 65535) throw PatchException("NewX inline resume-position factory exceeds register limits")
    val method = MutableMethod(
        ImmutableMethod(
            source.definingClass, "piko\$newxResumePosition",
            parameterTypes.map { ImmutableMethodParameter(it, emptySet(), null) }, resumeType,
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.SYNTHETIC.value,
            emptySet(), emptySet(),
            ImmutableMethodImplementation(localCount + 4, body, emptyList(), emptyList()),
        ),
    )
    method.insertHook(0, relocateBranchTargets = false) {
        arguments.forEachIndexed { index, register -> move(register, method.p0Register + index, parameterTypes[index]) }
        constantSeeds.forEach { (register, value) -> constInt(register, value) }
    }
    return InlineResumePositionFactory(method, code[clockIndex].getReference<MethodReference>()!!)
}

/** Live inputs for the deliberately narrow instruction family used by the inlined factory. */
internal fun resumeFactoryInputs(code: List<Instruction>): Set<Int> {
    val offsets = code.runningFold(0) { address, instruction -> address + instruction.codeUnits }
    val indexByOffset = offsets.dropLast(1).withIndex().associate { it.value to it.index }
    val successors = code.mapIndexed { index, instruction ->
        val opcode = instruction.opcode
        val name = opcode.toString()
        if (opcode == Opcode.MOVE_EXCEPTION || !(name.startsWith("INVOKE_") || name.startsWith("MOVE_") || name.startsWith("CONST") ||
                name.startsWith("IF_") || name.startsWith("GOTO") || opcode in setOf(
                    Opcode.MOVE,
                    Opcode.IGET, Opcode.IGET_OBJECT, Opcode.IGET_WIDE, Opcode.CHECK_CAST,
                    Opcode.NEW_INSTANCE, Opcode.INSTANCE_OF, Opcode.ADD_INT_LIT8, Opcode.RETURN_OBJECT,
                ))
        ) throw PatchException("Unsupported NewX inline resume-position instruction: $opcode")
        val target = (instruction as? OffsetInstruction)?.let {
            indexByOffset[offsets[index] + it.codeOffset]
                ?: throw PatchException("NewX inline resume-position branch escapes factory at $index")
        }
        when {
            opcode == Opcode.RETURN_OBJECT -> emptyList()
            name.startsWith("GOTO") -> listOf(target!!)
            target != null -> listOf(target, index + 1)
            else -> listOf(index + 1)
        }.also { successors ->
            if (successors.any { it !in code.indices }) throw PatchException("NewX inline factory falls through")
        }
    }
    val live = Array(code.size) { emptySet<Int>() }
    var changed: Boolean
    do {
        changed = false
        for (index in code.indices.reversed()) {
            val instruction = code[index]
            val destination = if (instruction.opcode.setsRegister()) (instruction as OneRegisterInstruction).registerA else null
            val writes = if (destination == null) emptySet() else buildSet {
                add(destination)
                if (instruction.opcode.setsWideRegister()) add(destination + 1)
            }
            val registers = instruction.registersUsed
            val reads = (if (destination != null && instruction.opcode != Opcode.CHECK_CAST) registers.drop(1) else registers).toMutableSet()
            if (instruction.opcode in setOf(Opcode.MOVE_WIDE, Opcode.MOVE_WIDE_FROM16, Opcode.MOVE_WIDE_16)) {
                reads.add((instruction as TwoRegisterInstruction).registerB + 1)
            }
            val next = reads + (successors[index].flatMap { live[it] }.toSet() - writes)
            if (next != live[index]) { live[index] = next; changed = true }
        }
    } while (changed)
    return live.first()
}
