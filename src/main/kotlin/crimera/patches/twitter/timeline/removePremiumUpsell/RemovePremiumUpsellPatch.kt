package crimera.patches.twitter.timeline.removePremiumUpsell

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
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

        val PREF = "invoke-static {}, ${SettingsPatch.PREF_DESCRIPTOR};->removePremiumUpsell()Z"

        val result = RemovePremiumUpsellPatchFingerprint.result
            ?: throw PatchException("RemovePremiumUpsellPatchFingerprint not found")

        val methods = result.mutableMethod
        val instructions = methods.getInstructions()

        val cond_loc = instructions.first { it.opcode == Opcode.INVOKE_VIRTUAL }.location.index

        methods.addInstruction(cond_loc+1,PREF)

        SettingsStatusLoadFingerprint.enableSettings("removePremiumUpsell")
    }
}