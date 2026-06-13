/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.forceHD

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c

private object ForceHDFingerprint : Fingerprint(
    filters =
        listOf(
            string("List"),
            string("VideoVariant"),
        ),
)

private object ForceHDFingerprint2 : Fingerprint(
    filters =
        listOf(
            string("variant"),
        ),
)

val forceHDPatch =
    bytecodePatch(
        name = "Force HD video",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            ForceHDFingerprint.method.apply {
                val invokeInterface = instructions.firstOrNull { it.opcode == Opcode.INVOKE_INTERFACE }
                    ?: throw PatchException("Failed to find INVOKE_INTERFACE in ForceHDFingerprint")
                val listReg = (invokeInterface as BuilderInstruction35c).registerC
                replaceInstruction(
                    invokeInterface.location.index + 1,
                    "invoke-static {v$listReg}, Lapp/crimera/piko/patches/twitter/TimelinePatch;->forceHD(Ljava/util/List;)Ljava/util/List;",
                    "move-result-object v$listReg",
                )
            }
            ForceHDFingerprint2.method.apply {
                val igetObj = instructions.firstOrNull { it.opcode == Opcode.IGET_OBJECT }
                    ?: throw PatchException("Failed to find IGET_OBJECT in ForceHDFingerprint2")
                replaceInstruction(
                    igetObj.location.index,
                    "invoke-static {p0}, Lapp/crimera/piko/patches/twitter/TimelinePatch;->forceHD(Ljava/lang/Object;)Ljava/lang/Object;",
                )
            }
            enableSettings("forceHD")
        }
    }
