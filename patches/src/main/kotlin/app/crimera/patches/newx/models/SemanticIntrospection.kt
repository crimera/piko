package app.crimera.patches.newx.models

import app.crimera.bytecode.Block
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.valueReachesRegister
import app.crimera.patches.common.semantic.ModelFieldAccessor as SharedModelFieldAccessor
import app.crimera.patches.common.semantic.fieldForBooleanToStringLabel as sharedFieldForBooleanToStringLabel
import app.crimera.patches.common.semantic.fieldForToStringLabel as sharedFieldForToStringLabel
import app.crimera.patches.common.semantic.patchBooleanAccessorGetter as sharedPatchBooleanAccessorGetter
import app.crimera.patches.common.semantic.patchBooleanFieldGetter as sharedPatchBooleanFieldGetter
import app.crimera.patches.common.semantic.patchBooleanMethodGetter as sharedPatchBooleanMethodGetter
import app.crimera.patches.common.semantic.patchBridge as sharedPatchBridge
import app.crimera.patches.common.semantic.patchObjectAccessorGetter as sharedPatchObjectAccessorGetter
import app.crimera.patches.common.semantic.patchObjectFieldGetter as sharedPatchObjectFieldGetter
import app.crimera.patches.common.semantic.patchObjectMethodGetter as sharedPatchObjectMethodGetter
import app.crimera.patches.common.semantic.patchWideAccessorGetter as sharedPatchWideAccessorGetter
import app.crimera.patches.common.semantic.patchWideFieldGetter as sharedPatchWideFieldGetter
import app.crimera.patches.common.semantic.patchWideMethodGetter as sharedPatchWideMethodGetter
import app.crimera.patches.common.semantic.readModelAccessor as sharedReadModelAccessor
import app.crimera.patches.common.semantic.requireBridge as sharedRequireBridge
import app.crimera.patches.common.semantic.requireGetter as sharedRequireGetter
import app.crimera.patches.common.semantic.requirePublicFields as sharedRequirePublicFields
import app.crimera.patches.common.semantic.requireSingle as sharedRequireSingle
import app.crimera.patches.common.semantic.requireSingleInstanceField as sharedRequireSingleInstanceField
import app.crimera.patches.common.semantic.resolveCurrentMethod as sharedResolveCurrentMethod
import app.crimera.patches.common.semantic.resolveFieldAccessor as sharedResolveFieldAccessor
import app.crimera.patches.common.semantic.resolveMutableMethodOwner as sharedResolveMutableMethodOwner
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefByOrNull
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"

internal typealias ModelFieldAccessor = SharedModelFieldAccessor

internal fun Fingerprint.requireSingle(target: String): Match =
    this.sharedRequireSingle("NewX $target")

private inline fun <T> withNewXDiagnostic(block: () -> T): T {
    try {
        return block()
    } catch (exception: PatchException) {
        val detail = exception.message?.takeIf { it.isNotBlank() } ?: "semantic resolution failed"
        if (detail.startsWith("NewX ")) throw exception
        throw PatchException("NewX $detail", exception)
    }
}

internal fun Match.fieldForToStringLabel(label: String): FieldReference =
    withNewXDiagnostic { this.sharedFieldForToStringLabel(label) }

internal fun Method.fieldForToStringLabel(label: String): FieldReference =
    withNewXDiagnostic { this.sharedFieldForToStringLabel(label) }

internal fun Match.fieldForBooleanToStringLabel(label: String): FieldReference =
    withNewXDiagnostic { this.sharedFieldForBooleanToStringLabel(label) }

internal fun ClassDef.requireSingleInstanceField(
    type: String,
    semanticName: String,
): FieldReference =
    withNewXDiagnostic { this.sharedRequireSingleInstanceField(type, semanticName) }

internal fun MutableClass.requirePublicFields(fields: List<FieldReference>) =
    withNewXDiagnostic { this.sharedRequirePublicFields(fields) }

internal fun MutableClass.resolveFieldAccessor(
    field: FieldReference,
    semanticName: String,
): ModelFieldAccessor =
    withNewXDiagnostic { this.sharedResolveFieldAccessor(field, semanticName) }

internal fun MutableClass.requireGetter(
    field: FieldReference,
    semanticName: String,
): MethodReference =
    withNewXDiagnostic { this.sharedRequireGetter(field, semanticName) }

internal fun Block.readModelAccessor(
    accessor: ModelFieldAccessor,
    receiver: Int,
    destination: Int = receiver,
) = withNewXDiagnostic {
    this.sharedReadModelAccessor(accessor, receiver, destination)
}

internal fun MutableClass.patchObjectFieldGetter(
    name: String,
    ownerDescriptor: String,
    field: FieldReference,
    returnType: String = OBJECT_DESCRIPTOR,
) = withNewXDiagnostic {
    this.sharedPatchObjectFieldGetter(name, ownerDescriptor, field, returnType)
}

