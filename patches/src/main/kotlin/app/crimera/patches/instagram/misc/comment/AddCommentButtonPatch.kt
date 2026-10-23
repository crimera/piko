/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.matchSingle
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

// Thanks to MyInsta.
@Suppress("unused")
val addCommentPatch =
    bytecodePatch(
        description = "Handles adding custom comment button's attributes.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            AddCommentButtonFingerprint.matchSingle().let {
                val index = it.instructionMatches.last().index
                val arrayListRegister = it.instructionMatches.first().getInstruction<OneRegisterInstruction>().registerA
                val commentRegister = it.instructionMatches.last().getInstruction<TwoRegisterInstruction>().registerB
                // Include copy button.
                it.method.addInstruction(
                    index,
                    "invoke-static {v$arrayListRegister,v$commentRegister},${HANDLE_COMMENT_BUTTON_EXTENSION_CLASS}->addButtons(Ljava/util/List;Ljava/lang/Object;)V",
                )
            }
        }
    }
