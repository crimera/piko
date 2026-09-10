package app.crimera.patches.newx.models

import app.crimera.patches.newx.utils.requireAtMostOne
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import java.util.ArrayDeque

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"

context(_: BytecodePatchContext)
internal fun Fingerprint.requireSingle(target: String): Match {
    val matches = scopedMatchAll()
    return requireExactlyOne("NewX $target", matches)
}
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"

internal fun Match.fieldForToStringLabel(label: String): FieldReference =
    originalMethod.fieldForToStringLabel(label)

internal fun Method.fieldForToStringLabel(label: String): FieldReference {
    val instructions = implementation?.instructions?.toList().orEmpty()
    val labelIndices = instructions.mapIndexedNotNull { index, instruction ->
        index.takeIf {
            instruction.getReference<StringReference>()?.string == label
        }
    }
    val labelIndex = requireExactlyOne("NewX model label '$label' in $this", labelIndices)

    val labelInstruction = instructions[labelIndex] as? OneRegisterInstruction
        ?: throw PatchException("NewX model label '$label' has an unsupported register layout in $this")
    val labelRegister = labelInstruction.registerA
    val labelConsumers = instructions.findFirstMethodConsumers(labelIndex, labelRegister)

    val directFields = labelConsumers.flatMap { labelConsumer ->
        val reference = instructions[labelConsumer.index].getReference<MethodReference>()
            ?: return@flatMap emptyList()
        if (!reference.isStringBuilderLabelConsumer()) return@flatMap emptyList()
        instructions.findStringBuilderValueAppendsAfter(labelConsumer.index).flatMap { valueAppendIndex ->
            val valueRegister = instructions[valueAppendIndex].singleArgumentRegister()
                ?: return@flatMap emptyList()
            instructions.findFieldsForRegister(
                register = valueRegister,
                fromIndex = 0,
                untilIndex = valueAppendIndex,
                definingClass = definingClass,
            )
        }
    }
    if (directFields.isNotEmpty()) {
        return requireSingleToStringField(label, toString(), directFields)
    }

    val helperCandidates = buildList {
        labelConsumers.forEach { consumer ->
            val helperIndex = consumer.index
            val instruction = instructions[helperIndex]
            val argumentRegisters = consumer.argumentRegisters
            val labelArgumentIndex = argumentRegisters.indexOfFirst { it in consumer.labelRegisters }
            if (labelArgumentIndex < 0 || labelArgumentIndex + 1 >= argumentRegisters.size) {
                return@forEach
            }
            val valueArgumentRegister = argumentRegisters[labelArgumentIndex + 1]
            instructions.findFieldsForRegister(
                register = valueArgumentRegister,
                fromIndex = labelIndex + 1,
                untilIndex = helperIndex,
                definingClass = definingClass,
            ).forEach(::add)
            instructions.findFieldsForRegister(
                register = valueArgumentRegister,
                fromIndex = 0,
                untilIndex = labelIndex,
                definingClass = definingClass,
            ).forEach(::add)
        }
    }
    return requireSingleToStringField(label, toString(), helperCandidates)
}

internal fun requireSingleToStringField(
    label: String,
    owner: String,
    candidates: List<FieldReference>,
): FieldReference {
    val distinct = candidates.distinctBy(FieldReference::toString)
    return requireExactlyOne("NewX model field for '$label' in $owner", distinct)
}

internal fun Match.fieldForBooleanToStringLabel(label: String): FieldReference {
    val instructions = originalMethod.implementation?.instructions?.toList().orEmpty()
    val labelIndices = instructions.mapIndexedNotNull { index, instruction ->
        index.takeIf { instruction.getReference<StringReference>()?.string == label }
    }
    requireExactlyOne("NewX boolean model label '$label' in $originalMethod", labelIndices)
    val fields = instructions.mapNotNull { instruction ->
        if (instruction.opcode != Opcode.IGET_BOOLEAN) return@mapNotNull null
        instruction.getReference<FieldReference>()?.takeIf { field ->
            field.definingClass == originalMethod.definingClass
        }
    }.distinctBy(FieldReference::toString)
    return requireExactlyOne("NewX boolean model field after '$label' in $originalMethod", fields)
}

