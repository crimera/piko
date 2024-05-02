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
import crimera.patches.twitter.premium.customAppIconAndNavBtns.fingerprints.CustomAppIconAndNavBtnsFingerprint
import crimera.patches.twitter.premium.customAppIconAndNavBtns.fingerprints.NavBarFixFingerprint

@Patch(
    name = "Enable custom app icon and nav icon settings",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    dependencies = [SettingsPatch::class],
    requiresIntegrations = true
)
object CustomAppIconAndNavBtnsPatch:BytecodePatch(
    setOf(CustomAppIconAndNavBtnsFingerprint, SettingsStatusLoadFingerprint,NavBarFixFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = CustomAppIconAndNavBtnsFingerprint.result
            ?:throw PatchException("CustomAppIconAndNavBtnsFingerprint not found")

        //usually the last method
        val methods = result.mutableClass.methods.last()
        val loc = methods.getInstructions().last { it.opcode == Opcode.CONST }.location.index

        //removes toast condition
        methods.removeInstruction(loc)
        methods.removeInstruction(loc-1)

        //credits aero
        val result2 = NavBarFixFingerprint.result
            ?:throw PatchException("NavBarFixFingerprint not found")

        val methods2 = result2.mutableMethod
        val loc2 = methods2.getInstructions().first { it.opcode == Opcode.IF_NEZ }.location.index
        methods2.removeInstruction(loc2)
        methods2.removeInstruction(loc2)
        methods2.removeInstruction(loc2)

        SettingsStatusLoadFingerprint.enableSettings("enableAppIconNNavIcon")
        //end
    }
}