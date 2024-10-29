package crimera.patches.twitter.misc.shareMenu.hooks

import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.Reference
import crimera.patches.twitter.misc.settings.SettingsPatch

object ShareMenuButtonAddHook : MethodFingerprint(
    returnType = "V",
    strings =
        listOf(
            "3691233323:audiospace",
        ),
    customFingerprint = { methodDef, _ ->
        methodDef.name == "a"
    },
) {
    fun addButton(
        buttonReference: Reference?,
        functionName: String,
    ) {
        val result = result ?: throw PatchException("ShareMenuButtonAddHook not found")

        val method = result.mutableMethod
        val instructions = method.getInstructions()

        val addMethodIndex = instructions.last { it.opcode == Opcode.INVOKE_VIRTUAL }.location.index

        val addMethod = method.getInstruction<ReferenceInstruction>(addMethodIndex).reference.toString()

        method.addInstructionsWithLabels(
            0,
            """
            invoke-static{}, ${SettingsPatch.PREF_DESCRIPTOR};->$functionName()Z
            move-result v0
            if-eqz v0, :next
            sget-object v0, $buttonReference
            invoke-virtual {p3,v0}, $addMethod 
            """.trimIndent(),
            ExternalLabel("next", instructions.first { it.opcode == Opcode.INVOKE_STATIC }),
        )
    }
}