internal fun MutableClass.patchObjectMethodGetter(
    name: String,
    ownerDescriptor: String,
    getter: MethodReference,
    returnType: String = OBJECT_DESCRIPTOR,
) = withNewXDiagnostic {
    this.sharedPatchObjectMethodGetter(name, ownerDescriptor, getter, returnType)
}

internal fun MutableClass.patchObjectAccessorGetter(
    name: String,
    ownerDescriptor: String,
    accessor: ModelFieldAccessor,
    returnType: String = OBJECT_DESCRIPTOR,
) = withNewXDiagnostic {
    this.sharedPatchObjectAccessorGetter(name, ownerDescriptor, accessor, returnType)
}

internal fun MutableClass.patchBooleanFieldGetter(
    name: String,
    ownerDescriptor: String,
    field: FieldReference,
) = withNewXDiagnostic {
    this.sharedPatchBooleanFieldGetter(name, ownerDescriptor, field)
}

internal fun MutableClass.patchBooleanMethodGetter(
    name: String,
    ownerDescriptor: String,
    getter: MethodReference,
) = withNewXDiagnostic {
    this.sharedPatchBooleanMethodGetter(name, ownerDescriptor, getter)
}

internal fun MutableClass.patchBooleanAccessorGetter(
    name: String,
    ownerDescriptor: String,
    accessor: ModelFieldAccessor,
) = withNewXDiagnostic {
    this.sharedPatchBooleanAccessorGetter(name, ownerDescriptor, accessor)
}

internal fun MutableClass.patchWideFieldGetter(
    name: String,
    ownerDescriptor: String,
    field: FieldReference,
) = withNewXDiagnostic {
    this.sharedPatchWideFieldGetter(name, ownerDescriptor, field)
}

internal fun MutableClass.patchWideMethodGetter(
    name: String,
    ownerDescriptor: String,
    getter: MethodReference,
) = withNewXDiagnostic {
    this.sharedPatchWideMethodGetter(name, ownerDescriptor, getter)
}

internal fun MutableClass.patchWideAccessorGetter(
    name: String,
    ownerDescriptor: String,
    accessor: ModelFieldAccessor,
) = withNewXDiagnostic {
    this.sharedPatchWideAccessorGetter(name, ownerDescriptor, accessor)
}

internal fun MutableClass.requireBridge(
    name: String,
    parameters: String,
    returnType: String,
): MutableMethod =
    withNewXDiagnostic { this.sharedRequireBridge(name, parameters, returnType) }

internal fun MutableMethod.patchBridge(block: Block.() -> Unit) =
    withNewXDiagnostic { this.sharedPatchBridge(block) }

internal fun MethodReference.resolveCurrentMethod(label: String): Method =
    withNewXDiagnostic { this.sharedResolveCurrentMethod(label) }

internal fun MethodReference.resolveMutableMethodOwner(label: String): Pair<MutableClass, MutableMethod> =
    withNewXDiagnostic { this.sharedResolveMutableMethodOwner(label) }

private const val STRING_BUILDER_DESCRIPTOR = "Ljava/lang/StringBuilder;"

private val OUTLINED_FIELD_READ_OPCODES =
    setOf(
        Opcode.IGET,
        Opcode.IGET_WIDE,
        Opcode.IGET_OBJECT,
        Opcode.IGET_BOOLEAN,
        Opcode.IGET_BYTE,
        Opcode.IGET_CHAR,
        Opcode.IGET_SHORT,
    )

private val OUTLINED_STATIC_INVOKE_OPCODES = setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)
private val OUTLINED_DIRECT_INVOKE_OPCODES = setOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE)
private val OUTLINED_VIRTUAL_INVOKE_OPCODES = setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE)

/**
 * Resolves the model field printed after [label] in this `toString` method, tracing through
 * R8-outlined static `StringBuilder` helpers (including labels passed as the helper's trailing
 * argument before a subsequent `StringBuilder.append`) before falling back to linear label order.
 */
internal fun Method.fieldForOutlinedToStringLabel(label: String): FieldReference =
    outlinedToStringField(label) ?: fieldForToStringLabel(label)

