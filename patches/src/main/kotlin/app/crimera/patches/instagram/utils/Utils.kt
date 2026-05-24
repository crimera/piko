/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.utils

import app.crimera.patches.instagram.misc.settings.HookFlagsLoadFingerprint
import app.crimera.patches.instagram.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.instagram.utils.Constants.LOAD_FLAGS_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.SSTS_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.BytecodePatchContext

context(patchContext: BytecodePatchContext)
fun enableSettings(functionName: String) {
    SettingsStatusLoadFingerprint.method.addInstruction(
        0,
        SSTS_DESCRIPTOR.format(functionName),
    )
}

context(patchContext: BytecodePatchContext)
fun addFlags(functionName: String) {
    HookFlagsLoadFingerprint.method.addInstruction(
        0,
        LOAD_FLAGS_DESCRIPTOR.format(functionName),
    )
}