internal fun com.android.tools.smali.dexlib2.iface.ClassDef.requireSingleInstanceField(
    type: String,
    semanticName: String,
): FieldReference {
    val matches = fields.filter { field ->
        field.type == type && !AccessFlags.STATIC.isSet(field.accessFlags)
    }
    return requireExactlyOne("NewX $semanticName field of type $type in $this", matches)
}

internal fun MutableClass.requirePublicFields(fields: List<FieldReference>) {
    fields.forEach { field ->
        val definition = requireExactlyOne(
            "NewX model field definition $field in $this",
            this.fields.filter { candidate -> candidate.toString() == field.toString() },
        )
        if (AccessFlags.PUBLIC.isSet(definition.accessFlags)) return@forEach
        throw PatchException(
            "NewX generated bridge requires a public model field: $field in $this",
        )
    }
}

internal data class ModelFieldAccessor(
    val field: FieldReference,
    val getter: MethodReference?,
)

/**
 * Resolves release-specific model access at patch time.
 *
 * BETA PATH: private model fields are read through generated getters.
 * ALPHA PATH: public model fields use direct iget instructions.
 * TODO: Remove the public-field fallback when alpha compatibility is deprecated.
 */
internal fun MutableClass.resolveFieldAccessor(
    field: FieldReference,
    semanticName: String,
): ModelFieldAccessor {
    val suffix = field.name.replaceFirstChar { character -> character.uppercaseChar() }
    val getterNames =
        if (field.type == "Z") listOf(field.name, "is$suffix", "get$suffix").distinct()
        else listOf("get$suffix")
    val getterMatches = methods.filter { method ->
        method.name in getterNames &&
            method.parameterTypes.isEmpty() &&
            method.returnType == field.type
    }
    // BETA PATH: prefer the stable getter exposed by the private model.
    val getter = requireAtMostOne("NewX $semanticName getter for $field in $this", getterMatches)
    if (getter != null) {
        return ModelFieldAccessor(field, getter)
    }
    // ALPHA PATH: fall back to the validated public field.
    val definition = requireAtMostOne(
        "NewX $semanticName field definition $field in $this",
        fields.filter { candidate -> candidate.toString() == field.toString() },
    )
    if (definition != null && AccessFlags.PUBLIC.isSet(definition.accessFlags)) {
        return ModelFieldAccessor(field, null)
    }
    throw PatchException("NewX $semanticName has neither a getter nor a public field: $field in $this")
}

internal fun MutableClass.requireGetter(
    field: FieldReference,
    semanticName: String,
): MethodReference = resolveFieldAccessor(field, semanticName).getter
    ?: throw PatchException("NewX $semanticName has no getter: $field in $this")

internal fun ModelFieldAccessor.readObject(register: String): String =
    getter?.let { getter ->
        "invoke-virtual {$register}, ${getter.smaliReference()}\nmove-result-object $register"
    } ?: "iget-object $register, $register, $field"

internal fun ModelFieldAccessor.readBoolean(register: String): String =
    getter?.let { getter ->
        "invoke-virtual {$register}, ${getter.smaliReference()}\nmove-result $register"
    } ?: "iget-boolean $register, $register, $field"

internal fun ModelFieldAccessor.readWide(receiver: String, destination: String): String =
    getter?.let { getter ->
        "invoke-virtual {$receiver}, ${getter.smaliReference()}\nmove-result-wide $destination"
    } ?: "iget-wide $destination, $receiver, $field"

internal fun MutableClass.patchObjectFieldGetter(
    name: String,
    ownerDescriptor: String,
    field: FieldReference,
    returnType: String = OBJECT_DESCRIPTOR,
) = patchBridge(
    name,
    OBJECT_DESCRIPTOR,
    returnType,
    "check-cast p0, $ownerDescriptor\niget-object p0, p0, $field\nreturn-object p0",
)

internal fun MutableClass.patchObjectMethodGetter(
    name: String,
    ownerDescriptor: String,
    getter: MethodReference,
    returnType: String = OBJECT_DESCRIPTOR,
) = patchBridge(
    name,
    OBJECT_DESCRIPTOR,
    returnType,
    "check-cast p0, $ownerDescriptor\n" +
        "invoke-virtual {p0}, ${getter.smaliReference()}\n" +
        "move-result-object p0\nreturn-object p0",
)

