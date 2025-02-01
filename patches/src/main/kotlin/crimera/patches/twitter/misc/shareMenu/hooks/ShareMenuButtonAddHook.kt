package crimera.patches.twitter.misc.shareMenu.hooks

import app.revanced.patcher.Match
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.Reference
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR

val ShareMenuButtonAddHook =
    fingerprint {
        strings("3691233323:audiospace")
        returns("V")
        custom { methodDef, _ ->
            methodDef.name == "a" && methodDef.parameters.size == 4
        }
    }

fun Match.addButton(
    buttonReference: Reference?,
    functionName: String,
) {
    val method = this.mutableMethod
    val instructions = method.instructions

    val addMethodIndex = instructions.last { it.opcode == Opcode.INVOKE_VIRTUAL }.location.index

    val addMethod = method.getInstruction<ReferenceInstruction>(addMethodIndex).reference.toString()

    method.addInstructionsWithLabels(
        0,
        """
        invoke-static{}, ${PREF_DESCRIPTOR};->$functionName()Z
        move-result v0
        if-eqz v0, :next
        sget-object v0, $buttonReference
        invoke-virtual {p3,v0}, $addMethod 
        """.trimIndent(),
        ExternalLabel("next", instructions.first { it.opcode == Opcode.INVOKE_STATIC }),
    )
}
