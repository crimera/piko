package crimera.patches.twitter.misc.shareMenu.hooks

import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.shareMenu.nativeDownloader.exception

object ShareMenuButtonAddHook : MethodFingerprint(
    returnType = "V",
    customFingerprint = { methodDef, _ ->
        methodDef.name == "a" && methodDef.parameters.map { it.type }
            .containsAll(listOf("I", "Lcom/twitter/model/timeline/p1;", "Lcom/twitter/util/collection/d1\$a;"))
    },
) {
    fun registerButton(name: String, functionName: String) {
        val shareMenuButtonAdd = ShareMenuButtonAddHook.result?.mutableMethod ?: throw ShareMenuButtonAddHook.exception

        val sgetObj = shareMenuButtonAdd.getInstructions().last { it.opcode == Opcode.SGET_OBJECT } as Instruction21c
        val addToCollection =
            shareMenuButtonAdd.getInstructions().last { it.opcode == Opcode.INVOKE_VIRTUAL } as Instruction35c

        shareMenuButtonAdd.addInstructionsWithLabels(
            0, """
                invoke-static{}, ${SettingsPatch.PREF_DESCRIPTOR};->$functionName()Z
                move-result v0
                if-eqz v0, :next
                sget-object v0, ${sgetObj.reference.toString().replace("ViewDebugDialog", name)}
                invoke-virtual {p2, v0}, ${addToCollection.reference}
            """.trimIndent(),
            ExternalLabel("next", shareMenuButtonAdd.getInstructions().first { it.opcode == Opcode.INVOKE_STATIC })
        )
    }
}
