package crimera.patches.twitter.timeline.hideNudgeButtons

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
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction23x
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object HideNudgeButtonPatchFingerprint : MethodFingerprint(
    strings = listOf("viewDelegate", "viewModel"),
    customFingerprint = {_, classDef ->
        classDef.type.endsWith("FollowNudgeButtonViewDelegateBinder;")
    }
)

@Patch(
    name = "Hide nudge button",
    description = "Hides follow/subscribe/follow back buttons on posts",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    dependencies = [SettingsPatch::class],
    use = true,
)
@Suppress("unused")
object HideNudgeButtonPatch:BytecodePatch(
    setOf(HideNudgeButtonPatchFingerprint,SettingsStatusLoadFingerprint)
) {
    const val HOOK_DESCRIPTOR =
        "invoke-static {}, ${SettingsPatch.PREF_DESCRIPTOR};->hideNudgeButton()Z"

    override fun execute(context: BytecodeContext) {
       val result = HideNudgeButtonPatchFingerprint.result
           ?: throw PatchException("HideNudgeButtonPatchFingerprint not found")

       val method = result.mutableMethod
       val instructions = method.getInstructions()

       val dummyRegIndex = instructions.first { it.opcode == Opcode.APUT_OBJECT}.location.index
       val dummyReg = method.getInstruction<Instruction23x>(dummyRegIndex).registerC

       method.addInstructionsWithLabels(dummyRegIndex+2,"""
           $HOOK_DESCRIPTOR
           move-result v$dummyReg
           if-eqz v$dummyReg, :piko
           const/16 v$dummyReg, 0x8
           invoke-virtual {p1, v$dummyReg}, Landroidx/appcompat/widget/AppCompatButton;->setVisibility(I)V
       """.trimIndent(), ExternalLabel("piko",instructions.first { it.opcode == Opcode.INVOKE_STATIC && it.location.index > dummyRegIndex })
       )

      SettingsStatusLoadFingerprint.enableSettings("hideNudgeButton")

    }
}