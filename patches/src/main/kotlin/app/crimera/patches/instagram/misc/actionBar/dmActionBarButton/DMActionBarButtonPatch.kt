/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.dmActionBarButton

import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.lastInstruction
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

object DMActionBarBuilderFingerprint : Fingerprint(
    strings = listOf("threadClientInfra", "actionBarListener"),
    returnType = "V",
)

val dmActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on DM action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            DMActionBarBuilderFingerprint.apply {
                val strIndex = stringMatches.first().index
                method.apply {
                    val viewGroupInstruction = lastInstruction(strIndex, Opcode.MOVE_OBJECT_FROM16)!!
                    val viewGroupRegister = viewGroupInstruction.registersUsed[1]

                    val layoutReturnInstruction = lastInstruction(strIndex, Opcode.MOVE_RESULT_OBJECT)!!
                    val layoutReturnIndex = layoutReturnInstruction.location.index

                    addInstruction(
                        layoutReturnIndex + 1,
                        """
                        invoke-static {v$viewGroupRegister}, $ACTIONBAR_DESCRIPTOR/DMActionBar;->addActionBarButton(Landroid/view/ViewGroup;)V
                        """.trimIndent(),
                    )
                }
            }
        }
    }
