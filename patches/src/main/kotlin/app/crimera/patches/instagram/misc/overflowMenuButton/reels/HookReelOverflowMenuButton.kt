/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.overflowMenuButton.reels

import app.crimera.patches.instagram.entity.decoder.CURRENT_MEDIA_FIELD
import app.crimera.patches.instagram.entity.decoder.MEDIA_ADD_INFO_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.misc.download.AddReelButtonFingerprint
import app.crimera.patches.instagram.utils.Constants.ADD_REEL_BTN_OVERFLOW_MENU_BUTTON_CLASS
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.FRAGMENT_ACTIVITY
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

internal object ClipsItemStateToStringFingerprint : Fingerprint(
    name = "toString",
    strings = listOf("ClipsItemState(lastUserPausedPositionMs="),
)

@Suppress("unused")
val hookReelOverflowMenuButton =
    bytecodePatch(
        description = "This patch hooks reel overflow button list adder",
    ) {
        dependsOn(reelsOverflowMenuButtonEntity, decoderEntity)
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            AddReelButtonFingerprint.method.apply {
                val classDef = AddReelButtonFingerprint.classDef
                val classFields = classDef.fields

                val appActivityField = classFields.first { it.type == FRAGMENT_ACTIVITY }

                // Find the field that holds the MEDIA_ADD_INFO object on the reel controller.
                // This is the same class used by the feed hook to get CURRENT_MEDIA_FIELD.
                val mediaExtraDataField = ClipsItemStateToStringFingerprint.classDef.fields.first { it.type == MEDIA_ADD_INFO_CLASS_NAME }

                val selfClassRegister = getInstruction(indexOfFirstInstruction(Opcode.MOVE_OBJECT_FROM16)).registersUsed[0]
                val buttonAdderInstanceRegister = getInstruction(indexOfFirstInstruction(Opcode.NEW_INSTANCE)).registersUsed[0]

                val sPutIndex = indexOfFirstInstruction(Opcode.SPUT)
                val mediaObjectFromParameterIndex = indexOfFirstInstruction(sPutIndex, Opcode.MOVE_OBJECT_FROM16)
                val mediaObjectRegister = getInstruction(mediaObjectFromParameterIndex).registersUsed[0]

                // freeRegisterOne is used for scratch — it's set by CONST_4 after our injection
                // point so it's safe to clobber before that instruction runs.
                val freeRegisterOne =
                    getInstruction(
                        indexOfFirstInstruction(mediaObjectFromParameterIndex, Opcode.CONST_4),
                    ).registersUsed[0]

                val freeRegisterTwo =
                    getInstruction(
                        indexOfFirstInstruction(mediaObjectFromParameterIndex, Opcode.SGET_OBJECT),
                    ).registersUsed[0]

                addInstructions(
                    mediaObjectFromParameterIndex + 1,
                    """
                    move-object/from16 v$freeRegisterOne,p2
                    iget-object v$freeRegisterOne, v$freeRegisterOne, $mediaExtraDataField
                    iget v$freeRegisterOne, v$freeRegisterOne, $CURRENT_MEDIA_FIELD
                    iget-object v$freeRegisterTwo, v$selfClassRegister, $appActivityField
                    invoke-static {v$freeRegisterTwo,v$buttonAdderInstanceRegister,v$mediaObjectRegister,v$freeRegisterOne},$ADD_REEL_BTN_OVERFLOW_MENU_BUTTON_CLASS->includeCustomReelOverflowButtons(Landroid/content/Context;Ljava/lang/Object;Ljava/lang/Object;I)V
                    """.trimIndent(),
                )
            }
        }
    }
