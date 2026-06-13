/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.interaction.downloads.changedirectory

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object ChangeDownloadDirFingerprint : Fingerprint(
    filters =
        listOf(
            string("Twitter"),
        ),
)

val changeDownloadDirPatch =
    bytecodePatch(
        name = "Change download directory",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            ChangeDownloadDirFingerprint.method.apply {
                val invokeVirtual = instructions.firstOrNull { it.opcode == Opcode.INVOKE_VIRTUAL }
                    ?: throw PatchException("Failed to find INVOKE_VIRTUAL in ChangeDownloadDirFingerprint")

                replaceInstruction(
                    invokeVirtual.location.index,
                    "invoke-static {v0}, Lapp/crimera/piko/patches/twitter/DownloadPatch;->getDownloadPath(Ljava/lang/String;)Ljava/lang/String;",
                )
            }
        }
    }
