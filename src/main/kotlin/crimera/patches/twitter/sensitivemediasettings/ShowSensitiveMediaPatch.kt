package crimera.patches.twitter.sensitivemediasettings

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.sensitivemediasettings.fingerprints.SensitiveMediaSettingsPatchFingerprint

// Credits to @Cradlesofashes
@Patch(
    name = "Show sensitive media",
    description = "Shows sensitive media",
    compatiblePackages = [CompatiblePackage("com.twitter.android")]
)
@Suppress("unused")
object SensitiveMediaPatch: BytecodePatch(
    setOf(SensitiveMediaSettingsPatchFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = SensitiveMediaSettingsPatchFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val method = result.mutableMethod
        val instructions = method.getInstructions()

        instructions.filter{ it.opcode == Opcode.IPUT_BOOLEAN }.forEach {
            method.removeInstruction(it.location.index)
        }
    }
}