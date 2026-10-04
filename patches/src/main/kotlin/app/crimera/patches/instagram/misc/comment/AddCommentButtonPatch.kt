/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

// Thanks to MyInsta.
@Suppress("unused")
val addCommentPatch =
    bytecodePatch(
        description = "Handles adding custom comment button's attributes.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            AddCommentButtonFingerprint.matchAll(1..1).single().method.apply {
                // Include copy button.
                val arrayListInitInstructions =
                    instructions.filter {
                        it.opcode == Opcode.NEW_INSTANCE &&
                            it.getReference<TypeReference>()?.type == "Ljava/util/ArrayList;"
                    }

                val candidates = arrayListInitInstructions.mapNotNull { instruction ->
                    val index = instruction.location.index
                    val constructor = instructions.getOrNull(index + 1) ?: return@mapNotNull null
                    val commentRead = instructions.getOrNull(index + 2) ?: return@mapNotNull null
                    val arrayListRegister = instruction.registersUsed.singleOrNull() ?: return@mapNotNull null
                    val commentRegisters = commentRead.registersUsed
                    if (constructor.opcode != Opcode.INVOKE_DIRECT ||
                        constructor.getReference<MethodReference>()?.let {
                            it.definingClass == "Ljava/util/ArrayList;" && it.name == "<init>" && it.parameterTypes.isEmpty()
                        } != true || constructor.registersUsed != listOf(arrayListRegister) ||
                        commentRead.opcode != Opcode.IGET_OBJECT || commentRegisters.size != 2 ||
                        commentRead.getReference<FieldReference>()?.type != "Lcom/instagram/user/model/User;"
                    ) return@mapNotNull null
                    Triple(index + 2, arrayListRegister, commentRegisters[1])
                }
                val (index, arrayListRegister, commentRegister) = candidates.singleOrNull()
                    ?: throw PatchException("Expected one comment action list initialization, found ${candidates.size}")
                if (arrayListRegister !in 0..15 || commentRegister !in 0..15 || arrayListRegister == commentRegister) {
                    throw PatchException("Expected distinct 4-bit comment action list and comment registers")
                }
                addInstruction(
                    index,
                    "invoke-static {v$arrayListRegister,v$commentRegister},${HANDLE_COMMENT_BUTTON_EXTENSION_CLASS}->addButtons(Ljava/util/List;Ljava/lang/Object;)V",
                )
            }
        }
    }
