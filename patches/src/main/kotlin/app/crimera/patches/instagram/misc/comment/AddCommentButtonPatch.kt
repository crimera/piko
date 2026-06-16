/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

// Thanks to MyInsta.
@Suppress("unused")
val addCommentPatch =
    bytecodePatch(
        description = "Handles adding custom comment button's attributes.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            AddCommentButtonFingerprint.method.apply {
                // Include copy button.
                val arrayListInitInstructions =
                    instructions.filter {
                        it.opcode == Opcode.NEW_INSTANCE &&
                            it.getReference<TypeReference>()?.type == "Ljava/util/ArrayList;"
                    }

                arrayListInitInstructions.firstOrNull { instruction ->
                    val index = instruction.location.index
                    val nextInstructionOpcode: Opcode = getInstruction(index + 1).opcode
                    val nextNextInstruction: Instruction = getInstruction(index + 2)

                    if (nextInstructionOpcode == Opcode.INVOKE_DIRECT && nextNextInstruction.opcode == Opcode.IGET_OBJECT) {
                        val arrayListRegister = instruction.registersUsed[0]
                        val freeRegister = findFreeRegister(index + 1)
                        var commentObjectDataRegister = nextNextInstruction.registersUsed[1]

                        addInstruction(
                            index + 2,
                            """
                            invoke-static {v$arrayListRegister,v$commentObjectDataRegister},${HANDLE_COMMENT_BUTTON_EXTENSION_CLASS}->addButtons(Ljava/util/List;Ljava/lang/Object;)V
                            """.trimIndent(),
                        )
                        true
                    }
                    false
                }
            }
        }
    }
