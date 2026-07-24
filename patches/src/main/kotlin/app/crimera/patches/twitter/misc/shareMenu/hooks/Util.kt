/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.shareMenu.hooks

import app.crimera.patches.twitter.misc.shareMenu.fingerprints.addAction
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.patch.BytecodePatchContext

context(patchContext: BytecodePatchContext)
fun shareMenuButtonInjection(
    actionName: String,
    prefFunctionName: String,
    stringId: String,
    iconId: String,
    statusFunctionName: String,
) {
    // Add action
    val buttonActionReference = addAction(actionName)

    // Register button
    registerButton(buttonActionReference, prefFunctionName)

    // Set Button Text
    setButtonText(actionName, stringId)
    setButtonIcon(actionName, iconId)

    enableSettings(statusFunctionName)
}
