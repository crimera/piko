package crimera.patches.twitter.misc.shareMenu.debugMenu

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonAddHooks
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonHooks

@Patch(
    name = "Enable debug menu for posts",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
object DebugMenu : BytecodePatch(
    setOf(SettingsStatusLoadFingerprint, ShareMenuButtonHooks, ShareMenuButtonAddHooks),
) {
    override fun execute(context: BytecodeContext) {
        val buttonReference =
            ShareMenuButtonHooks.buttonReference("ViewDebugDialog")
                ?: throw PatchException("ShareMenuButtonHooks not found")

        ShareMenuButtonAddHooks.addButton(buttonReference, "enableDebugMenu")

        SettingsStatusLoadFingerprint.enableSettings("enableDebugMenu")
        // end
    }
}
