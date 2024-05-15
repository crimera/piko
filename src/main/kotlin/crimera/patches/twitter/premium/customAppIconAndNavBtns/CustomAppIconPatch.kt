package crimera.patches.twitter.premium.customAppIconAndNavBtns

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.premium.customAppIconAndNavBtns.fingerprints.CustomAppIconFingerprint

@Patch(
    name = "Enable app icon settings",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    dependencies = [SettingsPatch::class],
    use = false,
    requiresIntegrations = true
)
object CustomAppIconPatch:BytecodePatch(
    setOf(CustomAppIconFingerprint, SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = CustomAppIconFingerprint.result
            ?:throw PatchException("CustomAppIconFingerprint not found")

        //usually the last method
        val methods = result.mutableClass.methods.last()
        val loc = methods.getInstructions().last { it.opcode == Opcode.CONST }.location.index

        //removes toast condition
        methods.removeInstruction(loc)
        methods.removeInstruction(loc-1)


        SettingsStatusLoadFingerprint.enableSettings("customAppIcon")
    }
}