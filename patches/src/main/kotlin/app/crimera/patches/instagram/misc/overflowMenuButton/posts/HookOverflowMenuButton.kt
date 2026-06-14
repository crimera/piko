/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.overflowMenuButton.posts

import app.crimera.patches.instagram.misc.download.MediaOptionsOverflowMenuCreatorConstructorFingerprint
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.FEED_OVERFLOW_MENU_BUTTON_CLASS
import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

@Suppress("unused")
val hookOverflowMenuButton =
    bytecodePatch(
        description = "This patch hooks array values initialisation in overflow menu button constructor.",
    ) {
        dependsOn(includeButtonsInOverflowMenuArrayPatch, hookOverflowMenuButtonOnClickPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            val enumBtnClass = classNameToExtension(EnumButtonClassFingerprint.classDef.type)
            GetEnumButtonClassExtensionFingerprint.changeFirstString(enumBtnClass)

            MediaOptionsOverflowMenuCreatorConstructorFingerprint.classDef.apply {
                val addingFeedButtonMethodName = methods.first { it.parameters.size > 1 }.name
                AddFeedButtonExtensionFingerprint.changeFirstString(addingFeedButtonMethodName)
            }

            AddFeedButtonFingerprint.method.apply {
                var arrayListRegister = -1
                var checkCastRegister = -1
                var checkCastIndex = -1

                if (getInstruction(0).opcode == Opcode.INVOKE_STATIC) {
                    arrayListRegister = getInstruction(1).registersUsed[0]
                    checkCastIndex = indexOfFirstInstruction(Opcode.CHECK_CAST)
                    checkCastRegister = getInstruction(checkCastIndex).registersUsed[0]
                } else {
                    val arrayListInstructions =
                        instructions.filter {
                            it.opcode == Opcode.NEW_INSTANCE &&
                                it.getReference<TypeReference>()?.type == "Ljava/util/ArrayList;"
                        }

                    arrayListInstructions.firstOrNull { instruction ->
                        val index = instruction.location.index
                        val nextNextInstructionOpcode = getInstruction(index + 2).opcode
                        val nextNextNextInstructionOpcode = getInstruction(index + 3).opcode
                        if (nextNextInstructionOpcode == Opcode.IGET_OBJECT && nextNextNextInstructionOpcode == Opcode.CHECK_CAST) {
                            val arrayInitInstruction = getInstruction(index + 1)
                            arrayListRegister = arrayInitInstruction.registersUsed[0]
                            checkCastIndex = indexOfFirstInstruction(index, Opcode.CHECK_CAST)
                            checkCastRegister = getInstruction(checkCastIndex).registersUsed[0]
                            true
                        }
                        false
                    }
                }

                if (arrayListRegister != -1 && checkCastRegister != -1 && checkCastIndex != -1) {
                    addInstructions(
                        checkCastIndex + 1,
                        """
                        invoke-static {v$checkCastRegister,v$arrayListRegister},$FEED_OVERFLOW_MENU_BUTTON_CLASS->addFeedOverflowButton(Ljava/lang/Object;Ljava/util/ArrayList;)V
                        """.trimIndent(),
                    )
                }
            }
        }
    }
