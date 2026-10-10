/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.overflowMenuButton.reels

import app.crimera.patches.instagram.entity.decoder.CURRENT_MEDIA_FIELD
import app.crimera.patches.instagram.entity.decoder.MEDIA_ADD_INFO_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.misc.download.AddReelButtonFingerprint
import app.crimera.patches.instagram.utils.Constants.ADD_REEL_BTN_OVERFLOW_MENU_BUTTON_CLASS
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.FRAGMENT_ACTIVITY
import app.crimera.patches.shared.declaredParameterRegister
import app.crimera.patches.shared.parameterRegisterStart
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

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
                val appActivityField = classDef.fields.singleOrNull { it.type == FRAGMENT_ACTIVITY }
                    ?: throw PatchException("Expected one reel controller activity field")
                val clipsStateClass = ClipsItemStateToStringFingerprint.classDef
                val mediaExtraDataField = clipsStateClass.fields.singleOrNull { it.type == MEDIA_ADD_INFO_CLASS_NAME }
                    ?: throw PatchException("Expected one reel carousel state field")

                fun parameterRegister(type: String): Int {
                    val parameter = parameterTypes.withIndex().singleOrNull { it.value.toString() == type }
                        ?: throw PatchException("Expected one reel menu parameter of type $type")
                    return declaredParameterRegister(this, parameter.index)
                }
                val controllerParameter = parameterRegister(classDef.type)
                val mediaParameter = parameterRegister(MEDIA_CLASS_NAME)
                val stateParameter = parameterRegister(clipsStateClass.type)

                val markerIndex = instructions.withIndex().singleOrNull { (_, instruction) ->
                    instruction.getReference<StringReference>()?.string == "ClipsOrganicMediaItemViewMoreOptionsController"
                }?.index ?: throw PatchException("Expected one reel menu controller marker")
                val builderIndex = indexOfFirstInstruction(markerIndex, Opcode.NEW_INSTANCE)
                val builderInstruction = getInstruction(builderIndex)
                val builderType = builderInstruction.getReference<TypeReference>()?.type
                    ?: throw PatchException("Could not resolve the reel menu builder type")
                val builderRegister = builderInstruction.registersUsed.singleOrNull()
                    ?.takeIf { it in 0..15 }
                    ?: throw PatchException("Expected one 4-bit reel menu builder register")
                val constructorIndex = instructions.withIndex().firstOrNull { (index, instruction) ->
                    index > builderIndex && instruction.opcode == Opcode.INVOKE_DIRECT &&
                        instruction.getReference<MethodReference>()?.let {
                            it.definingClass == builderType && it.name == "<init>"
                        } == true && instruction.registersUsed.firstOrNull() == builderRegister
                }?.index ?: throw PatchException("Could not resolve the reel menu builder constructor")
                val insertionIndex = instructions.withIndex().singleOrNull { (index, instruction) ->
                    index > constructorIndex && instruction.opcode == Opcode.IGET_OBJECT &&
                        instruction.getReference<FieldReference>()?.let {
                            it.definingClass == mediaExtraDataField.definingClass &&
                                it.name == mediaExtraDataField.name && it.type == mediaExtraDataField.type
                        } == true && instructions.getOrNull(index + 1)?.let {
                            it.opcode == Opcode.NEW_INSTANCE &&
                                it.getReference<TypeReference>()?.type == "Ljava/util/ArrayList;"
                        } == true
                }?.index ?: throw PatchException("Expected one reel carousel state read after menu initialization")

                val parameterRegisters = (parameterRegisterStart(this) until implementation!!.registerCount).toList()
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