internal fun MutableClass.patchObjectAccessorGetter(
    name: String,
    ownerDescriptor: String,
    accessor: ModelFieldAccessor,
    returnType: String = OBJECT_DESCRIPTOR,
) = patchBridge(
    name,
    OBJECT_DESCRIPTOR,
    returnType,
    "check-cast p0, $ownerDescriptor\n" +
        "${accessor.readObject("p0")}\nreturn-object p0",
)

internal fun MutableClass.patchBooleanFieldGetter(
    name: String,
    ownerDescriptor: String,
    field: FieldReference,
) = patchBridge(
    name,
    OBJECT_DESCRIPTOR,
    "Z",
    "check-cast p0, $ownerDescriptor\niget-boolean p0, p0, $field\nreturn p0",
)

internal fun MutableClass.patchBooleanMethodGetter(
    name: String,
    ownerDescriptor: String,
    getter: MethodReference,
) = patchBridge(
    name,
    OBJECT_DESCRIPTOR,
    "Z",
    "check-cast p0, $ownerDescriptor\n" +
        "invoke-virtual {p0}, ${getter.smaliReference()}\n" +
        "move-result p0\nreturn p0",
)

internal fun MutableClass.patchBooleanAccessorGetter(
    name: String,
    ownerDescriptor: String,
    accessor: ModelFieldAccessor,
) = patchBridge(
    name,
    OBJECT_DESCRIPTOR,
    "Z",
    "check-cast p0, $ownerDescriptor\n" +
        "${accessor.readBoolean("p0")}\nreturn p0",
)

internal fun MutableClass.patchWideFieldGetter(
    name: String,
    ownerDescriptor: String,
    field: FieldReference,
) = patchBridge(
    name,
    OBJECT_DESCRIPTOR,
    "J",
    "check-cast p0, $ownerDescriptor\niget-wide v0, p0, $field\nreturn-wide v0",
)

internal fun MutableClass.patchWideMethodGetter(
    name: String,
    ownerDescriptor: String,
    getter: MethodReference,
) = patchBridge(
    name,
    OBJECT_DESCRIPTOR,
    "J",
    "check-cast p0, $ownerDescriptor\n" +
        "invoke-virtual {p0}, ${getter.smaliReference()}\n" +
        "move-result-wide v0\nreturn-wide v0",
)

internal fun MutableClass.patchWideAccessorGetter(
    name: String,
    ownerDescriptor: String,
    accessor: ModelFieldAccessor,
) = patchBridge(
    name,
    OBJECT_DESCRIPTOR,
    "J",
    "check-cast p0, $ownerDescriptor\n" +
        "${accessor.readWide("p0", "v0")}\nreturn-wide v0",
)

internal fun MutableClass.patchBridge(
    name: String,
    parameters: String,
    returnType: String,
    instructions: String,
) {
    val matches = methods.filter { method ->
        method.name == name &&
            method.parameterTypes.joinToString("") == parameters &&
            method.returnType == returnType
    }
    requireExactlyOne("NewX bridge $name($parameters)$returnType in $this", matches)
        .addInstructions(0, instructions.trimIndent())
}

context(context: BytecodePatchContext)
internal fun MethodReference.resolveCurrentMethod(label: String): Method {
    val owner = context.classDefByOrNull(definingClass)
        ?: throw PatchException("NewX $label owner was not found: $definingClass")
    val matches = owner.methods.filter { method -> matches(method) }
    return requireExactlyOne("NewX $label matching $this in $owner", matches)
}

context(context: BytecodePatchContext)
internal fun MethodReference.resolveMutableMethodOwner(
    label: String,
): Pair<MutableClass, MutableMethod> {
    val owner = context.mutableClassDefBy(definingClass)
    val matches = owner.methods.filter { method -> matches(method) }
    return owner to requireExactlyOne("NewX $label matching $this in $owner", matches)
}

private fun MethodReference.matches(method: Method): Boolean =
    method.name == name &&
        method.returnType == returnType &&
        method.parameterTypes.map(CharSequence::toString) == parameterTypes.map(CharSequence::toString)

internal fun MethodReference.smaliReference(): String =
    "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

private fun MethodReference.isStringBuilderLabelConsumer(): Boolean =
    definingClass == "Ljava/lang/StringBuilder;" &&
        parameterTypes.map(CharSequence::toString) == listOf(STRING_DESCRIPTOR) &&
        (name == "<init>" || name == "append")

