package crimera.patches.twitter.link.cleartrackingparams

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.link.cleartrackingparams.fingerprints.AddSessionTokenFingerprint
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

// https://github.com/FrozenAlex/revanced-patches-new
@Patch(
    name = "Clear tracking params",
    description = "Removes tracking parameters when sharing links",
    compatiblePackages = [CompatiblePackage("com.twitter.android")]
)
@Suppress("unused")
object ClearTrackingParamsPatch: BytecodePatch(
    setOf(AddSessionTokenFingerprint,SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = AddSessionTokenFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        result.mutableMethod.addInstruction(0, "return-object p0")

        SettingsStatusLoadFingerprint.enableSettings("cleartrackingparams")
    }
}