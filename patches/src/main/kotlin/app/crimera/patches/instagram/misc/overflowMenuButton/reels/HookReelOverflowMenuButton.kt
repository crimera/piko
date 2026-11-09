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
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.newInstance
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.p0Register
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal object ClipsItemStateToStringFingerprint : Fingerprint(
    name = "toString",
    strings = listOf("ClipsItemState(lastUserPausedPositionMs="),
)

// Static reel menu builder parameters.
private const val REEL_MENU_STATE_PARAMETER = 2
private const val REEL_MENU_MEDIA_PARAMETER = 3
private const val REEL_MENU_CONTROLLER_PARAMETER = 4

/**
 * Builds the reel overflow menu, where the carousel state is read for the menu options.
 */
private fun reelMenuBuilderFingerprint(mediaExtraDataField: FieldReference) = Fingerprint(
    classFingerprint = AddReelButtonFingerprint,
    strings = listOf("reels"),
    filters = listOf(
        string("ClipsOrganicMediaItemViewMoreOptionsController"),
        // Menu builder.
        newInstance("L", location = MatchAfterImmediately()),
        fieldAccess(reference = mediaExtraDataField, opcode = Opcode.IGET_OBJECT),
        newInstance("Ljava/util/ArrayList;", location = MatchAfterWithin(1)),
    ),
)

@Suppress("unused")
val hookReelOverflowMenuButton =
    bytecodePatch(
        description = "This patch hooks reel overflow button list adder",
    ) {
        dependsOn(reelsOverflowMenuButtonEntity, decoderEntity)
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            val appActivityField = AddReelButtonFingerprint.originalClassDef.fields.single { it.type == FRAGMENT_ACTIVITY }
            val mediaExtraDataField =
                ClipsItemStateToStringFingerprint.originalClassDef.fields.single { it.type == MEDIA_ADD_INFO_CLASS_NAME }

            val reelMenuBuilder = reelMenuBuilderFingerprint(mediaExtraDataField)
            reelMenuBuilder.method.apply {
                val builderRegister = reelMenuBuilder.instructionMatches[1].getInstruction<OneRegisterInstruction>().registerA
                val insertionIndex = reelMenuBuilder.instructionMatches[2].index
                val controllerParameter = p0Register + REEL_MENU_CONTROLLER_PARAMETER
                val mediaParameter = p0Register + REEL_MENU_MEDIA_PARAMETER
                val stateParameter = p0Register + REEL_MENU_STATE_PARAMETER

                val parameterRegisters = (p0Register until implementation!!.registerCount).toList()
                val freeRegisters = getFreeRegisterProvider(insertionIndex, 3, parameterRegisters + builderRegister)
                val contextRegister = freeRegisters.getFreeRegister4Bit()
                val mediaRegister = freeRegisters.getFreeRegister4Bit()
                val indexRegister = freeRegisters.getFreeRegister4Bit()

                // Native overflow menus use index 0 when no carousel state is attached.
                addInstructionsWithLabels(
                    insertionIndex,
                    """
                    move-object/from16 v$contextRegister, v$controllerParameter
                    iget-object v$contextRegister, v$contextRegister, $appActivityField
                    move-object/from16 v$mediaRegister, v$mediaParameter
                    move-object/from16 v$indexRegister, v$stateParameter
                    iget-object v$indexRegister, v$indexRegister, $mediaExtraDataField
                    if-eqz v$indexRegister, :piko_no_carousel_state
                    iget v$indexRegister, v$indexRegister, $CURRENT_MEDIA_FIELD
                    goto :piko_carousel_index_ready
                    :piko_no_carousel_state
                    const/4 v$indexRegister, 0x0
                    :piko_carousel_index_ready
                    invoke-static {v$contextRegister,v$builderRegister,v$mediaRegister,v$indexRegister},$ADD_REEL_BTN_OVERFLOW_MENU_BUTTON_CLASS->includeCustomReelOverflowButtons(Landroid/content/Context;Ljava/lang/Object;Ljava/lang/Object;I)V
                    """.trimIndent(),
                )
            }
        }
    }
