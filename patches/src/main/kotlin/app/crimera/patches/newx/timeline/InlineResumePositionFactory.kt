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
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x

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

    var body = code.subList(start, end) + ImmutableInstruction11x(Opcode.RETURN_OBJECT, resultMove.registerA)
    var inputs = resumeFactoryInputs(body)
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
    val constantSeeds = mutableMapOf<Int, Int>()
    val inputSpills = (inputs - arguments.toSet()).mapNotNull { register ->
        val constantSeed = try {
            resolveDominatingNarrowConstant(source, code, offsets, start, register)
        } catch (_: PatchException) {
            null
        }
        if (constantSeed != null) {
            constantSeeds[register] = constantSeed
            return@mapNotNull null
        }
        resolveRedundantWideObjectSpill(body, register, source)?.let { instructions -> register to instructions }
    }
    if (inputSpills.isNotEmpty()) {
        val removedInstructions = inputSpills.flatMap { (_, instructions) -> instructions }.toSet()
        // Preserve every branch's original code-unit offsets while removing value-only moves.
        body = body.flatMapIndexed { index, instruction ->
            if (index !in removedInstructions) listOf(instruction)
            else List(instruction.codeUnits) { ImmutableInstruction10x(Opcode.NOP) }
        }
        inputs = resumeFactoryInputs(body)
        if (inputSpills.any { (register, _) -> register in inputs }) {
            throw PatchException("NewX inline resume-position preserved input remains live after spill removal in $source")
        }
    }
    val requiredConstantSeeds = (inputs - arguments.toSet()).associateWith { register ->
        constantSeeds[register] ?: resolveDominatingNarrowConstant(source, code, offsets, start, register)
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
        requiredConstantSeeds.forEach { (register, value) -> constInt(register, value) }
    }
    return InlineResumePositionFactory(method, code[clockIndex].getReference<MethodReference>()!!)
}

/** Remove only an object spill whose sole purpose is preserving a wide-overlapped local. */
private fun resolveRedundantWideObjectSpill(
    code: List<Instruction>,
    register: Int,
    source: Method,
): Set<Int>? {
    if (register <= 0 || code.none { instruction ->
            instruction.opcode.setsWideRegister() && instruction.destinationRegisterOrNull() == register - 1
        }
    ) return null

    val successors = resumeFactorySuccessors(code)
    val implementation = source.implementation
        ?: throw PatchException("NewX inline resume-position source has no implementation: $source")
    val aliasesIn = arrayOfNulls<BooleanArray>(code.size)
    aliasesIn[0] = BooleanArray(implementation.registerCount).also { it[register] = true }
    val work = ArrayDeque<Int>()
    work.add(0)
    val removable = mutableSetOf<Int>()
    while (work.isNotEmpty()) {
        val index = work.removeFirst()
        val incoming = aliasesIn[index] ?: continue
        val instruction = code[index]
        val destination = instruction.destinationRegisterOrNull()
        val reads = instruction.registersUsed.let { registers ->
            if (destination == null) registers else registers.drop(1)
        }
        val originReads = reads.filter { read -> incoming.getOrElse(read) { false } }
        if (originReads.isNotEmpty()) {
            if (instruction.opcode !in OBJECT_MOVE_OPCODES || originReads != listOf((instruction as TwoRegisterInstruction).registerB)) {
                throw PatchException(
                    "NewX inline resume-position input v$register is consumed outside its wide-overlap spill in $source",
                )
            }
            removable += index
        }

        val outgoing = incoming.copyOf()
        if (destination != null) {
            when {
                instruction.opcode in OBJECT_MOVE_OPCODES -> {
                    val move = instruction as TwoRegisterInstruction
                    outgoing[destination] = incoming.getOrElse(move.registerB) { false }
                }
                instruction.opcode == Opcode.CHECK_CAST -> Unit
                else -> outgoing[destination] = false
            }
            if (instruction.opcode.setsWideRegister() && destination + 1 < outgoing.size) {
                outgoing[destination + 1] = false
            }
        }
        successors[index].forEach { next ->
            val prior = aliasesIn[next]
            if (prior == null) {
                aliasesIn[next] = outgoing.copyOf()
                work.add(next)
                return@forEach
            }
            var changed = false
            for (candidate in prior.indices) {
                val merged = prior[candidate] || outgoing[candidate]
                if (merged == prior[candidate]) continue
                prior[candidate] = merged
                changed = true
            }
            if (changed) work.add(next)
        }
    }
    if (removable.isEmpty()) {
        throw PatchException("NewX inline resume-position input v$register has no proven preservation moves in $source")
    }
    return removable
}

