package crimera.patches.twitter.misc.shareMenu.hooks

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.patches.shared.misc.mapping.ResourceMappingPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.Reference
import crimera.patches.twitter.misc.settings.SettingsPatch

object ShareMenuButtonInitHook : MethodFingerprint(
    strings =
        listOf(
            "Debug",
            "View in Tweet Sandbox",
            "View in Spaces Sandbox",
        ),
) {
    fun setButtonText(
        matchString: String,
        stringId: String,
        offset: Int = 0,
    ) {
        val result = result ?: throw PatchException("ShareMenuButtonInitHook not found")
        val method = result.mutableMethod

        result.scanResult.stringsScanResult!!.matches.forEach { match ->
            val matchStr = match.string
            if (matchStr == matchString) {
                val loc = match.index + offset
                val r = method.getInstruction<OneRegisterInstruction>(loc).registerA
                method.addInstructions(
                    loc + 1,
                    """
                    const-string v$r, "$stringId"
                    invoke-static {v$r},${SettingsPatch.UTILS_DESCRIPTOR};->strRes(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$r
                    """.trimIndent(),
                )
                return@forEach
            }
        }
    }

    fun setButtonIcon(
        buttonReference: Reference,
        iconStr: String,
        offset: Int = 0,
    ) {
        val result = result ?: throw PatchException("ShareMenuButtonInitHook not found")
        val allMethods = result.mutableClass.methods
        val method = allMethods.first { it.returnType == "V" }
        val instructions = method.getInstructions()
        instructions.filter { it.opcode == Opcode.SGET_OBJECT }.forEach { instruction ->
            val ref = (instruction as ReferenceInstruction).reference.toString()
            if (ref == buttonReference.toString()) {
                var index = instruction.location.index + offset
                index =
                    method
                        .getInstructions()
                        .first { it.opcode == Opcode.INVOKE_VIRTUAL && it.location.index > index }
                        .location.index
                val r = method.getInstruction<BuilderInstruction35c>(index).registerE
                val iconId = ResourceMappingPatch["drawable", iconStr]
                method.addInstructions(
                    index,
                    """
                    const v$r, $iconId
                    invoke-static {v$r}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
                    move-result-object v$r
                    """.trimIndent(),
                )
            }
        }
    }
}
