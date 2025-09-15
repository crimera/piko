package app.crimera.utils

import app.crimera.patches.twitter.misc.settings.FSTS_DESCRIPTOR
import app.crimera.patches.twitter.misc.settings.SSTS_DESCRIPTOR
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedMethodReference
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.Reference

fun MutableMethod.enableSettings(functionName: String) {
    addInstruction(
        0,
        "$SSTS_DESCRIPTOR->$functionName()V",
    )
}

fun MutableMethod.flagSettings(functionName: String) {
    addInstruction(
        0,
        "$FSTS_DESCRIPTOR->$functionName()V",
    )
}

fun Reference.extractDescriptors(): List<String> {
    val regex = Regex("L[^;]+;")
    return regex.findAll(this.toString()).map { it.value }.toList()
}

fun MutableMethod.getReference(index: Int): Reference = getInstruction<ReferenceInstruction>(index).reference

fun MutableMethod.getMethodName(index: Int): String = getInstruction<DexBackedMethodReference>(index).name

fun MutableMethod.getFieldName(index: Int): String = getInstruction<FieldReference>(index).name

fun MutableMethod.changeStringAt(
    index: Int,
    value: String,
) {
    instructions.filter { it.opcode == Opcode.CONST_STRING }[index].let { instruction ->
        val register = (instruction as BuilderInstruction21c).registerA
        replaceInstruction(instruction.location.index, "const-string v$register, \"$value\"")
    }
}

fun MutableMethod.changeFirstString(value: String) {
    changeStringAt(0, value)
}
