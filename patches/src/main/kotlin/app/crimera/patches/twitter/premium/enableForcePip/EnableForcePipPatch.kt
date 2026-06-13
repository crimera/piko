/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.premium.enableForcePip

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object ForcePipFingerprint : Fingerprint(
    filters =
        listOf(
            string("PIP_NUDGE_COUNT"),
        ),
)

val enableForcePipPatch =
    bytecodePatch(
        name = "Enable force pip",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            ForcePipFingerprint.method.apply {
                val ifNez = instructions.firstOrNull { it.opcode == Opcode.IF_NEZ }
                    ?: throw PatchException("Failed to find IF_NEZ in ForcePipFingerprint")
                replaceInstruction(ifNez.location.index, "if-eqz v0, :cond_0")

                val sgetObj = instructions.firstOrNull { it.opcode == Opcode.SGET_OBJECT }
                    ?: throw PatchException("Failed to find SGET_OBJECT in ForcePipFingerprint")
                replaceInstruction(sgetObj.location.index, "const/4 v0, 0x1")
            }
        }
    }
