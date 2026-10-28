package app.crimera.patches.newx.misc.inlineactions

import app.crimera.patches.common.classDefFlatMap
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.newx.models.ResolvedNewXInlineActionModels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val SIZING_TO_STRING_PREFIX = "InlineActionSizing(counterFontSize="
private const val SIZING_ICON_SIZE_LABEL = ", iconSize="
private const val UNKNOWN_VALUE = -1
private const val PARAMETER_VALUE = -2

internal data class ResolvedCapturedInlineActionRenderer(
    val method: MutableMethod,
    val entryRegister: Int,
    val iconSizeReadIndex: Int?,
    val iconSizeRegister: Int?,
    val tintReadIndex: Int,
    val tintRegister: Int,
    val contentTintReadIndex: Int?,
    val contentTintRegister: Int?,
    val tintRenderer: MethodReference,
)

/** Recognizes the captured FunctionN implementation by its entry-field reads and renderer call. */
internal fun Method.isCapturedInlineActionEntryRenderer(
    owner: ClassDef,
    models: ResolvedNewXInlineActionModels,
): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    if (AccessFlags.STATIC.isSet(accessFlags) || returnType != OBJECT_DESCRIPTOR ||
        parameters.isEmpty() || parameters.any { it != OBJECT_DESCRIPTOR }
    ) return false

    val functionArity = owner.interfaces.mapNotNull { type ->
        Regex("Lkotlin/jvm/functions/Function(\\d+);").matchEntire(type.toString())
            ?.groupValues?.get(1)?.toIntOrNull()
    }
    if (functionArity != listOf(parameters.size)) return false

    val code = implementation?.instructions?.toList() ?: return false
    val captureReads = code.mapIndexedNotNull { index, instruction ->
        val field = instruction.getReference<FieldReference>() ?: return@mapIndexedNotNull null
        if (instruction.opcode != Opcode.IGET_OBJECT || field.type != models.inlineActionEntryDescriptor ||
            field.definingClass != owner.type
        ) return@mapIndexedNotNull null
        val registers = instruction as? TwoRegisterInstruction ?: return@mapIndexedNotNull null
        index to registers
    }.filter { (index, registers) ->
        this.provesParameterValue(index, registers.registerB, p0Register)
    }
    if (captureReads.size != 1) return false

    val (captureIndex, capture) = captureReads.single()
    val actionTypeReads = code.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.IGET_OBJECT ||
            instruction.getReference<FieldReference>()?.toString() != models.inlineActionTypeField.toString()
        ) return@mapIndexedNotNull null
        val read = instruction as? TwoRegisterInstruction ?: return@mapIndexedNotNull null
        index to read
    }.filter { (index, read) ->
        this.provesValueFromInstruction(captureIndex, capture.registerA, index, read.registerB)
    }
    val enabledReads = code.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.IGET_BOOLEAN ||
            instruction.getReference<FieldReference>()?.toString() != models.inlineActionEnabledField.toString()
        ) return@mapIndexedNotNull null
        val read = instruction as? TwoRegisterInstruction ?: return@mapIndexedNotNull null
        index to read
    }.filter { (index, read) ->
        this.provesValueFromInstruction(captureIndex, capture.registerA, index, read.registerB)
    }
    if (actionTypeReads.size != 1 || enabledReads.size != 1) return false

    val actionTypeRead = actionTypeReads.single()
    val tintCalls = code.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
            return@mapIndexedNotNull null
        }
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        if (reference.returnType != "V" ||
            reference.parameterTypes.firstOrNull()?.toString() != models.postActionTypeDescriptor
        ) return@mapIndexedNotNull null
        val actionTypeRegister = instruction.argumentRegister(reference, 0) ?: return@mapIndexedNotNull null
        (index to reference).takeIf { candidate ->
            this.provesValueFromInstruction(
                actionTypeRead.first,
                actionTypeRead.second.registerA,
                candidate.first,
                actionTypeRegister,
            )
        }
    }
    return tintCalls.size == 1
}

