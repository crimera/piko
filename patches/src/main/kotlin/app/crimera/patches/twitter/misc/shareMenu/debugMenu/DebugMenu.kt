/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.shareMenu.debugMenu

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.shareMenu.fingerprints.ActionEnumsFingerprint
import app.crimera.patches.twitter.misc.shareMenu.hooks.registerButton
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.crimera.patches.twitter.utils.versionCheckPatch
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val debugMenu =
    bytecodePatch(
        name = "Enable debug menu for posts",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, versionCheckPatch)

        execute {
            val buttonEnumClass = ActionEnumsFingerprint.classDef.toString()
            val buttonReference = "$buttonEnumClass->ViewDebugDialog:$buttonEnumClass"
            registerButton(buttonReference, "enableDebugMenu")
            enableSettings("enableDebugMenu")
        }
    }
