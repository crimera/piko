/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment

import app.crimera.patches.instagram.entity.decoder.COMMENT_BUTTON_CLASS
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.indexOfFirstLiteralInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

context(patchContext: BytecodePatchContext)
fun addButtonAttribute(
    stringLateral: Long,
    drawableLateral: Long,
    button2CheckFingerprint: Fingerprint,
    buttonInstanceFingerprint: Fingerprint,
) {
    AddCommentButtonFingerprint.method.apply {
        val drawableId = getResourceId(ResourceType.DRAWABLE, "instagram_eye_off_outline_24")
        val drawableIndex = indexOfFirstLiteralInstruction(drawableId)

        val arrayAddInstruction =
            instructions.last { it.opcode == Opcode.INVOKE_VIRTUAL && it.location.index < drawableIndex }
        val existingButtonSGetObjectInstruction =
            instructions.last {
                it.opcode == Opcode.SGET_OBJECT &&
                    it.location.index < drawableIndex
            }

        val existingButtonClass =
            extensionToClassName(existingButtonSGetObjectInstruction.fieldExtractor().definingClass)

        val isEqualsInstruction =
            instructions.last { it.opcode == Opcode.INVOKE_STATIC && it.location.index < drawableIndex }
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
            sget-object v$ourButtonRegister, ${button2CheckFingerprint.classDef.fields.first()}
            invoke-static {v$compareButtonRegister, v$ourButtonRegister}, $isEqualsClass->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z
            move-result v$ourButtonRegister
            
            if-eqz v$ourButtonRegister, :next_button
            const v$ourButtonRegister, $stringLateral
            new-instance v$stringInitRegister, $stringInitClass
            invoke-direct {v$stringInitRegister, v$ourButtonRegister}, $stringInitClass-><init>(I)V
            
            const v$ourButtonRegister, $drawableLateral
            new-instance v$drawableInitRegister, $drawableInitClass
            invoke-direct {v$drawableInitRegister, v$ourButtonRegister}, $drawableInitClass-><init>(I)V
            
            sget-object v$buttonStyleRegister, $buttonStyleClass->A00:$buttonStyleClass
            
            new-instance v$bundleRegister, ${buttonInstanceFingerprint.definingClass}
            invoke-direct {v$bundleRegister, v$buttonStyleRegister, v$drawableInitRegister, v$stringInitRegister, v$buttonInvokeRelatedRegister}, $bundleClass-><init>($buttonStyleParentClass $drawableInitClass $stringInitClass $buttonInvokeRelatedClass)V
            
            goto :array_add
            :next_button
            sget-object v0, $existingButtonClass->A00:$existingButtonClass
            """.trimIndent(),
            ExternalLabel("array_add", arrayAddInstruction),
        )

        // Dynamically inject super class to  our extension class
        buttonInstanceFingerprint.classDef.setSuperClass(bundleClass)
    }
}

context(patchContext: BytecodePatchContext)
fun addButtonInterface(buttonInstanceFingerprint: Fingerprint) {
    // Dynamically inject interface to our extension class.
    buttonInstanceFingerprint.classDef.interfaces.add(COMMENT_BUTTON_CLASS)
}
