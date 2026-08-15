package app.crimera.patches.xlite.models

import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"

context(_: BytecodePatchContext)
internal fun Fingerprint.requireSingle(target: String): Match {
    val matches = scopedMatchAll()
    if (matches.size == 1) return matches.single()
    throw PatchException(
        "Expected one X-Lite $target, found ${matches.size}: " +
            matches.joinToString { it.originalMethod.toString() },
    )
}
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"

internal fun Match.fieldForToStringLabel(label: String): FieldReference {
    val instructions = originalMethod.implementation?.instructions?.toList().orEmpty()
    val labelIndex = instructions.indexOfFirst { instruction ->
        instruction.getReference<StringReference>()?.string == label
    }
    if (labelIndex < 0) {
        throw PatchException("X-Lite model label '$label' was not found in $originalMethod")
    }

    val labelInstruction = instructions[labelIndex] as? OneRegisterInstruction
        ?: throw PatchException("X-Lite model label '$label' has an unsupported register layout in $originalMethod")
    val labelRegister = labelInstruction.registerA
    val labelConsumerIndex = instructions.withIndex()
        .drop(labelIndex + 1)
        .firstOrNull { (_, instruction) ->
            val reference = instruction.getReference<MethodReference>() ?: return@firstOrNull false
            reference.isStringBuilderLabelConsumer() && instruction.singleArgumentRegister() == labelRegister
        }?.index

    if (labelConsumerIndex != null) {
        val valueAppend = instructions.withIndex()
            .drop(labelConsumerIndex + 1)
            .firstOrNull { (_, instruction) ->
                instruction.getReference<MethodReference>()?.isStringBuilderValueAppend() == true
            }
        if (valueAppend != null) {
            val valueRegister = valueAppend.value.singleArgumentRegister()
            if (valueRegister != null) {
                val directField = instructions.findFieldForRegister(
                    register = valueRegister,
                    fromIndex = labelConsumerIndex + 1,
                    untilIndex = valueAppend.index,
                    definingClass = originalMethod.definingClass,
                )
                if (directField != null) return directField
            }
        }
    }

    val helperCandidates = buildList {
        instructions.withIndex().drop(labelIndex + 1).forEach { (helperIndex, instruction) ->
            if (instruction.getReference<MethodReference>() == null) return@forEach
            val argumentRegisters = instruction.registersUsed
            val labelArgumentIndex = argumentRegisters.indexOf(labelRegister)
            if (labelArgumentIndex < 0 || labelArgumentIndex + 1 >= argumentRegisters.size) {
                return@forEach
            }
            val valueArgumentRegister = argumentRegisters[labelArgumentIndex + 1]
            instructions.findFieldForRegister(
                register = valueArgumentRegister,
                fromIndex = labelIndex + 1,
                untilIndex = helperIndex,
                definingClass = originalMethod.definingClass,
            )?.let { add(it) }
        }
    }
    return requireSingleToStringField(label, originalMethod.toString(), helperCandidates)
}

internal fun requireSingleToStringField(
    label: String,
    owner: String,
    candidates: List<FieldReference>,
): FieldReference {
    if (candidates.isEmpty()) {
        throw PatchException("X-Lite model field for '$label' was not found in $owner")
    }
    val distinct = candidates.distinctBy(FieldReference::toString)
    if (distinct.size == 1) return distinct.single()
    // First-match would silently bind the patch to an arbitrary descriptor; distinct duplicates
    // are ambiguity and must fail loudly.
    throw PatchException(
        "Expected one X-Lite model field for '$label' in $owner, found " +
            "${distinct.size} distinct candidates: ${distinct.joinToString()}",
    )
}

internal fun Match.fieldForBooleanToStringLabel(label: String): FieldReference {
    val instructions = originalMethod.implementation?.instructions?.toList().orEmpty()
    val labelIndex = instructions.indexOfFirst { instruction ->
        instruction.getReference<StringReference>()?.string == label
    }
    if (labelIndex < 0) {
        throw PatchException("X-Lite boolean model label '$label' was not found in $originalMethod")
    }
    val fields = instructions.drop(labelIndex + 1).mapNotNull { instruction ->
        if (instruction.opcode != Opcode.IGET_BOOLEAN) return@mapNotNull null
        instruction.getReference<FieldReference>()?.takeIf { field ->
            field.definingClass == originalMethod.definingClass
        }
    }.distinctBy(FieldReference::toString)
    if (fields.size == 1) return fields.single()
    throw PatchException(
        "Expected one X-Lite boolean model field after '$label' in $originalMethod, found " +
            "${fields.size}: ${fields.joinToString()}",
    )
}

internal fun com.android.tools.smali.dexlib2.iface.ClassDef.requireSingleInstanceField(
    type: String,
    semanticName: String,
): FieldReference {
    val matches = fields.filter { field ->
        field.type == type && !AccessFlags.STATIC.isSet(field.accessFlags)
    }
    if (matches.size == 1) return matches.single()
    throw PatchException(
        "Expected one X-Lite $semanticName field of type $type in $this, found " +
            "${matches.size}: ${matches.joinToString()}",
    )
}

internal fun MutableClass.makeFieldsPublic(fields: List<FieldReference>) {
    fields.forEach { field ->
        val definition = this.fields.singleOrNull { candidate -> candidate.toString() == field.toString() }
            ?: throw PatchException("X-Lite model field definition was not found: $field in $this")
        val nonPublicFlags = AccessFlags.PRIVATE.value or AccessFlags.PROTECTED.value
        definition.accessFlags =
            (definition.accessFlags and nonPublicFlags.inv()) or AccessFlags.PUBLIC.value
    }
}

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
    if (matches.size != 1) {
        throw PatchException(
            "Expected one X-Lite bridge $name($parameters)$returnType in $this, found " +
                "${matches.size}: ${matches.joinToString()}",
        )
    }
    matches.single().addInstructions(0, instructions.trimIndent())
}

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

private fun List<Instruction>.findFieldForRegister(
    register: Int,
    fromIndex: Int,
    untilIndex: Int,
    definingClass: String,
): FieldReference? =
    subList(fromIndex, untilIndex)
        .asReversed()
        .firstNotNullOfOrNull { instruction ->
            val registerInstruction = instruction as? TwoRegisterInstruction
                ?: return@firstNotNullOfOrNull null
            if (registerInstruction.registerA != register) {
                return@firstNotNullOfOrNull null
            }
            instruction.getReference<FieldReference>()?.takeIf { field ->
                field.definingClass == definingClass
            }
        }

private fun Instruction.singleArgumentRegister(): Int? =
    when (this) {
        is FiveRegisterInstruction -> registerD
        is RegisterRangeInstruction -> startRegister + 1
        else -> null
    }
