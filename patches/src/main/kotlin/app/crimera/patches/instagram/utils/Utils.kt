/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.utils

import app.crimera.patches.instagram.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.BytecodePatchContext

context(BytecodePatchContext)
fun enableSettings(functionName: String) {
    SettingsStatusLoadFingerprint.method.addInstruction(
        0,
        Constants.SSTS_DESCRIPTOR.format(functionName),
    )
}