private fun MethodReference.isStringBuilderValueAppend(): Boolean =
    definingClass == "Ljava/lang/StringBuilder;" &&
        name == "append" &&
        parameterTypes.size == 1

private data class LabelMethodConsumer(
    val index: Int,
    val argumentRegisters: List<Int>,
    val labelRegisters: Set<Int>,
)

private data class RegisterFlowState(
    val index: Int,
    val register: Int,
)

private data class LabelFlowState(
    val index: Int,
    val registers: Set<Int>,
)

private fun List<Instruction>.findFirstMethodConsumers(
    labelIndex: Int,
    labelRegister: Int,
): List<LabelMethodConsumer> {
    if (labelIndex + 1 >= size) return emptyList()
    val successors = controlFlowSuccessors()
    val pending = ArrayDeque<LabelFlowState>()
    val seen = mutableSetOf<LabelFlowState>()
    val consumers = linkedMapOf<Int, LabelMethodConsumer>()
    pending.add(LabelFlowState(labelIndex + 1, setOf(labelRegister)))

    while (pending.isNotEmpty()) {
        val state = pending.removeFirst()
        if (state.index !in indices || !seen.add(state)) continue
        val instruction = this[state.index]
        val reference = instruction.getReference<MethodReference>()
        val argumentRegisters = instruction.registersUsed
        if (reference != null && argumentRegisters.any { it in state.registers }) {
            consumers.putIfAbsent(
                state.index,
                LabelMethodConsumer(state.index, argumentRegisters, state.registers),
            )
            continue
        }

        val nextRegisters = instruction.updateTrackedRegisters(state.registers)
        if (nextRegisters.isEmpty()) continue
        successors[state.index].forEach { successor ->
            pending.add(LabelFlowState(successor, nextRegisters))
        }
    }
    return consumers.values.toList()
}

private fun List<Instruction>.findStringBuilderValueAppendsAfter(
    consumerIndex: Int,
): List<Int> {
    val successors = controlFlowSuccessors()
    val pending = ArrayDeque<Int>()
    val seen = mutableSetOf<Int>()
    val appends = linkedSetOf<Int>()
    successors[consumerIndex].forEach(pending::add)

    while (pending.isNotEmpty()) {
        val index = pending.removeFirst()
        if (index !in indices || !seen.add(index)) continue
        val instruction = this[index]
        if (instruction.getReference<MethodReference>()?.isStringBuilderValueAppend() == true) {
            appends += index
            continue
        }
        successors[index].forEach(pending::add)
    }
    return appends.toList()
}

private fun List<Instruction>.findFieldsForRegister(
    register: Int,
    fromIndex: Int,
    untilIndex: Int,
    definingClass: String,
): List<FieldReference> {
    if (fromIndex >= untilIndex || untilIndex <= 0) return emptyList()
    val lowerBound = fromIndex.coerceAtLeast(0)
    val upperBound = untilIndex.coerceAtMost(size)
    if (lowerBound >= upperBound) return emptyList()

    val predecessors = controlFlowPredecessors()
    val pending = ArrayDeque<RegisterFlowState>()
    val seen = mutableSetOf<RegisterFlowState>()
    val fields = linkedMapOf<String, FieldReference>()
    pending.add(RegisterFlowState(upperBound - 1, register))

    while (pending.isNotEmpty()) {
        val state = pending.removeFirst()
        if (state.index !in lowerBound until upperBound || !seen.add(state)) continue
        val instruction = this[state.index]

        val fieldRead = instruction.fieldReadForDestination(state.register)
        if (fieldRead != null) {
            if (fieldRead.definingClass == definingClass) {
                fields.putIfAbsent(fieldRead.toString(), fieldRead)
            }
            continue
        }

        val moveSource = instruction.moveSourceForDestination(state.register)
        if (moveSource != null) {
            predecessors[state.index].forEach { predecessor ->
                pending.add(RegisterFlowState(predecessor, moveSource))
            }
            continue
        }

        if (instruction.writesRegister(state.register)) continue
        predecessors[state.index].forEach { predecessor ->
            pending.add(RegisterFlowState(predecessor, state.register))
        }
    }
    return fields.values.toList()
}

