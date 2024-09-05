package crimera.patches.twitter.timeline.enableVidAutoAdvance

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
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

object EnableVidAutoAdvancePatchFingerprint : MethodFingerprint(
    strings = listOf("immersive_video_auto_advance_duration_threshold"),
)

@Patch(
    name = "Control video auto scroll",
    description = "Control video auto scroll in immersive view",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    dependencies = [SettingsPatch::class],
    use = true,
)
@Suppress("unused")
class EnableVidAutoAdvancePatch :
    BytecodePatch(
        setOf(EnableVidAutoAdvancePatchFingerprint, SettingsStatusLoadFingerprint),
    ) {
    override fun execute(context: BytecodeContext) {
        val result =
            EnableVidAutoAdvancePatchFingerprint.result
                ?: throw PatchException("EnableVidAutoAdvancePatchFingerprint not found")

        var strLoc: Int = 0
        result.scanResult.stringsScanResult!!.matches.forEach { match ->
            val str = match.string
            if (str.contains("immersive_video_auto_advance_duration_threshold")) {
                strLoc = match.index
                return@forEach
            }
        }
        if (strLoc == 0) {
            throw PatchException("hook not found")
        }

        val method = result.mutableMethod
        val instructions = method.getInstructions()
        val loc = instructions.first { it.opcode == Opcode.MOVE_RESULT && it.location.index > strLoc }.location.index
        val reg = method.getInstruction<OneRegisterInstruction>(loc).registerA
        method.addInstruction(
            loc,
            """
            invoke-static {}, ${SettingsPatch.PREF_DESCRIPTOR};->enableVidAutoAdvance()I
            """.trimIndent(),
        )

        SettingsStatusLoadFingerprint.enableSettings("enableVidAutoAdvance")
    }
}
