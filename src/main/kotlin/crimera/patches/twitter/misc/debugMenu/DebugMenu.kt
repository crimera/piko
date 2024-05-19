package crimera.patches.twitter.misc.debugMenu

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object DebugMenuFingerprint: MethodFingerprint(
    strings = listOf(
        "unblock",
        "report",
        "report_dsa",
        "Debug",
        "View in Tweet Sandbox",
        "View in Spaces Sandbox",
    )
)

@Patch(
    name = "Enable debug menu for posts",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false
)
object DebugMenu:BytecodePatch(
    setOf(SettingsStatusLoadFingerprint,DebugMenuFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val result = DebugMenuFingerprint.result
            ?:throw PatchException("DebugMenuFingerprint not found")

        val methods = result.mutableClass.methods

        val method = methods.first { it.name == "a" }

        val instructions = method.getInstructions()

        val M = "invoke-static{}, ${SettingsPatch.PREF_DESCRIPTOR};->enableDebugMenu()Z"

        val move_res = instructions.first { it.opcode == Opcode.MOVE_RESULT }.location.index

        method.addInstructions(move_res,M)

        SettingsStatusLoadFingerprint.enableSettings("enableDebugMenu")
        //end
    }


}