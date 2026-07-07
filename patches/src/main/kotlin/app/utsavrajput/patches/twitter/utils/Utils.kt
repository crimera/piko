/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.utsavrajput.patches.twitter.utils

import app.utsavrajput.patches.twitter.featureFlag.featureFlagPatch.fingerprints.FeatureFlagLoadFingerprint
import app.utsavrajput.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.utsavrajput.patches.twitter.utils.Constants.FSTS_DESCRIPTOR
import app.utsavrajput.patches.twitter.utils.Constants.SSTS_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.BytecodePatchContext

context(patchContext: BytecodePatchContext)
fun enableSettings(functionName: String) {
    SettingsStatusLoadFingerprint.method.addInstruction(
        0,
        "$SSTS_DESCRIPTOR->$functionName()V",
    )
}

context(patchContext: BytecodePatchContext)
fun flagSettings(functionName: String) {
    FeatureFlagLoadFingerprint.method.addInstruction(
        0,
        "$FSTS_DESCRIPTOR->$functionName()V",
    )
}
