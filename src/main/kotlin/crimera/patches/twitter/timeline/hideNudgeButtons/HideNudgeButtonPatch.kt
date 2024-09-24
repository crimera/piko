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
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object HideNudgeButtonPatchFingerprint : MethodFingerprint(
    strings = listOf("creator_subscriptions_subscribe_button_tweet_detail_enabled"),
    customFingerprint = {methodDef, _ ->
        methodDef.parameters.size == 1 && methodDef.name == "invoke"
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

       val injectLocation = instructions.first { it.opcode == Opcode.IF_NEZ}.location.index
       val dummyReg = method.getInstruction<OneRegisterInstruction>(injectLocation-1).registerA

       method.addInstructionsWithLabels(injectLocation-1,"""
           $HOOK_DESCRIPTOR
           move-result v$dummyReg
           if-nez v$dummyReg, :piko
       """.trimIndent(), ExternalLabel("piko",instructions.last { it.opcode == Opcode.SGET_OBJECT })
       )

      SettingsStatusLoadFingerprint.enableSettings("hideNudgeButton")

    }
}