private fun Instruction.updateTrackedRegisters(registers: Set<Int>): Set<Int> {
    val move = moveInstruction()
    if (move != null) {
        val next = registers.toMutableSet()
        next.remove(move.registerA)
        if (move.registerB in registers) next += move.registerA
        return next
    }
    return destinationRegister()?.let(registers::minus) ?: registers
}

private fun Instruction.moveSourceForDestination(register: Int): Int? {
    val move = moveInstruction() ?: return null
    return move.registerB.takeIf { move.registerA == register }
}

private fun Instruction.moveInstruction(): TwoRegisterInstruction? =
    takeIf { opcode in MOVE_OPCODES }?.let { it as? TwoRegisterInstruction }

private fun Instruction.fieldReadForDestination(register: Int): FieldReference? {
    if (opcode in INSTANCE_FIELD_READ_OPCODES) {
        val instruction = this as? TwoRegisterInstruction ?: return null
        if (instruction.registerA != register) return null
        return getReference<FieldReference>()
    }
    if (opcode in STATIC_FIELD_READ_OPCODES) {
        val instruction = this as? OneRegisterInstruction ?: return null
        if (instruction.registerA != register) return null
        return getReference<FieldReference>()
    }
    return null
}

private fun Instruction.destinationRegister(): Int? {
    if (opcode in MOVE_OPCODES) return (this as? TwoRegisterInstruction)?.registerA
    if (opcode in INSTANCE_FIELD_READ_OPCODES) {
        return (this as? TwoRegisterInstruction)?.registerA
    }
    if (opcode in STATIC_FIELD_READ_OPCODES) {
        return (this as? OneRegisterInstruction)?.registerA
    }
    if (opcode in NON_DESTINATION_OPCODES) return null
    if (this is ThreeRegisterInstruction) return registerA
    if (this is TwoRegisterInstruction) return registerA
    if (this is OneRegisterInstruction) return registerA
    return null
}

private fun Instruction.writesRegister(register: Int): Boolean =
    destinationRegister() == register

private fun List<Instruction>.controlFlowPredecessors(): Array<List<Int>> {
    val predecessors = Array(size) { mutableListOf<Int>() }
    controlFlowSuccessors().forEachIndexed { index, successors ->
        successors.forEach { successor ->
            predecessors[successor] += index
        }
    }
    return Array(size) { predecessors[it].toList() }
}

private fun List<Instruction>.controlFlowSuccessors(): Array<Set<Int>> {
    val offsets = IntArray(size)
    val indexByOffset = mutableMapOf<Int, Int>()
    var codeOffset = 0
    forEachIndexed { index, instruction ->
        offsets[index] = codeOffset
        indexByOffset[codeOffset] = index
        codeOffset += instruction.codeUnits
    }

    fun branchTarget(index: Int): Int? {
        val offsetInstruction = this[index] as? OffsetInstruction ?: return null
        return indexByOffset[offsets[index] + offsetInstruction.codeOffset]
    }

    fun fallthrough(index: Int): Int? = (index + 1).takeIf { it < size }

    return Array(size) { index ->
        val instruction = this[index]
        when {
            instruction is SwitchPayload -> emptySet()
            instruction.opcode in GOTO_OPCODES ->
                setOfNotNull(branchTarget(index))
            instruction.opcode in CONDITIONAL_BRANCH_OPCODES ->
                setOfNotNull(branchTarget(index), fallthrough(index))
            instruction.opcode in SWITCH_OPCODES -> {
                val payload = branchTarget(index)?.let { this[it] as? SwitchPayload }
                buildSet {
                    fallthrough(index)?.let(::add)
                    payload?.switchElements?.forEach { element ->
                        indexByOffset[offsets[index] + element.offset]?.let(::add)
                    }
                }
            }
            instruction.opcode in TERMINAL_OPCODES -> emptySet()
            else -> setOfNotNull(fallthrough(index))
        }
    }
}

private val MOVE_OPCODES =
    setOf(
        Opcode.MOVE,
        Opcode.MOVE_FROM16,
        Opcode.MOVE_16,
        Opcode.MOVE_WIDE,
        Opcode.MOVE_WIDE_FROM16,
        Opcode.MOVE_WIDE_16,
        Opcode.MOVE_OBJECT,
        Opcode.MOVE_OBJECT_FROM16,
        Opcode.MOVE_OBJECT_16,
    )

