package crimera.patches.twitter.featureFlag

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.patch.BytecodePatch
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint


import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.featureFlag.fingerprints.FeatureFlagLoadFingerprint

@Patch(
    name = "Hide bookmark icon in timeline",
    dependencies = [SettingsPatch::class,FeatureFlagPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
object HideBookmarkInTimelinePatch:BytecodePatch(
    setOf( FeatureFlagLoadFingerprint,SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {

        FeatureFlagLoadFingerprint.result!!.mutableMethod.addInstruction(
            0,
            "${SettingsPatch.FSTS_DESCRIPTOR}->bookmarkInTimeline()V"
        )

        SettingsStatusLoadFingerprint.result!!.mutableMethod.addInstruction(
            0,
            "${SettingsPatch.SSTS_DESCRIPTOR}->hideInlineBmk()V"
        )

        //end
    }

}