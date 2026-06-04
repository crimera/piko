/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.utils.changeFirstString
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import kotlin.properties.Delegates

var commentDataObjectClassType: String by Delegates.notNull()
    private set

// Thanks to MyInsta.
@Suppress("unused")
val commentButtonClickCheckPatch =
    bytecodePatch(
        description = "handles comment button on click check.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {

            CommentButtonOnClickFingerprint.apply {

                val commentShareClickStrIndex = stringMatches[1].index

                method.apply {
                    commentDataObjectClassType = parameters[0].type

                    val firstIfEqzIndex = indexOfFirstInstruction(Opcode.IF_EQZ)
                    val freeRegister = getInstruction(firstIfEqzIndex).registersUsed[0]

                    val arrayListInstruction =
                        instructions.last {
                            it.location.index < firstIfEqzIndex && it.opcode == Opcode.MOVE_RESULT_OBJECT
                        }
                    val arrayListIndex = arrayListInstruction.location.index
                    val arrayListRegister = arrayListInstruction.registersUsed[0]

                    addInstructionsWithLabels(
                        arrayListIndex + 1,
                        """
                        move-object/from16 v$freeRegister, p1
                        invoke-static {v$freeRegister, v$arrayListRegister},${HANDLE_COMMENT_BUTTON_EXTENSION_CLASS}->checkOnCommentButtonClick(Ljava/lang/Object;Ljava/util/List;)Z
                        move-result v$freeRegister
                        if-eqz v$freeRegister, :piko
                        return-void
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(arrayListIndex + 1)),
                    )

                    val ifEqzIndex = indexOfFirstInstruction(commentShareClickStrIndex, Opcode.IF_EQZ)
                    val commentTextField = getInstruction(ifEqzIndex - 1).fieldExtractor().name
                    CheckOnCommentButtonClickExtensionFingerprint.changeFirstString(commentTextField)
                }
            }
        }
    }
