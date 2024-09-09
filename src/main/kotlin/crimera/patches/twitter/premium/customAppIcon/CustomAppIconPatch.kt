package crimera.patches.twitter.premium.customAppIcon

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object CustomiseAppIconFingerprint:MethodFingerprint(
    strings = listOf(
        "current_app_icon_id"
    )
)

@Patch(
    name = "Enable app icon settings",
    dependencies = [SettingsPatch::class,RedirectBMTab::class],
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

        val method = result.mutableClass.methods.last()
        val loc = method.getInstructions().last { it.opcode == Opcode.MOVE_RESULT }.location.index

        val reg = method.getInstruction<OneRegisterInstruction>(loc).registerA

        method.addInstruction(loc+1,"""
            const v$reg, 0x0
        """.trimIndent())

        SettingsStatusLoadFingerprint.enableSettings("customAppIcon")
        //end
    }
}