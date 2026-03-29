/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.entity.dialogbox

import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

val instagramDialogBoxEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of the native box of Instagram",
    ) {
        execute {

            ConstructorExtensionFingerprint.changeFirstString(
                classNameToExtension(GetDialogFingerprint.classDef.type),
            )

            GetDialogExtensionFingerprint.changeFirstString(GetDialogFingerprint.method.name)

            ShowDialogHelperFingerprint.method.apply {
                SetTitleExtensionFingerprint.changeFirstString(instructions.first { it.opcode == Opcode.IPUT_OBJECT }.fieldExtractor().name)

                val firstStringIndex = ShowDialogHelperFingerprint.stringMatches.first().index
                val targetInvokeVirtualInstruction =
                    instructions.last {
                        it.opcode == Opcode.INVOKE_VIRTUAL &&
                            it.location.index < firstStringIndex
                    }
                SetMessageExtensionFingerprint.changeFirstString(targetInvokeVirtualInstruction.methodExtractor().name)

                val lastBeforeInvokeVirtualIndex =
                    getInstruction(instructions.last { it.opcode == Opcode.INVOKE_VIRTUAL }.location.index - 1)
                SetOnDismissListenerExtensionFingerprint.changeFirstString(lastBeforeInvokeVirtualIndex.methodExtractor().name)

                TARGET_STRING_ARRAY.forEachIndexed { index, targetString ->
                    val stringIndex = ShowDialogHelperFingerprint.stringMatches.first { match -> match.string == targetString }.index
                    val targetInvokeVirtualInstruction = getInstruction(indexOfFirstInstruction(stringIndex, Opcode.INVOKE_VIRTUAL))
                    TARGET_FINGERPRINT_ARRAY[index].changeFirstString(targetInvokeVirtualInstruction.methodExtractor().name)
                }
            }

            AddDialogMenuItemsExtensionFingerprint.changeFirstString(DialogBoxAddItemsMethodFingerprint.method.name)
        }
    }
