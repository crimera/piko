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
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

object SnoozeExpLockoutManagerFlagFingerprint : Fingerprint(
    strings = listOf("snooze_expiration_lockout_manager"),
    returnType = "Z",
)

@Suppress("unused")
val removeBuildExpiredPopupPatch =
    bytecodePatch(
        name = "Remove build expired popup",
        description = "Removes the popup that appears after a while, when the app version ages.",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // Get the constructor.
            SnoozeExpLockoutManagerFlagFingerprint.classDef.methods.first().apply {
                val lastIPut = instructions.last { it.opcode == Opcode.IPUT }
                val appAgeRegister = lastIPut.registersUsed[0]

                addInstructions(
                    lastIPut.location.index,
                    """
                     invoke-static/range {v$appAgeRegister .. v$appAgeRegister}, $PREF_DESCRIPTOR->buildAge(I)I
                    move-result v$appAgeRegister
                    """.trimIndent(),
                )

                enableSettings("removeBuildExpirePopup")
            }
        }
    }
