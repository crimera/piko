/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.sanitizeShareLinks

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object SanitizeShareLinksFingerprint : Fingerprint(
    filters =
        listOf(
            string("UTF-8"),
            string("ig_shid"),
        ),
)

val sanitizeShareLinksPatch =
    bytecodePatch(
        name = "Sanitize share links",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            SanitizeShareLinksFingerprint.method.apply {
                val returnObjectInst = instructions.lastOrNull { it.opcode == Opcode.RETURN_OBJECT }
                    ?: throw PatchException("Failed to find RETURN_OBJECT in SanitizeShareLinksFingerprint")

                replaceInstruction(
                    returnObjectInst.location.index,
                    "invoke-static {v0}, Lapp/crimera/piko/patches/instagram/LinkPatch;->sanitizeShareLinks(Ljava/lang/String;)Ljava/lang/String;",
                    "move-result-object v0",
                    "return-object v0",
                )
            }
        }
    }