context(context: BytecodePatchContext)
internal fun resolveCapturedInlineActionEntryRenderer(
    method: MutableMethod,
    owner: ClassDef,
    models: ResolvedNewXInlineActionModels,
    resolveIconSize: Boolean = true,
): ResolvedCapturedInlineActionRenderer {
    val code = method.instructions.toList()
    val captureRead = requireExactlyOne(
        "NewX captured inline-action entry field read in $method",
        code.mapIndexedNotNull { index, instruction ->
            val field = instruction.getReference<FieldReference>() ?: return@mapIndexedNotNull null
            if (instruction.opcode != Opcode.IGET_OBJECT ||
                field.definingClass != owner.type || field.type != models.inlineActionEntryDescriptor
            ) return@mapIndexedNotNull null
            val registers = instruction as? TwoRegisterInstruction ?: return@mapIndexedNotNull null
            (index to registers).takeIf { (_, read) ->
                method.provesParameterValue(index, read.registerB, method.p0Register)
            }
        },
    )
    val entryRegister = captureRead.second.registerA
    val actionTypeRead = requireExactlyOne(
        "NewX captured inline-action type read in $method",
        code.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode != Opcode.IGET_OBJECT ||
                instruction.getReference<FieldReference>()?.toString() != models.inlineActionTypeField.toString()
            ) return@mapIndexedNotNull null
            val read = instruction as? TwoRegisterInstruction ?: return@mapIndexedNotNull null
            (index to read).takeIf { (readIndex, registers) ->
                method.provesValueFromInstruction(captureRead.first, entryRegister, readIndex, registers.registerB)
            }
        },
    )
    requireExactlyOne(
        "NewX captured inline-action enabled read in $method",
        code.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode != Opcode.IGET_BOOLEAN ||
                instruction.getReference<FieldReference>()?.toString() != models.inlineActionEnabledField.toString()
            ) return@mapIndexedNotNull null
            val read = instruction as? TwoRegisterInstruction ?: return@mapIndexedNotNull null
            index.takeIf { method.provesValueFromInstruction(captureRead.first, entryRegister, index, read.registerB) }
        },
    )

    val tintCall = requireExactlyOne(
        "NewX captured inline-action tint renderer call in $method",
        code.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
                return@mapIndexedNotNull null
            }
            val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
            if (reference.returnType != "V" ||
                reference.parameterTypes.firstOrNull()?.toString() != models.postActionTypeDescriptor
            ) return@mapIndexedNotNull null
            val actionTypeRegister = instruction.argumentRegister(reference, 0)
                ?: throw PatchException("NewX captured inline-action renderer has no action-type argument: $instruction")
            (index to reference).takeIf { (callIndex, _) ->
                method.provesValueFromInstruction(
                    actionTypeRead.first,
                    actionTypeRead.second.registerA,
                    callIndex,
                    actionTypeRegister,
                )
            }
        },
    )

    val sizingClass = requireExactlyOne(
        "NewX captured inline-action sizing model in ${owner.type}",
        owner.fields.filter { field -> !AccessFlags.STATIC.isSet(field.accessFlags) }
            .mapNotNull { field -> context.classDefByOrNull(field.type) }
            .distinctBy { definition -> definition.type }
            .filter { definition -> definition.isInlineActionSizingModel() },
    )
    val sizingCaptureField = requireExactlyOne(
        "NewX captured inline-action sizing field in ${owner.type}",
        owner.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) && field.type == sizingClass.type
        },
    )
    val iconSizeRead = if (!resolveIconSize) null else {
        val sizingCaptureRead = requireExactlyOne(
            "NewX captured inline-action sizing read in $method",
            code.mapIndexedNotNull { index, instruction ->
                if (instruction.opcode != Opcode.IGET_OBJECT ||
                    instruction.getReference<FieldReference>()?.toString() != sizingCaptureField.toString()
                ) return@mapIndexedNotNull null
                val read = instruction as? TwoRegisterInstruction ?: return@mapIndexedNotNull null
                index.takeIf { method.provesParameterValue(index, read.registerB, method.p0Register) }
                    ?.let { it to read.registerA }
            },
        )
        val iconSizeFlow = requireExactlyOne(
            "NewX captured inline-action icon-size flow from sizing state to the renderer in $method",
            sizingClass.fields.filter { field ->
                !AccessFlags.STATIC.isSet(field.accessFlags) && field.type == "F"
            }.flatMap { sizeField ->
                code.mapIndexedNotNull { index, instruction ->
                    if (instruction.opcode != Opcode.IGET ||
                        instruction.getReference<FieldReference>()?.toString() != sizeField.toString()
                    ) return@mapIndexedNotNull null
                    val read = instruction as? TwoRegisterInstruction ?: return@mapIndexedNotNull null
                    if (!method.provesValueFromInstruction(
                            sizingCaptureRead.first,
                            sizingCaptureRead.second,
                            index,
                            read.registerB,
                        )
                    ) return@mapIndexedNotNull null
                    tintCall.second.parameterTypes.map(CharSequence::toString).indices
                        .filter { parameterIndex -> tintCall.second.parameterTypes[parameterIndex].toString() == "F" }
                        .mapNotNull { parameterIndex ->
                            val argument = code[tintCall.first].argumentRegister(tintCall.second, parameterIndex)
                                ?: return@mapNotNull null
                            Triple(index, read.registerA, parameterIndex).takeIf {
                                method.provesValueFromInstruction(index, read.registerA, tintCall.first, argument)
                            }
                        }
                }.flatten()
            },
        )
        (iconSizeFlow.first to iconSizeFlow.second).also { resolvedSizeRead ->
            if (!method.provesValueFromInstruction(captureRead.first, entryRegister, resolvedSizeRead.first, entryRegister)) {
                throw PatchException("NewX captured inline-action entry does not reach the size hook in $method")
            }
        }
    }

    val constructorCapture = resolveCapturedConstructor(owner, sizingCaptureField, models.inlineActionEntryDescriptor)
    val tintRead = resolveCapturedWideFieldRead(method, owner, constructorCapture.tintField)
    val tintArgument = requireExactlyOne(
        "NewX captured inline-action tint argument in $method",
        tintCall.second.parameterTypes.map(CharSequence::toString).indices
            .filter { index -> tintCall.second.parameterTypes[index].toString() == "J" }
            .mapNotNull { parameterIndex ->
                val register = code[tintCall.first].argumentRegister(tintCall.second, parameterIndex)
                    ?: return@mapNotNull null
                (parameterIndex to register).takeIf {
                    method.provesValueFromInstruction(tintRead.first, tintRead.second, tintCall.first, register)
                }
            },
    )
    val contentTint = constructorCapture.contentTintField?.let { contentField ->
        val wideArguments = tintCall.second.parameterTypes.map(CharSequence::toString).indices
            .filter { index -> tintCall.second.parameterTypes[index].toString() == "J" }
        if (wideArguments.size < 3) return@let null
        val contentRead = resolveCapturedWideFieldRead(method, owner, contentField)
        val thirdWideParameter = wideArguments[2]
        val thirdWideRegister = code[tintCall.first].argumentRegister(tintCall.second, thirdWideParameter)
            ?: throw PatchException("NewX captured inline-action count-tint argument is missing: $method")
        if (!method.provesValueFromInstruction(contentRead.first, contentRead.second, tintCall.first, thirdWideRegister)) {
            throw PatchException("NewX captured inline-action count tint does not reach the renderer: $method")
        }
        contentRead
    }

    if (tintArgument.first >= tintCall.second.parameterTypes.size) {
        throw PatchException("NewX captured inline-action renderer argument resolution is invalid: $method")
    }
    return ResolvedCapturedInlineActionRenderer(
        method = method,
        entryRegister = entryRegister,
        iconSizeReadIndex = iconSizeRead?.first,
        iconSizeRegister = iconSizeRead?.second,
        tintReadIndex = tintRead.first,
        tintRegister = tintRead.second,
        contentTintReadIndex = contentTint?.first,
        contentTintRegister = contentTint?.second,
        tintRenderer = tintCall.second,
    )
}

