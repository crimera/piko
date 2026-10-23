/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment

import app.crimera.patches.instagram.entity.decoder.COMMENT_BUTTON_CLASS
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.patches.instagram.utils.methodReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction

/**
 * String and drawable resources of a native chat button, from its constructor.
 */
context(_: BytecodePatchContext)
internal fun nativeChatButtonResources(constructor: Fingerprint): Pair<Long, Long> =
    constructor.matchSingle().let {
        val drawable = it.instructionMatches[1].getInstruction<WideLiteralInstruction>().wideLiteral
        val string = it.instructionMatches[2].getInstruction<WideLiteralInstruction>().wideLiteral
        string to drawable
    }

context(patchContext: BytecodePatchContext)
fun addButtonAttribute(
    stringLateral: Long,
    drawableLateral: Long,
    button2CheckFingerprint: Fingerprint,
    buttonInstanceFingerprint: Fingerprint,
) {
    // Previously added buttons changed the method.
    CommentActionButtonFingerprint.clearMatch()
    CommentActionButtonFingerprint.method.apply {
        val matches = CommentActionButtonFingerprint.instructionMatches
        val existingButtonIndex = matches[0].index
        val existingButtonClass = matches[0].fieldReference.definingClass

        val isEqualsInstruction = matches[1].instruction
        val isEqualsClass = matches[1].methodReference.definingClass
        val compareButtonRegister = isEqualsInstruction.registersUsed[0]
        val ourButtonRegister = isEqualsInstruction.registersUsed[1]

        val buttonStyleClass = matches[5].fieldReference.definingClass

        val bundleInstruction = matches[7].instruction
        val bundleMethodRef = matches[7].methodReference
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

        // The button list addition, before the existing buttons.
        val arrayAddInstruction = getInstruction(
            indexOfFirstInstructionReversedOrThrow(
                existingButtonIndex,
                methodCall(smali = "Ljava/util/AbstractCollection;->add(Ljava/lang/Object;)Z"),
            ),
        )

        addInstructionsWithLabels(
            existingButtonIndex + 1,
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
            
            new-instance v$bundleRegister, ${buttonInstanceFingerprint.originalMethod.definingClass}
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
