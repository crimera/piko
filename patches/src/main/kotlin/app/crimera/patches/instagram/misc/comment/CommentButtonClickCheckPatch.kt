/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

// Thanks to MyInsta.
@Suppress("unused")
val commentButtonClickCheckPatch =
    bytecodePatch(
        description = "handles comment button on click check.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {

            CommentButtonClickCheckFingerprint.let {
                val arrayListIndex = it.instructionMatches[1].index
                val arrayListRegister = it.instructionMatches[1].getInstruction<OneRegisterInstruction>().registerA
                val freeRegister = it.instructionMatches.last().getInstruction<OneRegisterInstruction>().registerA

                it.method.addInstructionsWithLabels(
                    arrayListIndex + 1,
                    """
                    move-object/from16 v$freeRegister, p1
                    invoke-static {v$freeRegister, v$arrayListRegister},${HANDLE_COMMENT_BUTTON_EXTENSION_CLASS}->checkOnCommentButtonClick(Ljava/lang/Object;Ljava/util/List;)Z
                    move-result v$freeRegister
                    if-eqz v$freeRegister, :piko
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko", it.method.getInstruction(arrayListIndex + 1)),
                )
            }
        }
    }
