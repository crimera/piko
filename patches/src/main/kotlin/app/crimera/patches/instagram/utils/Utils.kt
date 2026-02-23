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
