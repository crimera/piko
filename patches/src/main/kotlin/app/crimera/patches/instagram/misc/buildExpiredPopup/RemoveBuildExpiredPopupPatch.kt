/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.buildExpiredPopup

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

object SnoozeExpLockoutManagerFlagFingerprint : Fingerprint(
    strings = listOf("snooze_expiration_lockout_manager"),
    returnType = "Z",
)

private object BuildExpirationConstructorFingerprint : Fingerprint(
    classFingerprint = SnoozeExpLockoutManagerFlagFingerprint,
    name = "<init>",
    filters = listOf(
        // App age.
        fieldAccess(opcode = Opcode.IPUT),
    ),
)

val removeBuildExpiredPopupPatch =
    bytecodePatch(
        description = "Removes the popup that appears after a while, when the app version ages.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            BuildExpirationConstructorFingerprint.let {
                val appAgeStore = it.instructionMatches.first()
                val appAgeRegister = appAgeStore.getInstruction<TwoRegisterInstruction>().registerA

                it.method.addInstruction(
                    appAgeStore.index,
                    "const/16 v$appAgeRegister, 0x1",
                )
            }
        }
    }
