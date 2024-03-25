package crimera.patches.twitter.premium.premiumsettings

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import crimera.patches.twitter.misc.settings.fingerprints.SettingsFingerprint
import crimera.patches.twitter.premium.premiumsettings.fingerprints.PremiumSettingsPatchFingerprint

@Patch(
    name = "Premium settings",
    description = "Unlocks premium options from settings like custom app icon and custom navbar",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
object PremiumSettingsPatch : BytecodePatch(
    setOf(PremiumSettingsPatchFingerprint, SettingsFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        PremiumSettingsPatchFingerprint.result?.mutableMethod?.addInstruction(
            0, "return-void"
        ) ?: throw PatchException("Patch not found")

        val prefCLickedMethod = SettingsFingerprint.result?.mutableClass?.methods?.find {
            it.returnType == "Z"
        } ?: throw PatchException("Method not found")

        prefCLickedMethod.getInstructions().filter { it.opcode == Opcode.INVOKE_INTERFACE }.find {
                prefCLickedMethod.getInstruction<BuilderInstruction35c>(it.location.index).reference.toString()
                    .contains("SubscriptionsUserSubgraph")
            }?.let {
                val loc = it.location.index
                prefCLickedMethod.addInstruction(loc + 4, "const p1, 0x1")
            } ?: throw PatchException("Error adding instruction")
    }
}