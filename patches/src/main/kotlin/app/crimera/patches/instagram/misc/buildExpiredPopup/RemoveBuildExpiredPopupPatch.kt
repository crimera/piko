/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.buildExpiredPopup

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

object AppUpdateLockoutBuilderFingerprint : Fingerprint(
    strings = listOf("android.hardware.sensor.hinge_angle"),
    filters =
        listOf(
            literal(0x5265c00L),
        ),
)

@Suppress("unused")
val removeBuildExpiredPopupPatch =
    bytecodePatch(
        name = "Remove build expired popup",
        description = "Removes the popup that appears after a while, when the app version ages.",
    ) {
        dependsOn(settingsPatch)
        compatibleWith("com.instagram.android")

        execute {
            AppUpdateLockoutBuilderFingerprint.method.apply {
                val longToIntIndex = indexOfFirstInstruction(Opcode.LONG_TO_INT)
                val appAgeRegister = getInstruction(longToIntIndex).registersUsed[0]

                addInstructions(
                    longToIntIndex + 1,
                    """
                    invoke-static/range {v$appAgeRegister .. v$appAgeRegister}, ${PREF_DESCRIPTOR}->buildAge(I)I
                    move-result v$appAgeRegister
                    """.trimIndent(),
                )

                enableSettings("removeBuildExpirePopup")
            }
        }
    }