private fun Method.outlinedToStringField(label: String): FieldReference? {
    val methodInstructions = implementation?.instructions?.toList() ?: return null
    fun definedAt(register: Int, callIndex: Int, matches: (Int) -> Boolean): List<Int> =
        methodInstructions.indices.filter { index ->
            index < callIndex && matches(index) &&
                methodInstructions.valueReachesRegister(
                    index,
                    (methodInstructions[index] as OneRegisterInstruction).registerA,
                    callIndex,
                    register,
                )
        }

    val candidates =
        methodInstructions.withIndex().flatMap { (callIndex, call) ->
            if (call.opcode !in OUTLINED_STATIC_INVOKE_OPCODES) return@flatMap emptyList()
            val helper = call.getReference<MethodReference>() ?: return@flatMap emptyList()
            val helperMethod =
                classDefByOrNull(helper.definingClass)?.let { owner ->
                    requireAtMostOne(
                        "NewX outlined toString helper $helper",
                        owner.methods.filter { candidate ->
                            candidate.definingClass == helper.definingClass &&
                                candidate.name == helper.name &&
                                candidate.returnType == helper.returnType &&
                                candidate.parameterTypes.map(CharSequence::toString) ==
                                    helper.parameterTypes.map(CharSequence::toString)
                        },
                    ) { it.toString() }
                } ?: return@flatMap emptyList()
            val appendOrder = helperMethod.stringBuilderParameterOrder() ?: return@flatMap emptyList()
            val arguments = call.registersUsed
            val labelSlots =
                arguments.indices.filter { slot ->
                    definedAt(arguments[slot], callIndex) { index ->
                        val insn = methodInstructions[index]
                        (insn.opcode == Opcode.CONST_STRING || insn.opcode == Opcode.CONST_STRING_JUMBO) &&
                            insn.getReference<StringReference>()?.string == label
                    }.isNotEmpty()
                }
            labelSlots.flatMap { labelSlot ->
                val labelPos = appendOrder.indexOf(labelSlot)
                if (labelPos < 0) return@flatMap emptyList()
                val fieldSlot = appendOrder.getOrNull(labelPos + 1)
                if (fieldSlot != null) {
                    val register = arguments.getOrNull(fieldSlot) ?: return@flatMap emptyList()
                    definedAt(register, callIndex) { index ->
                        methodInstructions[index].opcode in OUTLINED_FIELD_READ_OPCODES &&
                            methodInstructions[index].getReference<FieldReference>()?.definingClass == definingClass
                    }.mapNotNull { index -> methodInstructions[index].getReference<FieldReference>() }
                } else if (helper.returnType == STRING_BUILDER_DESCRIPTOR) {
                    val moveResult = methodInstructions.getOrNull(callIndex + 1)
                    if (moveResult?.opcode != Opcode.MOVE_RESULT_OBJECT) return@flatMap emptyList()
                    val sbRegister = (moveResult as OneRegisterInstruction).registerA
                    val nextAppendIndex =
                        ((callIndex + 2) until methodInstructions.size).firstOrNull { index ->
                            val insn = methodInstructions[index]
                            if (insn.opcode !in OUTLINED_VIRTUAL_INVOKE_OPCODES) return@firstOrNull false
                            val ref = insn.getReference<MethodReference>() ?: return@firstOrNull false
                            ref.definingClass == STRING_BUILDER_DESCRIPTOR &&
                                ref.name == "append" &&
                                insn.registersUsed.firstOrNull()?.let { receiver ->
                                    methodInstructions.valueReachesRegister(callIndex + 1, sbRegister, index, receiver)
                                } == true
                        } ?: return@flatMap emptyList()
                    val valueRegister =
                        methodInstructions[nextAppendIndex].registersUsed.getOrNull(1)
                            ?: return@flatMap emptyList()
                    definedAt(valueRegister, nextAppendIndex) { index ->
                        methodInstructions[index].opcode in OUTLINED_FIELD_READ_OPCODES &&
                            methodInstructions[index].getReference<FieldReference>()?.definingClass == definingClass
                    }.mapNotNull { index -> methodInstructions[index].getReference<FieldReference>() }
                } else {
                    emptyList()
                }
            }
        }.distinctBy { it.toString() }

    return requireAtMostOne("NewX outlined toString field for '$label' in $this", candidates) { it.toString() }
}

private fun Method.stringBuilderParameterOrder(): List<Int>? {
    if (!AccessFlags.STATIC.isSet(accessFlags)) return null
    val impl = implementation ?: return null
    val paramStart =
        impl.registerCount -
            parameterTypes.sumOf { type ->
                if (type.toString() == "J" || type.toString() == "D") 2 else 1
            }
    val paramCount = parameterTypes.size
    if (paramCount == 0) return null

    val methodInstructions = impl.instructions.toList()
    val order = mutableListOf<Int>()
    for ((index, instruction) in methodInstructions.withIndex()) {
        if (instruction.opcode !in OUTLINED_VIRTUAL_INVOKE_OPCODES &&
            instruction.opcode !in OUTLINED_DIRECT_INVOKE_OPCODES
        ) {
            continue
        }
        val ref = instruction.getReference<MethodReference>() ?: continue
        if (ref.definingClass != STRING_BUILDER_DESCRIPTOR) continue
        val isWrite = (ref.name == "<init>" && ref.parameterTypes.size == 1) || ref.name == "append"
        if (!isWrite) continue
        val argRegister = instruction.registersUsed.getOrNull(1) ?: return null
        val slot = argRegister - paramStart
        if (slot in 0 until paramCount) {
            order += slot
        } else {
            val matchingSlot =
                (0 until paramCount).firstOrNull { candidateSlot ->
                    methodInstructions.valueReachesRegister(0, paramStart + candidateSlot, index, argRegister)
                } ?: return null
            order += matchingSlot
        }
    }
    return order.takeIf { it.isNotEmpty() }
}