internal data class CapturedRendererCandidate(
    val method: Method,
    val owner: ClassDef,
)

context(context: BytecodePatchContext)
internal fun capturedInlineActionRendererCandidates(
    models: ResolvedNewXInlineActionModels,
): List<CapturedRendererCandidate> =
    classDefFlatMap { owner ->
        owner.methods.filter { method -> method.isCapturedInlineActionEntryRenderer(owner, models) }
            .map { method -> CapturedRendererCandidate(method, owner) }
    }

private data class CapturedConstructorFields(
    val tintField: FieldReference,
    val contentTintField: FieldReference?,
)

context(context: BytecodePatchContext)
private fun resolveCapturedConstructor(
    owner: ClassDef,
    sizingCaptureField: FieldReference,
    entryDescriptor: String,
): CapturedConstructorFields {
    val entryFields = owner.fields.filter { field ->
        !AccessFlags.STATIC.isSet(field.accessFlags) && field.type == entryDescriptor
    }
    val entryField = requireExactlyOne("NewX captured inline-action entry field in ${owner.type}", entryFields)
    val sizingClass = context.classDefByOrNull(sizingCaptureField.type)
        ?: throw PatchException("NewX captured inline-action sizing class was not found: ${sizingCaptureField.type}")
    val constructors = owner.methods.filter { constructor ->
        if (constructor.name != "<init>" || constructor.returnType != "V") return@filter false
        val parameters = constructor.parameterTypes.map(CharSequence::toString)
        val entryParameter = parameters.indexOf(entryDescriptor)
        val sizingParameter = parameters.indexOf(sizingClass.type)
        val wideParameters = parameters.indices.filter { index -> parameters[index] == "J" }
        entryParameter >= 0 && sizingParameter >= 0 && wideParameters.isNotEmpty() &&
            constructorParameterField(constructor, entryParameter, entryField) != null &&
            constructorParameterField(constructor, sizingParameter, sizingCaptureField) != null
    }
    val constructor = requireExactlyOne("NewX captured inline-action constructor in ${owner.type}", constructors)
    val wideParameters = constructor.parameterTypes.map(CharSequence::toString).indices
        .filter { index -> constructor.parameterTypes[index].toString() == "J" }
    val wideFieldsByCaptureOrder = wideParameters.mapIndexed { captureOrder, parameterIndex ->
        val fields = owner.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) && field.type == "J" &&
                constructorParameterField(constructor, parameterIndex, field) != null
        }
        captureOrder to requireExactlyOne(
            "NewX captured inline-action wide field for parameter $parameterIndex in ${owner.type}",
            fields,
        )
    }
    return CapturedConstructorFields(
        tintField = requireExactlyOne(
            "NewX captured inline-action tint field in ${owner.type}",
            wideFieldsByCaptureOrder.filter { (captureOrder, _) -> captureOrder == 0 }
                .map { (_, field) -> field },
        ),
        contentTintField = requireAtMostOne(
            "NewX captured inline-action count-tint field in ${owner.type}",
            wideFieldsByCaptureOrder.filter { (captureOrder, _) -> captureOrder == 1 }
                .map { (_, field) -> field },
        ),
    )
}

