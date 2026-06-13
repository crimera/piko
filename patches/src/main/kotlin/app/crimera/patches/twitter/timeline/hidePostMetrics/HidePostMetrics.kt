/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.hidePostMetrics

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.ExternalLabel
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object HideViewCountFingerprint : Fingerprint(
    filters =
        listOf(
            string("ViewCountLabel"),
        ),
)

private object HideEngagementFingerprint : Fingerprint(
    filters =
        listOf(
            string("TweetEngagement"),
        ),
)

val hidePostMetricsPatch =
    bytecodePatch(
        name = "Hide post metrics",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            HideViewCountFingerprint.method.apply {
                val const16 = instructions.firstOrNull { it.opcode == Opcode.CONST_16 }
                    ?: throw PatchException("Failed to find CONST_16 in HideViewCountFingerprint")

                labels =
                    listOf(
                        ExternalLabel("cond", const16),
                    )
                replaceInstruction(
                    0,
                    "invoke-static {}, $PATCHES_DESCRIPTOR/TimelinePatch;->hidePostMetrics()Z",
                    "move-result v0",
                    "if-nez v0, :cond",
                    "return-void",
                    ":cond",
                )
            }

            HideEngagementFingerprint.method.apply {
                val ifNez = instructions.firstOrNull { it.opcode == Opcode.IF_NEZ }
                    ?: throw PatchException("Failed to find IF_NEZ in HideEngagementFingerprint")
                val ifNezLoc = ifNez.location.index

                val const4 = instructions.firstOrNull { it.opcode == Opcode.CONST_4 }
                    ?: throw PatchException("Failed to find CONST_4 in HideEngagementFingerprint")

                labels =
                    listOf(
                        ExternalLabel("cond", const4),
                    )
                replaceInstruction(
                    ifNezLoc + 1,
                    "invoke-static {}, $PATCHES_DESCRIPTOR/TimelinePatch;->hidePostMetrics()Z",
                    "move-result v0",
                    "if-nez v0, :cond",
                    "return-void",
                    ":cond",
                )
            }
            enableSettings("hidePostMetrics")
        }
    }
