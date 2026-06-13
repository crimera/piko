/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.hideHiddenReplies

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object HideHiddenRepliesFingerprint : Fingerprint(
    filters =
        listOf(
            string("has_hidden_replies"),
        ),
)

val hideHiddenRepliesPatch =
    bytecodePatch(
        name = "Hide hidden replies",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            HideHiddenRepliesFingerprint.method.apply {
                val igetBool = instructions.lastOrNull { it.opcode == Opcode.IGET_BOOLEAN }
                    ?: throw PatchException("Failed to find IGET_BOOLEAN in HideHiddenRepliesFingerprint")

                replaceInstruction(
                    igetBool.location.index,
                    "const/4 v0, 0x0",
                )
            }
        }
    }
