package crimera.patches.twitter.timeline.banner


import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.timeline.banner.fingerprints.HideBannerFingerprint

@Patch(
    name = "Hide Banner",
    description = "Hide new post banner",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false
)
@Suppress("unused")
object HideBannerPatch : BytecodePatch(
    setOf(HideBannerFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = HideBannerFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val method = result.mutableMethod
        val instuctions = method.getInstructions()

        val loc = instuctions.first{it.opcode == Opcode.IF_NEZ}.location.index

        method.removeInstruction(loc)

    }
}