private val INSTANCE_FIELD_READ_OPCODES =
    setOf(
        Opcode.IGET,
        Opcode.IGET_WIDE,
        Opcode.IGET_OBJECT,
        Opcode.IGET_BOOLEAN,
        Opcode.IGET_BYTE,
        Opcode.IGET_CHAR,
        Opcode.IGET_SHORT,
    )

private val STATIC_FIELD_READ_OPCODES =
    setOf(
        Opcode.SGET,
        Opcode.SGET_WIDE,
        Opcode.SGET_OBJECT,
        Opcode.SGET_BOOLEAN,
        Opcode.SGET_BYTE,
        Opcode.SGET_CHAR,
        Opcode.SGET_SHORT,
    )

private val GOTO_OPCODES = setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)

private val CONDITIONAL_BRANCH_OPCODES =
    setOf(
        Opcode.IF_EQ,
        Opcode.IF_NE,
        Opcode.IF_LT,
        Opcode.IF_GE,
        Opcode.IF_GT,
        Opcode.IF_LE,
        Opcode.IF_EQZ,
        Opcode.IF_NEZ,
        Opcode.IF_LTZ,
        Opcode.IF_GEZ,
        Opcode.IF_GTZ,
        Opcode.IF_LEZ,
    )

private val SWITCH_OPCODES = setOf(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH)

private val TERMINAL_OPCODES =
    setOf(
        Opcode.RETURN_VOID,
        Opcode.RETURN,
        Opcode.RETURN_WIDE,
        Opcode.RETURN_OBJECT,
        Opcode.THROW,
    )

private val NON_DESTINATION_OPCODES =
    setOf(
        Opcode.NOP,
        Opcode.MONITOR_ENTER,
        Opcode.MONITOR_EXIT,
        Opcode.CHECK_CAST,
        Opcode.THROW,
        Opcode.GOTO,
        Opcode.GOTO_16,
        Opcode.GOTO_32,
        Opcode.PACKED_SWITCH,
        Opcode.SPARSE_SWITCH,
        Opcode.FILL_ARRAY_DATA,
        Opcode.RETURN_VOID,
        Opcode.RETURN,
        Opcode.RETURN_WIDE,
        Opcode.RETURN_OBJECT,
        Opcode.IF_EQ,
        Opcode.IF_NE,
        Opcode.IF_LT,
        Opcode.IF_GE,
        Opcode.IF_GT,
        Opcode.IF_LE,
        Opcode.IF_EQZ,
        Opcode.IF_NEZ,
        Opcode.IF_LTZ,
        Opcode.IF_GEZ,
        Opcode.IF_GTZ,
        Opcode.IF_LEZ,
        Opcode.APUT,
        Opcode.APUT_WIDE,
        Opcode.APUT_OBJECT,
        Opcode.APUT_BOOLEAN,
        Opcode.APUT_BYTE,
        Opcode.APUT_CHAR,
        Opcode.APUT_SHORT,
        Opcode.IPUT,
        Opcode.IPUT_WIDE,
        Opcode.IPUT_OBJECT,
        Opcode.IPUT_BOOLEAN,
        Opcode.IPUT_BYTE,
        Opcode.IPUT_CHAR,
        Opcode.IPUT_SHORT,
        Opcode.SPUT,
        Opcode.SPUT_WIDE,
        Opcode.SPUT_OBJECT,
        Opcode.SPUT_BOOLEAN,
        Opcode.SPUT_BYTE,
        Opcode.SPUT_CHAR,
        Opcode.SPUT_SHORT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.INVOKE_SUPER,
        Opcode.INVOKE_DIRECT,
        Opcode.INVOKE_STATIC,
        Opcode.INVOKE_INTERFACE,
        Opcode.INVOKE_VIRTUAL_RANGE,
        Opcode.INVOKE_SUPER_RANGE,
        Opcode.INVOKE_DIRECT_RANGE,
        Opcode.INVOKE_STATIC_RANGE,
        Opcode.INVOKE_INTERFACE_RANGE,
    )

private fun Instruction.singleArgumentRegister(): Int? = registersUsed.getOrNull(1)
