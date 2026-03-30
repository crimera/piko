/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.utils

import app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints.FeatureFlagLoadFingerprint
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.utils.Constants.FSTS_DESCRIPTOR
import app.crimera.patches.twitter.utils.Constants.SSTS_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.BytecodePatchContext

context(BytecodePatchContext)
fun enableSettings(functionName: String) {
    SettingsStatusLoadFingerprint.method.addInstruction(
        0,
        "$SSTS_DESCRIPTOR->$functionName()V",
    )
}

context(BytecodePatchContext)
fun flagSettings(functionName: String) {
    FeatureFlagLoadFingerprint.method.addInstruction(
        0,
        "$FSTS_DESCRIPTOR->$functionName()V",
    )
}
