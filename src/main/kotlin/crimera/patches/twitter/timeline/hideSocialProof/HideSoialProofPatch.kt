package crimera.patches.twitter.timeline.hideSocialProof

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction23x
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object HideSocialProofPatchFingerprint : MethodFingerprint(

    customFingerprint = {methodDef, classDef ->
        classDef.type.endsWith("SocialProofView;") && methodDef.name == "setSocialProofData"
    }
)

@Patch(
    name = "Hide followed by context",
    description = "Hides followed by context under profile",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    dependencies = [SettingsPatch::class],
    use = true,
)
@Suppress("unused")
object HideSocialProofPatch:BytecodePatch(
    setOf(HideSocialProofPatchFingerprint,SettingsStatusLoadFingerprint)
) {
    const val HOOK_DESCRIPTOR =
        "invoke-static {}, ${SettingsPatch.PREF_DESCRIPTOR};->hideSocialProof()Z"

    override fun execute(context: BytecodeContext) {
       val result = HideSocialProofPatchFingerprint.result
           ?: throw PatchException("HideSocialProofPatchFingerprint not found")

       val method = result.mutableMethod
       val instructions = method.getInstructions()



       method.addInstructionsWithLabels(0,"""
           $HOOK_DESCRIPTOR
           move-result v0
           if-eqz v0, :piko
           return-void
       """.trimIndent(), ExternalLabel("piko",instructions.first { it.opcode == Opcode.CONST_4})
       )

      SettingsStatusLoadFingerprint.enableSettings("hideSocialProof")

    }
}