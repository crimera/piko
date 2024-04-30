package crimera.patches.twitter.featureFlag

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.featureFlag.fingerprints.FeatureFlagLoadFingerprint
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

// Credits to @iKirby
@Patch(
    name = "Remove view count",
    description = "Removes the view count from the bottom of tweets",
    dependencies = [SettingsPatch::class,FeatureFlagPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
@Suppress("unused")
object RemoveViewCountPatch: BytecodePatch(
    setOf(FeatureFlagLoadFingerprint,SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        FeatureFlagLoadFingerprint.result!!.mutableMethod.addInstruction(
            0,
            "${SettingsPatch.FSTS_DESCRIPTOR}->viewCount()V"
        )

        SettingsStatusLoadFingerprint.enableSettings("hideViewCount")
    }

}