private fun constructorParameterField(
    constructor: Method,
    parameterIndex: Int,
    field: FieldReference,
): FieldReference? {
    val parameterRegister = constructor.parameterRegister(parameterIndex)
    val opcode = if (field.type == "J" || field.type == "D") Opcode.IPUT_WIDE else Opcode.IPUT_OBJECT
    val matchingWrites = constructor.implementation?.instructions?.withIndex()?.filter { indexed ->
        val instruction = indexed.value
        if (instruction.opcode != opcode ||
            instruction.getReference<FieldReference>()?.toString() != field.toString()
        ) return@filter false
        val registers = instruction as? TwoRegisterInstruction ?: return@filter false
        constructor.provesParameterValue(indexed.index, registers.registerA, parameterRegister) &&
            constructor.provesParameterValue(indexed.index, registers.registerB, constructor.p0Register)
    }.orEmpty()
    return field.takeIf { matchingWrites.size == 1 }
}

private fun resolveCapturedWideFieldRead(
    method: MutableMethod,
    owner: ClassDef,
    field: FieldReference,
): Pair<Int, Int> {
    val reads = method.instructions.withIndex().mapNotNull { indexed ->
        if (indexed.value.opcode != Opcode.IGET_WIDE ||
            indexed.value.getReference<FieldReference>()?.toString() != field.toString()
        ) return@mapNotNull null
        val read = indexed.value as? TwoRegisterInstruction ?: return@mapNotNull null
        indexed.index.takeIf { method.provesParameterValue(indexed.index, read.registerB, method.p0Register) }
            ?.let { it to read.registerA }
    }
    if (field.definingClass != owner.type) throw PatchException("NewX captured inline-action field owner changed: $field")
    return requireExactlyOne("NewX captured inline-action wide read $field in $method", reads)
}

