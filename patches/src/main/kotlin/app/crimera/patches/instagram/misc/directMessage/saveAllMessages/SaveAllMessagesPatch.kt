/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.directMessage.saveAllMessages

import app.crimera.patches.instagram.entity.messageInfoEntity.messageInfoEntity
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

internal object DMLongPressButtonAdderFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("⏰", "userSession"),
    filters = listOf(
        // Save option content type check.
        opcode(Opcode.IF_EQZ),
        opcode(Opcode.IF_NEZ, MatchAfterImmediately()),
        opcode(Opcode.SGET_OBJECT, MatchAfterImmediately()),
    ),
)

@Suppress("unused")
val saveAllMessagesPatch =
    bytecodePatch(
        description = "Enables save option for all direct messages",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(messageInfoEntity)
        execute {

            // Make Save option available for all DM content.
            DMLongPressButtonAdderFingerprint.let {
                it.method.removeInstruction(it.instructionMatches.first().index)
            }
        }
    }
