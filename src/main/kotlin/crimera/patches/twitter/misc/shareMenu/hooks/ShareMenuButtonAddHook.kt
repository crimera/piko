package crimera.patches.twitter.misc.shareMenu.hooks

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedFieldReference
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
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
    fun registerButton(name: String) {
        val shareMenuButtonAdd = ShareMenuButtonAddHook.result?.mutableMethod ?: throw ShareMenuButtonAddHook.exception

        val sgetObj = shareMenuButtonAdd.getInstructions().last { it.opcode == Opcode.SGET_OBJECT } as Instruction21c
        val addToCollection =
            shareMenuButtonAdd.getInstructions().last { it.opcode == Opcode.INVOKE_VIRTUAL } as Instruction35c

        shareMenuButtonAdd.addInstructions(
            0, """
                sget-object v0, ${sgetObj.reference.toString().replace("ViewDebugDialog", name)}
                invoke-virtual {p2, v0}, ${addToCollection.reference}
            """.trimIndent()
        )
    }

    fun addButton(
        buttonReference: String?,
        functionName: String,
    ) {
        val result = result ?: throw PatchException("ShareMenuButtonAddHook not found")

        val method = result.mutableMethod
        val instructions = method.getInstructions()

        val addMethodIndex = instructions.last { it.opcode == Opcode.INVOKE_VIRTUAL }.location.index

        val addMethod = method.getInstruction<ReferenceInstruction>(addMethodIndex).reference.toString()

        val buttonClass =
            (
                    (
                            method
                                .getInstruction<BuilderInstruction21c>(
                                    addMethodIndex - 1,
                                ).reference
                            ) as DexBackedFieldReference
                    ).definingClass

        method.addInstructionsWithLabels(
            0,
            """
            invoke-static{}, ${SettingsPatch.PREF_DESCRIPTOR};->$functionName()Z
            move-result v0
            if-eqz v0, :next
            sget-object v0, $buttonClass->$buttonReference:$buttonClass
            invoke-virtual {p3,v0}, $addMethod 
            """.trimIndent(),
            ExternalLabel("next", instructions.first { it.opcode == Opcode.INVOKE_STATIC }),
        )
    }
}