private fun ClassDef.isInlineActionSizingModel(): Boolean =
    methods.any { method ->
        method.name == "toString" && method.parameterTypes.isEmpty() &&
            method.implementation?.instructions?.any { instruction ->
                instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.StringReference>()?.string ==
                    SIZING_TO_STRING_PREFIX
            } == true && method.implementation?.instructions?.any { instruction ->
                instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.StringReference>()?.string ==
                    SIZING_ICON_SIZE_LABEL
            } == true
    }

private fun Method.parameterRegister(parameterIndex: Int): Int {
    if (parameterIndex !in parameterTypes.indices) {
        throw PatchException("NewX captured inline-action parameter index is out of range: $parameterIndex in $this")
    }
    val firstParameterRegister = p0Register + if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    return firstParameterRegister + parameterTypes.take(parameterIndex).sumOf { parameter ->
        if (parameter.toString() == "J" || parameter.toString() == "D") 2 else 1
    }
}

private fun Method.provesParameterValue(
    targetIndex: Int,
    targetRegister: Int,
    parameterRegister: Int,
): Boolean = valueProvenanceAt(targetIndex, targetRegister, parameterRegister, null)

internal fun Method.provesValueFromInstruction(
    sourceIndex: Int,
    sourceRegister: Int,
    targetIndex: Int,
    targetRegister: Int,
): Boolean = valueProvenanceAt(targetIndex, targetRegister, sourceRegister, sourceIndex)

/** A branch-local load need not dominate a shared renderer's other color-selection branches. */
internal fun Method.provesValueFromInstructionOnSourcePaths(
    sourceIndex: Int,
    sourceRegister: Int,
    targetIndex: Int,
    targetRegister: Int,
): Boolean = valueProvenanceAt(targetIndex, targetRegister, sourceRegister, sourceIndex, sourcePathsOnly = true)

private fun Method.valueProvenanceAt(
    targetIndex: Int,
    targetRegister: Int,
    originRegister: Int,
    originInstruction: Int?,
    sourcePathsOnly: Boolean = false,
): Boolean {
    val implementation = implementation ?: return false
    val code = implementation.instructions.toList()
    if (targetIndex !in code.indices || originRegister !in 0 until implementation.registerCount ||
        originInstruction != null && originInstruction !in code.indices || implementation.tryBlocks.isNotEmpty()
    ) return false
    val edges = code.controlFlowEdges() ?: return false
    val input = arrayOfNulls<IntArray>(code.size)
    val initial = IntArray(implementation.registerCount) { UNKNOWN_VALUE }
    if (originInstruction == null) initial[originRegister] = PARAMETER_VALUE
    val startIndex = if (sourcePathsOnly) originInstruction ?: return false else 0
    input[startIndex] = initial
    val work = ArrayDeque<Int>()
    work.add(startIndex)
    while (work.isNotEmpty()) {
        val index = work.removeFirst()
        val inState = input[index] ?: continue
        val outState = inState.copyOf()
        val instruction = code[index]
        val destination = instruction.destinationRegisterOrNull()
        if (destination != null) {
            if (instruction.opcode == Opcode.CHECK_CAST) {
                // CHECK_CAST refines the verifier type without changing the object identity.
            } else {
                val movedFrom = instruction.moveSourceRegister()
                val movedValue = movedFrom?.let { inState.getOrElse(it) { UNKNOWN_VALUE } } ?: UNKNOWN_VALUE
                outState[destination] = movedValue
                if (instruction.opcode.setsWideRegister() && destination + 1 < outState.size) {
                    outState[destination + 1] = UNKNOWN_VALUE
                }
            }
        }
        if (originInstruction == index && destination != null) {
            outState[destination] = index
            if (instruction.opcode.setsWideRegister() && destination + 1 < outState.size) {
                outState[destination + 1] = UNKNOWN_VALUE
            }
        }
        edges[index].forEach { edge ->
            val next = if (edge.exceptional) inState else outState
            val prior = input[edge.target]
            if (prior == null) {
                input[edge.target] = next.copyOf()
                work.add(edge.target)
                return@forEach
            }
            var changed = false
            for (register in prior.indices) {
                if (prior[register] == next[register]) continue
                if (prior[register] != UNKNOWN_VALUE) {
                    prior[register] = UNKNOWN_VALUE
                    changed = true
                }
            }
            if (changed) work.add(edge.target)
        }
    }
    val state = input[targetIndex] ?: return false
    val expected = originInstruction ?: PARAMETER_VALUE
    return state.getOrElse(targetRegister) { UNKNOWN_VALUE } == expected
}

