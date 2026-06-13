/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.premium.redirectBMNavBar

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object RedirectBMTabFingerprint : Fingerprint(
    filters =
        listOf(
            string("bookmarks"),
        ),
)

val redirectBMTabPatch =
    bytecodePatch(
        name = "Redirect BM tab",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            RedirectBMTabFingerprint.method.apply {
                val firstIget = instructions.firstOrNull { it.opcode == Opcode.IGET_OBJECT }
                    ?: throw PatchException("Failed to find IGET_OBJECT in RedirectBMTabFingerprint")

                replaceInstruction(
                    firstIget.location.index,
                    "invoke-static {p0}, Lapp/crimera/piko/patches/twitter/RedirectBMPatch;->redirectBM(Ljava/lang/Object;)Ljava/lang/Object;",
                )
            }
        }
    }
