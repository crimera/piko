package crimera.patches.twitter.premium.customAppIcon

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object CustomiseAppIconFingerprint:MethodFingerprint(
    strings = listOf(
        "current_app_icon_id"
    )
)

@Patch(
    name = "Enable app icon settings",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true,
    requiresIntegrations = true
)
@Suppress("unused")
object CustomiseAppIcon:BytecodePatch(
    setOf(CustomiseAppIconFingerprint,SettingsStatusLoadFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val result = CustomiseAppIconFingerprint.result
            ?:throw PatchException("CustomiseAppIconFingerprint not found")

        val methods = result.mutableClass.methods.last()
        val loc = methods.getInstructions().last { it.opcode == Opcode.CONST }.location.index

        //removes toast condition
        methods.removeInstruction(loc)
        methods.removeInstruction(loc-1)


        SettingsStatusLoadFingerprint.enableSettings("customAppIcon")
        //end
    }
}