/** Every path reaching the extraction edge must carry the same narrow literal. */
private fun resolveDominatingNarrowConstant(
    source: Method,
    code: List<Instruction>,
    offsets: List<Int>,
    start: Int,
    register: Int,
): Int {
    val implementation = source.implementation!!
    if (register >= implementation.registerCount ||
        implementation.tryBlocks.any { block ->
            block.startCodeAddress < offsets[start] &&
                block.startCodeAddress + block.codeUnitCount > 0
        } || code.any { instruction -> instruction.opcode == Opcode.PACKED_SWITCH || instruction.opcode == Opcode.SPARSE_SWITCH }
    ) {
        throw PatchException("NewX inline resume-position seed control flow is not proven for v$register in $source")
    }
    val successors = resumeFactorySuccessors(code)
    val incoming = arrayOfNulls<IntArray>(code.size)
    incoming[0] = IntArray(implementation.registerCount) { UNKNOWN_CONSTANT }
    val work = ArrayDeque<Int>()
    work.add(0)
    while (work.isNotEmpty()) {
        val index = work.removeFirst()
        val state = incoming[index] ?: continue
        val outgoing = state.copyOf()
        val instruction = code[index]
        val destination = instruction.destinationRegisterOrNull()
        if (destination != null) {
            val movedFrom = instruction.moveSourceRegister()
            outgoing[destination] = when {
                instruction.opcode in NARROW_CONSTANT_OPCODES && destination == register ->
                    (instruction as? NarrowLiteralInstruction)?.narrowLiteral ?: UNKNOWN_CONSTANT
                instruction.opcode in MOVE_OPCODES && movedFrom != null -> state.getOrElse(movedFrom) { UNKNOWN_CONSTANT }
                else -> UNKNOWN_CONSTANT
            }
            if (instruction.opcode.setsWideRegister() && destination + 1 < outgoing.size) {
                outgoing[destination + 1] = UNKNOWN_CONSTANT
            }
        }
        successors[index].filter { next -> next <= start }.forEach { next ->
            val prior = incoming[next]
            if (prior == null) {
                incoming[next] = outgoing.copyOf()
                work.add(next)
                return@forEach
            }
            var changed = false
            for (candidate in prior.indices) {
                if (prior[candidate] == outgoing[candidate]) continue
                if (prior[candidate] != UNKNOWN_CONSTANT) {
                    prior[candidate] = UNKNOWN_CONSTANT
                    changed = true
                }
            }
            if (changed) work.add(next)
        }
    }
    val value = incoming[start]?.getOrElse(register) { UNKNOWN_CONSTANT } ?: UNKNOWN_CONSTANT
    if (value == UNKNOWN_CONSTANT) {
        throw PatchException("NewX inline resume-position input v$register is not a dominating narrow constant in $source")
    }
    return value
}

private const val UNKNOWN_CONSTANT = Int.MIN_VALUE
private val NARROW_CONSTANT_OPCODES =
    setOf(Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST, Opcode.CONST_HIGH16)
private val OBJECT_MOVE_OPCODES =
    setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)
private val MOVE_OPCODES =
    OBJECT_MOVE_OPCODES + setOf(Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16)

