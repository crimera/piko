package crimera.patches.twitter.misc.shareMenu.debugMenu

import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonAddHook
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonHook
import crimera.patches.twitter.misc.shareMenu.hooks.addButton
import crimera.patches.twitter.misc.shareMenu.hooks.buttonReference

@Suppress("unused")
val debugMenuPatch =
    bytecodePatch(
        name = "Enable debug menu for posts",
    ) {
        dependsOn(settingsPatch)
        compatibleWith("com.twitter.android")

        val shareMenuButtonHookMatch by ShareMenuButtonHook()
        val buttonReference =
            shareMenuButtonHookMatch.buttonReference("ViewDebugDialog")

        val shareMenuButtonAddHookMatch by ShareMenuButtonAddHook()
        shareMenuButtonAddHookMatch.addButton(buttonReference, "enableDebugMenu")

        val settingsStatusMatch by settingsStatusLoadFingerprint()
        settingsStatusMatch.enableSettings("enableDebugMenu")
    }
