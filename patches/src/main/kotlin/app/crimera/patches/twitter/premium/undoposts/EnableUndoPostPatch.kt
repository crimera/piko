/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.premium.undoposts

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object UndoPostFingerprint : Fingerprint(
    filters =
        listOf(
            string("undo_post_nudge_active"),
        ),
)

private object UndoPostEnabledFingerprint : Fingerprint(
    filters =
        listOf(
            string("undo_post_enabled"),
        ),
)

val enableUndoPostPatch =
    bytecodePatch(
        name = "Enable undo post",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            UndoPostFingerprint.method.apply {
                val ifEqz = instructions.firstOrNull { it.opcode == Opcode.IF_EQZ }
                    ?: throw PatchException("Failed to find IF_EQZ in UndoPostFingerprint")
                replaceInstruction(ifEqz.location.index, "if-nez v0, :cond_0")
            }
            UndoPostEnabledFingerprint.method.apply {
                val ifEqz = instructions.firstOrNull { it.opcode == Opcode.IF_EQZ }
                    ?: throw PatchException("Failed to find IF_EQZ in UndoPostEnabledFingerprint")
                replaceInstruction(ifEqz.location.index, "if-nez v0, :cond_0")
            }
        }
    }