private fun Instruction.destinationRegisterOrNull(): Int? {
    if (!opcode.setsRegister()) return null
    return when (this) {
        is ThreeRegisterInstruction -> registerA
        is TwoRegisterInstruction -> registerA
        is OneRegisterInstruction -> registerA
        else -> null
    }
}

private fun Instruction.moveSourceRegister(): Int? =
    if (opcode in MOVE_OPCODES) (this as? TwoRegisterInstruction)?.registerB else null

private fun resumeFactorySuccessors(code: List<Instruction>): List<List<Int>> {
    val offsets = code.runningFold(0) { address, instruction -> address + instruction.codeUnits }
    val indexByOffset = offsets.dropLast(1).withIndex().associate { (index, offset) -> offset to index }
    return code.mapIndexed { index, instruction ->
        val opcode = instruction.opcode
        val name = opcode.toString()
        val target = (instruction as? OffsetInstruction)?.let {
            indexByOffset[offsets[index] + it.codeOffset]
                ?: throw PatchException("NewX inline resume-position branch escapes factory at $index")
        }
        when {
            opcode in setOf(
                Opcode.RETURN,
                Opcode.RETURN_OBJECT,
                Opcode.RETURN_VOID,
                Opcode.RETURN_WIDE,
                Opcode.THROW,
            ) -> emptyList()
            name.startsWith("GOTO") -> listOf(target ?: throw PatchException("NewX inline resume-position goto has no target"))
            target != null -> listOf(target, index + 1)
            else -> listOf(index + 1)
        }.also { successors ->
            if (successors.any { it !in code.indices }) throw PatchException("NewX inline factory falls through")
        }
    }
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
                    Opcode.NEW_INSTANCE, Opcode.INSTANCE_OF, Opcode.ADD_INT_LIT8, Opcode.RETURN_OBJECT, Opcode.NOP,
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
            reads.removeAll(instruction.wideReadHighRegisters())
            val next = reads + (successors[index].flatMap { live[it] }.toSet() - writes)
            if (next != live[index]) { live[index] = next; changed = true }
        }
    } while (changed)
    return live.first()
}

internal fun Instruction.wideReadHighRegisters(): Set<Int> {
    if (opcode in INVOKE_OPCODES) {
        val reference = getReference<MethodReference>() ?: return emptySet()
        val registers = registersUsed
        var registerIndex =
            if (opcode == Opcode.INVOKE_STATIC || opcode == Opcode.INVOKE_STATIC_RANGE) 0 else 1
        return reference.parameterTypes.mapNotNull { parameter ->
            val highRegister = registers.getOrNull(registerIndex + 1)
            val width = if (parameter.toString() == "J" || parameter.toString() == "D") 2 else 1
            registerIndex += width
            highRegister.takeIf { width == 2 }
        }.toSet()
    }
    return when (opcode) {
        Opcode.MOVE_WIDE, Opcode.MOVE_WIDE_FROM16, Opcode.MOVE_WIDE_16 ->
            (this as? TwoRegisterInstruction)?.registerB?.plus(1)?.let(::setOf).orEmpty()
        Opcode.RETURN_WIDE ->
            (this as? OneRegisterInstruction)?.registerA?.plus(1)?.let(::setOf).orEmpty()
        Opcode.IPUT_WIDE, Opcode.SPUT_WIDE ->
            registersUsed.firstOrNull()?.plus(1)?.let(::setOf).orEmpty()
        else -> emptySet()
    }
}

private val INVOKE_OPCODES = setOf(
    Opcode.INVOKE_VIRTUAL,
    Opcode.INVOKE_VIRTUAL_RANGE,
    Opcode.INVOKE_SUPER,
    Opcode.INVOKE_SUPER_RANGE,
    Opcode.INVOKE_DIRECT,
    Opcode.INVOKE_DIRECT_RANGE,
    Opcode.INVOKE_STATIC,
    Opcode.INVOKE_STATIC_RANGE,
    Opcode.INVOKE_INTERFACE,
    Opcode.INVOKE_INTERFACE_RANGE,
)