private fun Instruction.destinationRegisterOrNull(): Int? {
    if (!opcode.setsRegister()) return null
    return when (this) {
        is ThreeRegisterInstruction -> registerA
        is TwoRegisterInstruction -> registerA
        is OneRegisterInstruction -> registerA
        else -> null
    }
}

private fun Instruction.moveSourceRegister(): Int? {
    if (opcode !in MOVE_OPCODES) return null
    return (this as? TwoRegisterInstruction)?.registerB
}

private val MOVE_OPCODES = setOf(
    Opcode.MOVE,
    Opcode.MOVE_FROM16,
    Opcode.MOVE_16,
    Opcode.MOVE_OBJECT,
    Opcode.MOVE_OBJECT_FROM16,
    Opcode.MOVE_OBJECT_16,
    Opcode.MOVE_WIDE,
    Opcode.MOVE_WIDE_FROM16,
    Opcode.MOVE_WIDE_16,
)

private data class FlowEdge(val target: Int, val exceptional: Boolean)

private fun List<Instruction>.controlFlowEdges(): List<List<FlowEdge>>? {
    val offsets = runningFold(0) { address, instruction -> address + instruction.codeUnits }
    val indexByOffset = offsets.dropLast(1).withIndex().associate { (index, offset) -> offset to index }
    return mapIndexed { index, instruction ->
        val opcode = instruction.opcode
        val offset = offsets[index]
        fun target(offset: Int): Int? = indexByOffset[offset]
        val branch = instruction as? OffsetInstruction
        val branchTarget = branch?.let { target(offset + it.codeOffset) }
        when {
            opcode in setOf(Opcode.RETURN, Opcode.RETURN_OBJECT, Opcode.RETURN_VOID, Opcode.RETURN_WIDE, Opcode.THROW) ->
                emptyList()
            opcode in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32) ->
                listOfNotNull(branchTarget?.let { FlowEdge(it, false) }).takeIf { it.size == 1 } ?: return null
            opcode == Opcode.PACKED_SWITCH || opcode == Opcode.SPARSE_SWITCH -> {
                val payloadIndex = branchTarget ?: return null
                val payload = getOrNull(payloadIndex) as? SwitchPayload ?: return null
                val cases = payload.switchElements.map { element ->
                    target(offset + element.offset)?.let { FlowEdge(it, false) } ?: return null
                }
                (cases + listOfNotNull((index + 1).takeIf { it < size }?.let { FlowEdge(it, false) })).distinct()
            }
            opcode.format in setOf(com.android.tools.smali.dexlib2.Format.Format21t,
                com.android.tools.smali.dexlib2.Format.Format22t) -> {
                val destination = branchTarget ?: return null
                if (index + 1 >= size) return null
                listOf(FlowEdge(destination, false), FlowEdge(index + 1, false)).distinct()
            }
            instruction is SwitchPayload -> emptyList()
            else -> (index + 1).takeIf { it < size }?.let { listOf(FlowEdge(it, false)) }.orEmpty()
        }
    }
}

internal fun MutableMethod.hasOnlyFallThroughInto(index: Int): Boolean {
    val implementation = implementation ?: return false
    if (implementation.tryBlocks.isNotEmpty() || index <= 0 || index >= instructions.size) return false
    val edges = instructions.toList().controlFlowEdges() ?: return false
    val predecessors = edges.flatMapIndexed { source, successors ->
        successors.filter { edge -> !edge.exceptional && edge.target == index }.map { source }
    }
    return predecessors == listOf(index - 1)
}

private fun Instruction.argumentRegister(reference: MethodReference, parameterIndex: Int): Int? {
    if (parameterIndex !in reference.parameterTypes.indices) return null
    val registers = registersUsed
    var word = if (opcode == Opcode.INVOKE_STATIC || opcode == Opcode.INVOKE_STATIC_RANGE) 0 else 1
    reference.parameterTypes.take(parameterIndex).forEach { parameter ->
        word += if (parameter.toString() == "J" || parameter.toString() == "D") 2 else 1
    }
    return registers.getOrNull(word)
}
