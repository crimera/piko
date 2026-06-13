/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.showpollresults

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object ShowPollResultsFingerprint : Fingerprint(
    filters =
        listOf(
            string("Poll: "),
        ),
)

val showPollResultsPatch =
    bytecodePatch(
        name = "Show poll results",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            ShowPollResultsFingerprint.method.apply {
                val moveResultObject = instructions.firstOrNull { it.opcode == Opcode.MOVE_RESULT_OBJECT }
                    ?: throw PatchException("Failed to find MOVE_RESULT_OBJECT in ShowPollResultsFingerprint")

                addInstructions(
                    moveResultObject.location.index + 1,
                    "invoke-static {v0}, $PATCHES_DESCRIPTOR/TimelinePatch;->showPollResults(Ljava/lang/Object;)Ljava/lang/Object;",
                    "move-result-object v0",
                )
            }
            enableSettings("showPollResults")
        }
    }
