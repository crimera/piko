/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
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
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction

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

            val dialogBoxClassMethods = GetDialogFingerprint.classDef.methods
            val dialogBoxAddItemsMethod =
                dialogBoxClassMethods
                    .singleOrNull {
                        it.parameterTypes == listOf("Landroid/content/DialogInterface\$OnClickListener;", "[Ljava/lang/CharSequence;") &&
                            it.returnType == "V"
                    } ?: throw PatchException("Could not uniquely resolve dialog menu method")
            AddDialogMenuItemsExtensionFingerprint.changeFirstString(dialogBoxAddItemsMethod.name)

            // The native builder enables top and bottom rounding together on the menu adapter.
            val cornerWrites = GetDialogFingerprint.method.instructions.toList().windowed(3)
                .filter { (constant, top, bottom) ->
                    constant.opcode == Opcode.CONST_4 &&
                        (constant as NarrowLiteralInstruction).narrowLiteral == 1 &&
                        top.opcode == Opcode.IPUT_BOOLEAN && bottom.opcode == Opcode.IPUT_BOOLEAN &&
                        top.registersUsed.size == 2 && bottom.registersUsed == top.registersUsed &&
                        constant.registersUsed.single() == top.registersUsed.first() &&
                        top.fieldExtractor().definingClass == bottom.fieldExtractor().definingClass &&
                        top.fieldExtractor().definingClass != classNameToExtension(GetDialogFingerprint.classDef.type) &&
                        top.fieldExtractor().name != bottom.fieldExtractor().name
                }.singleOrNull()
                ?: throw PatchException("Could not uniquely resolve dialog menu corner flags")
            ClearMenuBottomCornersExtensionFingerprint.changeFirstString(cornerWrites.last().fieldExtractor().name)
        }
    }
