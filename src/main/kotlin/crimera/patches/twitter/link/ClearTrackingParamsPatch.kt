package crimera.patches.twitter.link

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.link.fingerprints.AddSessionTokenFingerprint

// https://github.com/FrozenAlex/revanced-patches-new
@Patch(
    name = "Clear tracking params",
    description = "Removes tracking parameters when sharing links",
    compatiblePackages = [CompatiblePackage("com.twitter.android")]
)
@Suppress("unused")
object ClearTrackingParamsPatch: BytecodePatch(
    setOf(AddSessionTokenFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = AddSessionTokenFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        result.mutableMethod.addInstruction(0, "return-object p0")
    }
}