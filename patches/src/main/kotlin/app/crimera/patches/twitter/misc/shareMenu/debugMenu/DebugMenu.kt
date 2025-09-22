package app.crimera.patches.twitter.misc.shareMenu.debugMenu

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.shareMenu.fingerprints.actionEnumsFingerprint
import app.crimera.patches.twitter.misc.shareMenu.hooks.registerButton
import app.crimera.utils.enableSettings
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val debugMenu =
    bytecodePatch(
        name = "Enable debug menu for posts",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val buttonEnumClass = actionEnumsFingerprint.classDef.toString()
            val buttonReference = "$buttonEnumClass->ViewDebugDialog:$buttonEnumClass"
            registerButton(buttonReference, "enableDebugMenu")
            settingsStatusLoadFingerprint.enableSettings("enableDebugMenu")
        }
    }
