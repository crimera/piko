/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.showSourceLabel

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object ShowSourceLabelFingerprint : Fingerprint(
    filters =
        listOf(
            string("source"),
        ),
)

val showSourceLabelPatch =
    bytecodePatch(
        name = "Show source label",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            ShowSourceLabelFingerprint.method.apply {
                val igetWide = instructions.firstOrNull { it.opcode == Opcode.IGET_WIDE }
                    ?: throw PatchException("Failed to find IGET_WIDE in ShowSourceLabelFingerprint")

                replaceInstruction(
                    igetWide.location.index,
                    "invoke-static {p0}, Lapp/crimera/piko/patches/twitter/TimelinePatch;->showSourceLabel(Ljava/lang/Object;)Ljava/lang/Object;",
                )
            }
            enableSettings("showSourceLabel")
        }
    }
