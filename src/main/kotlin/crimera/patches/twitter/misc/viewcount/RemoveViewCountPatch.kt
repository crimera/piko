package crimera.patches.twitter.misc.viewcount

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.misc.viewcount.fingerprints.RemoveViewCountPatchFingerprint

// Credits to @iKirby
@Patch(
    name = "Remove view count",
    description = "Removes the view count from the bottom of tweets",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
@Suppress("unused")
object RemoveViewCountPatch: BytecodePatch(
    setOf(RemoveViewCountPatchFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = RemoveViewCountPatchFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val method = result.mutableMethod

        method.addInstructions(0, """
            invoke-static {}, ${SettingsPatch.PREF_DESCRIPTOR};->hideViewCount()Z
            move-result v0
            return v0
        """.trimIndent())

        SettingsStatusLoadFingerprint.result!!.mutableMethod.addInstruction(
            0,
            "${SettingsPatch.SSTS_DESCRIPTOR}->hideViewCount()V"
        )
    }

}