/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.sensitivemediasettings

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object ShowSensitiveMediaFingerprint : Fingerprint(
    filters =
        listOf(
            string("possibly_sensitive"),
        ),
)

val showSensitiveMediaPatch =
    bytecodePatch(
        name = "Show sensitive media",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            ShowSensitiveMediaFingerprint.method.apply {
                val returnObj = instructions.lastOrNull { it.opcode == Opcode.RETURN_OBJECT }
                    ?: throw PatchException("Failed to find RETURN_OBJECT in ShowSensitiveMediaFingerprint")

                replaceInstruction(
                    returnObj.location.index,
                    "invoke-static {v0}, Lapp/crimera/piko/patches/twitter/TimelinePatch;->showSensitiveMedia(Ljava/lang/Object;)Ljava/lang/Object;",
                    "move-result-object v0",
                    "return-object v0",
                )
            }
        }
    }
