/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.hideNudgeButtons

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string

private object HideNudgeButtonFingerprint : Fingerprint(
    filters =
        listOf(
            string("nudge_id"),
        ),
)

val hideNudgeButtonPatch =
    bytecodePatch(
        name = "Hide nudge buttons",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            HideNudgeButtonFingerprint.method.apply {
                replaceInstructions(
                    listOf(
                        "return-void",
                    ),
                )
            }
        }
    }
