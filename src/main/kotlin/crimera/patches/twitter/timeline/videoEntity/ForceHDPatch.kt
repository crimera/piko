package crimera.patches.twitter.timeline.videoEntity

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.misc.settings.SettingsPatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint


@Patch(
    name = "Enable force HD videos",
    description = "Videos will be played in highest quality always",
    dependencies = [SettingsPatch::class,VideoEntityPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    requiresIntegrations = true
)
@Suppress("unused")
object ForceHDPatch:BytecodePatch(
    setOf(SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        SettingsStatusLoadFingerprint.enableSettings("enableForceHD")
    }
}