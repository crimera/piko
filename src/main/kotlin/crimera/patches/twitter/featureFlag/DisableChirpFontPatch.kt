package crimera.patches.twitter.featureFlag

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.featureFlag.fingerprints.FeatureFlagLoadFingerprint
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

@Patch(
    name = "Disable chirp font",
    use = true,
    dependencies = [SettingsPatch::class,FeatureFlagPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")]
)
@Suppress("unused")
object DisableChirpFontPatch: BytecodePatch(
    setOf( FeatureFlagLoadFingerprint,SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {

        FeatureFlagLoadFingerprint.enableSettings("chirpFont")
        SettingsStatusLoadFingerprint.enableSettings("enableFont")
        //end
    }
}