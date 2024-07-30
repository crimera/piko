package crimera.patches.twitter.timeline.removePremiumUpsell

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object RemovePremiumUpsellPatchFingerprint: MethodFingerprint(
    strings = listOf("subscriptions_upsells_premium_home_nav")
)


@Patch(
    name = "Remove premium upsell",
    description = "Removes premium upsell in home timeline",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    dependencies = [SettingsPatch::class],
    use = false
)
@Suppress("unused")
object DisablePremiumUpsellPatch:BytecodePatch(
    setOf(SettingsStatusLoadFingerprint,RemovePremiumUpsellPatchFingerprint)
) {
    override fun execute(context: BytecodeContext) {

        val PREF = "${SettingsPatch.PREF_DESCRIPTOR};->removePremiumUpsell(Ljava/lang/String;)Ljava/lang/String;"

        val result = RemovePremiumUpsellPatchFingerprint.result
            ?: throw PatchException("RemovePremiumUpsellPatchFingerprint not found")

        val methods = result.mutableMethod
        val instructions = methods.getInstructions()

        val cond_loc = instructions.filter { it.opcode == Opcode.CONST_STRING }[1].location.index + 3
        val reg = methods.getInstruction<OneRegisterInstruction>(cond_loc).registerA

        methods.addInstructions(cond_loc,"""
            invoke-static {v$reg}, $PREF
             move-result-object v$reg
        """.trimIndent())

        SettingsStatusLoadFingerprint.enableSettings("removePremiumUpsell")
    }
}