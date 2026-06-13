/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.live

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object HideLiveThreadsFingerprint : Fingerprint(
    filters =
        listOf(
            string("is_live"),
        ),
)

val hideLiveThreadsPatch =
    bytecodePatch(
        name = "Hide live threads",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            HideLiveThreadsFingerprint.method.apply {
                val igetObj = instructions.firstOrNull { it.opcode == Opcode.IGET_OBJECT }
                    ?: throw PatchException("Failed to find IGET_OBJECT in HideLiveThreadsFingerprint")

                replaceInstruction(
                    igetObj.location.index,
                    "invoke-static {p0}, Lapp/crimera/piko/patches/twitter/TimelinePatch;->hideLiveThreads(Ljava/lang/Object;)Ljava/lang/Object;",
                )
            }
            enableSettings("hideLiveThreads")
        }
    }
