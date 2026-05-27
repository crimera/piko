/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.mainFeedActionBarButton

import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.addFlags
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

object BindMainFeedActionBarFingerprint : Fingerprint(
    strings = listOf("BindMainFeedActionBar"),
    returnType = "Ljava/lang/Object;",
)

val mainFeedActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on main feed action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            BindMainFeedActionBarFingerprint.apply {
                val strIndex = stringMatches.first().index
                method.apply {
                    val allIfEqz = instructions.filter { it.location.index > strIndex && it.opcode == Opcode.IF_EQZ }
                    allIfEqz.firstOrNull {
                        val index = it.location.index
                        val prevInstruction = getInstruction(index - 1)
                        val prevInstructionOpcode = prevInstruction.opcode
                        if (prevInstructionOpcode == Opcode.IGET_OBJECT) {
                            val layoutRegister = prevInstruction.registersUsed[0]
                            addInstruction(
                                index,
                                """
                                invoke-static {v$layoutRegister}, $ACTIONBAR_DESCRIPTOR/MainFeedActionBar;->addActionBarButton(Landroid/view/ViewGroup;)V
                                """.trimIndent(),
                            )
                            addFlags("mainFeedActionBarFlags")
                            true
                        } else {
                            false
                        }
                    }
                }
            }
        }
    }
