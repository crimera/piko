package app.crimera.patches.instagram.misc.RemoveBuildExpiredPopup

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

object AppUpdateLockoutBuilderFingerprint : Fingerprint(
    strings = listOf("android.hardware.sensor.hinge_angle"),
    filters = listOf(
        literal(0x5265c00L)
    )
)

@Suppress("unused")
val removeBuildExpiredPopupPatch = bytecodePatch(
    name = "Remove build expired popup",
    description = "Removes the popup that appears after a while, when the app version ages.",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.instagram.android")

    execute {
        AppUpdateLockoutBuilderFingerprint.method.apply {
            val longToIntIndex = indexOfFirstInstruction(Opcode.LONG_TO_INT)
            val appAgeRegister = getInstruction(longToIntIndex).registersUsed[0]

            // Set app age to 0 days old such that the build expired popup doesn't appear.
            addInstructions(longToIntIndex + 1,"""
                invoke-static {}, ${Constants.PREF_DESCRIPTOR}->buildAge()J
                move-result-wide v$appAgeRegister
            """.trimIndent())

            enableSettings("removeBuildExpirePopup")
        }
    }
}