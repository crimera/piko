/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.link.unshorten

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object NoShortenedUrlFingerprint : Fingerprint(
    filters =
        listOf(
            string("t.co"),
        ),
)

val noShortenedUrlPatch =
    bytecodePatch(
        name = "No shortened URL",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            NoShortenedUrlFingerprint.method.apply {
                val returnObj = instructions.lastOrNull { it.opcode == Opcode.RETURN_OBJECT }
                    ?: throw PatchException("Failed to find RETURN_OBJECT in NoShortenedUrlFingerprint")

                replaceInstruction(
                    returnObj.location.index,
                    "invoke-static {v0}, Lapp/crimera/piko/patches/twitter/LinkPatch;->unshorten(Ljava/lang/String;)Ljava/lang/String;",
                    "move-result-object v0",
                    "return-object v0",
                )
            }
        }
    }
