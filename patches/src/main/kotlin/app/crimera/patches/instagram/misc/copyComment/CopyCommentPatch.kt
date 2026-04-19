/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.copyComment

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeFirstString
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.shared.misc.mapping.ResourceType
import app.morphe.shared.misc.mapping.getResourceId
import app.morphe.shared.misc.mapping.resourceMappingPatch
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.indexOfFirstLiteralInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31i
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

// Thanks to MyInsta.
@Suppress("unused")
val copyCommentPatch =
    bytecodePatch(
        name = "Copy comment",
        description = "Adds a button to copy comments on posts and reels.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, resourceMappingPatch)
        execute {

            var copyTextDrawableLateral: Long
            var copyTextStringLateral: Long
            CopyTextChatButtonToStringFingerprint.classDef.methods.first { it.name == "<init>" }.apply {
                copyTextDrawableLateral = (instructions.first { it.opcode == Opcode.CONST } as Instruction31i).wideLiteral
                copyTextStringLateral = (instructions.last { it.opcode == Opcode.CONST } as Instruction31i).wideLiteral
            }

            AddCommentButtonFingerprint.method.apply {
                // Include copy button.
                val arrayListInitInstruction =
                    instructions.filter {
                        it.opcode == Opcode.NEW_INSTANCE &&
                            it.getReference<TypeReference>()?.type == "Ljava/util/ArrayList;"
                    }[1]

                val arrayListIndex = arrayListInitInstruction.location.index

                val arrayListRegister = arrayListInitInstruction.registersUsed[0]
                val freeRegister = findFreeRegister(arrayListIndex + 1)

                addInstruction(
                    arrayListIndex + 2,
                    """
                    invoke-static {v$arrayListRegister},$HANDLE_COMMENT_BUTTON_EXTENSION_CLASS->addCopyButton(Ljava/util/List;)V
                    """.trimIndent(),
                )

                // Copy button attributes.
                val drawableId = getResourceId(ResourceType.DRAWABLE, "instagram_eye_off_outline_24")
                val drawableIndex = indexOfFirstLiteralInstruction(drawableId)

                val arrayAddInstruction = instructions.last { it.opcode == Opcode.INVOKE_VIRTUAL && it.location.index < drawableIndex }
                val existingButtonSGetObjectInstruction =
                    instructions.last {
                        it.opcode == Opcode.SGET_OBJECT &&
                            it.location.index < drawableIndex
                    }

                val existingButtonClass = extensionToClassName(existingButtonSGetObjectInstruction.fieldExtractor().definingClass)

                val isEqualsInstruction = instructions.last { it.opcode == Opcode.INVOKE_STATIC && it.location.index < drawableIndex }
                val isEqualsClass = extensionToClassName(isEqualsInstruction.methodExtractor().definingClass)
                val compareButtonRegister = isEqualsInstruction.registersUsed[0]
                val ourButtonRegister = isEqualsInstruction.registersUsed[1]

                val buttonStyleInstruction = getInstruction(indexOfFirstInstruction(drawableIndex, Opcode.SGET_OBJECT))
                val buttonStyleClass = extensionToClassName(buttonStyleInstruction.fieldExtractor().definingClass)

                val gotoIndex = indexOfFirstInstruction(drawableIndex, Opcode.GOTO)
                val bundleInstruction = getInstruction(gotoIndex - 1)
                val bundleMethodRef = bundleInstruction.getReference<MethodReference>()!!
                val bundleClass = bundleMethodRef.definingClass
                val bundleParameters = bundleMethodRef.parameterTypes
                val bundleRegisters = bundleInstruction.registersUsed

                val bundleRegister = bundleRegisters[0]

                val buttonStyleParentClass = bundleParameters[0]
                val buttonStyleRegister = bundleRegisters[1]

                val drawableInitClass = bundleParameters[1]
                val drawableInitRegister = bundleRegisters[2]

                val stringInitClass = bundleParameters[2]
                val stringInitRegister = bundleRegisters[3]

                val buttonInvokeRelatedClass = bundleParameters[3]
                val buttonInvokeRelatedRegister = bundleRegisters[4]

                addInstructionsWithLabels(
                    existingButtonSGetObjectInstruction.location.index + 1,
                    """
                    sget-object v$ourButtonRegister, ${COPY_BUTTON_EXTENSION_CLASS}->A00:${COPY_BUTTON_EXTENSION_CLASS}
                    invoke-static {v$compareButtonRegister, v$ourButtonRegister}, $isEqualsClass->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z
                    move-result v$ourButtonRegister
                    
                    if-eqz v$ourButtonRegister, :next_button
                    const v$ourButtonRegister, $copyTextStringLateral
                    new-instance v$stringInitRegister, $stringInitClass
                    invoke-direct {v$stringInitRegister, v$ourButtonRegister}, $stringInitClass-><init>(I)V
                    
                    const v$ourButtonRegister, $copyTextDrawableLateral
                    new-instance v$drawableInitRegister, $drawableInitClass
                    invoke-direct {v$drawableInitRegister, v$ourButtonRegister}, $drawableInitClass-><init>(I)V
                    
                    sget-object v$buttonStyleRegister, $buttonStyleClass->A00:$buttonStyleClass
                    
                    new-instance v$bundleRegister, $INIT_COPY_BUTTON_EXTENSION_CLASS
                    invoke-direct {v$bundleRegister, v$buttonStyleRegister, v$drawableInitRegister, v$stringInitRegister, v$buttonInvokeRelatedRegister}, $bundleClass-><init>($buttonStyleParentClass $drawableInitClass $stringInitClass $buttonInvokeRelatedClass)V
                    
                    goto :array_add
                    :next_button
                    sget-object v0, $existingButtonClass->A00:$existingButtonClass
                    """.trimIndent(),
                    ExternalLabel("array_add", arrayAddInstruction),
                )

                // Dynamically inject super class to  our extension class
                InitCopyButtonInitExtensionFingerprint.classDef.setSuperClass(bundleClass)
            }

            CommentButtonOnClickFingerprint.apply {

                val commentShareClickStrIndex = stringMatches[1].index

                method.apply {
                    // Dynamically inject interface to  our extension class.
                    val mutableClass = mutableClassDefBy(classDefBy(COPY_BUTTON_EXTENSION_CLASS))
                    mutableClass.interfaces.add(parameters[0].type)

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
                        invoke-static {v$freeRegister, v$arrayListRegister},$HANDLE_COMMENT_BUTTON_EXTENSION_CLASS->checkOnCommentButtonClick(Ljava/lang/Object;Ljava/util/List;)Z
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

            enableSettings("copyCommentButton")
        }
    }
