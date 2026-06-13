/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.dialogbox

import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
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
                val iputObj = instructions.firstOrNull { it.opcode == Opcode.IPUT_OBJECT }
                    ?: throw PatchException("Failed to find IPUT_OBJECT in ShowDialogHelperFingerprint")
                SetTitleExtensionFingerprint.changeFirstString(iputObj.fieldExtractor().name)

                val firstStringMatch = ShowDialogHelperFingerprint.stringMatches.firstOrNull()
                    ?: throw PatchException("Failed to find string matches in ShowDialogHelperFingerprint")
                val firstStringIndex = firstStringMatch.index

                val targetInvokeVirtualInstruction =
                    instructions.lastOrNull {
                        it.opcode == Opcode.INVOKE_VIRTUAL &&
                            it.location.index < firstStringIndex
                    } ?: throw PatchException("Failed to find INVOKE_VIRTUAL before string in ShowDialogHelperFingerprint")
                SetMessageExtensionFingerprint.changeFirstString(targetInvokeVirtualInstruction.methodExtractor().name)

                val lastInvokeVirtual = instructions.lastOrNull { it.opcode == Opcode.INVOKE_VIRTUAL }
                    ?: throw PatchException("Failed to find last INVOKE_VIRTUAL in ShowDialogHelperFingerprint")
                val lastBeforeInvokeVirtualIndex =
                    getInstruction(lastInvokeVirtual.location.index - 1)
                SetOnDismissListenerExtensionFingerprint.changeFirstString(lastBeforeInvokeVirtualIndex.methodExtractor().name)

                TARGET_STRING_ARRAY.forEachIndexed { index, targetString ->
                    val match = ShowDialogHelperFingerprint.stringMatches.firstOrNull { it.string == targetString }
                        ?: throw PatchException("Failed to find string match for '$targetString' in ShowDialogHelperFingerprint")
                    val stringIndex = match.index
                    val targetIndex = indexOfFirstInstruction(stringIndex, Opcode.INVOKE_VIRTUAL)
                    if (targetIndex < 0) throw PatchException("Failed to find INVOKE_VIRTUAL after '$targetString' in ShowDialogHelperFingerprint")
                    val targetInvokeVirtualInstruction = getInstruction(targetIndex)
                    TARGET_FINGERPRINT_ARRAY[index].changeFirstString(targetInvokeVirtualInstruction.methodExtractor().name)
                }
            }

            val dialogBoxClassMethods = GetDialogFingerprint.classDef.methods
            val dialogBoxAddItemsMethod =
                dialogBoxClassMethods
                    .firstOrNull {
                        it.parameters.size > 1 &&
                            it.parameters[1].type == "[Ljava/lang/CharSequence;"
                    } ?: throw PatchException("Failed to find addItems method in ${GetDialogFingerprint.definingClass}")
            AddDialogMenuItemsExtensionFingerprint.changeFirstString(dialogBoxAddItemsMethod.name)
        }
    }
