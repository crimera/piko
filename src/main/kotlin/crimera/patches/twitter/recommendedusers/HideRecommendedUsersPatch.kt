package crimera.patches.twitter.recommendedusers

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.patch.options.PatchOption.PatchExtensions.stringArrayPatchOption
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction 
import crimera.patches.twitter.recommendedusers.fingerprints.HideRecommendedUsersFingerprint


@Patch(
    name = "Hide Recommended Users",
    description = "Hide recommended users that pops up when you follow someone",
    compatiblePackages = [CompatiblePackage("com.twitter.android")]
)
@Suppress("unused")
object HideRecommendedUsers: BytecodePatch(
    setOf(HideRecommendedUsersFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = HideRecommendedUsersFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val method = result.mutableMethod
        val instructions = method.getInstructions()

        val check = instructions.last { it.opcode == Opcode.IGET_OBJECT }.location.index
        val reg = method.getInstruction<OneRegisterInstruction>(check).registerA

        method.addInstruction(check+1, """
            const v$reg, 0x0
        """.trimIndent())
    }
}