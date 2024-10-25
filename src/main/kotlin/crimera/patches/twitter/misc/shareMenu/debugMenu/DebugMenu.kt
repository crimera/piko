package crimera.patches.twitter.misc.shareMenu.debugMenu

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.misc.shareMenu.fingerprints.ShareMenuButtonAddFingerprint
import crimera.patches.twitter.misc.shareMenu.fingerprints.ShareMenuButtonFingerprint

@Patch(
    name = "Enable debug menu for posts",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
object DebugMenu : BytecodePatch(
    setOf(SettingsStatusLoadFingerprint, ShareMenuButtonFingerprint, ShareMenuButtonAddFingerprint),
) {
    override fun execute(context: BytecodeContext) {
        val buttonReference =
            ShareMenuButtonFingerprint.buttonReference("ViewDebugDialog")
                ?: throw PatchException("ShareMenuButtonFingerprint not found")

        ShareMenuButtonAddFingerprint.addButton(buttonReference, "enableDebugMenu")

        SettingsStatusLoadFingerprint.enableSettings("enableDebugMenu")
        // end
    }
}
