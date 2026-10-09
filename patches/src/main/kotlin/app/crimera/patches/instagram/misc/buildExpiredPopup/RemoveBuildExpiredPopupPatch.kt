/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.buildExpiredPopup

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

object SnoozeExpLockoutManagerFlagFingerprint : Fingerprint(
    strings = listOf("snooze_expiration_lockout_manager"),
    returnType = "Z",
)

val removeBuildExpiredPopupPatch =
    bytecodePatch(
        description = "Removes the popup that appears after a while, when the app version ages.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val constructor = SnoozeExpLockoutManagerFlagFingerprint.classDef.methods
                .singleOrNull { it.name == "<init>" }
                ?: throw PatchException("Could not uniquely resolve build expiration constructor")
            constructor.apply {
                val appAgeStore = instructions.singleOrNull { it.opcode == Opcode.IPUT }
                    ?: throw PatchException("Could not uniquely resolve build age field assignment")
                val appAgeRegister = appAgeStore.registersUsed[0]

                addInstruction(
                    appAgeStore.location.index,
                    "const/16 v$appAgeRegister, 0x1",
                )
            }
        }
    }
