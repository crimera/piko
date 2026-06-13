/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.entity.mediadata.mediaDataEntity
import app.crimera.patches.instagram.entity.originalsounddata.originalSoundDataIntfEntity
import app.crimera.patches.instagram.entity.trackDataIntf.trackDataIntfEntity
import app.crimera.patches.instagram.misc.download.EditMediaInfoGetCurrentMediaIdFingerprint
import app.crimera.patches.instagram.misc.extension.hooks.handleStoryButtonPatch
import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.FEED_BUTTON_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.REEL_BUTTON_DESCRIPTOR
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.classNameToExtension
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

@Suppress("unused")
val downloadMediaPatch =
    bytecodePatch(
        name = "Download media",
        description = "Adds ability to download posts, reels, stories and highlights",
    ) {
        dependsOn(
            settingsPatch,
            mediaDataEntity,
            handleStoryButtonPatch,
            originalSoundDataIntfEntity,
            trackDataIntfEntity,
            hookFlagsPatch,
        )
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val appActivity = "Landroidx/fragment/app/FragmentActivity;"

            val enumBtnClass = classNameToExtension(EnumButtonClassFingerprint.classDef.type)
            GetEnumButtonClassExtensionFingerprint.changeFirstString(enumBtnClass)

            MediaOptionsOverflowMenuCreatorConstructorFingerprint.classDef.apply {
                val addingFeedButtonMethod = methods.firstOrNull { it.parameters.size > 1 }
                    ?: throw PatchException("Failed to find addingFeedButtonMethod in ${type}")
                AddFeedButtonExtensionFingerprint.changeFirstString(addingFeedButtonMethod.name)
            }

            FeedReplaceAudioDialogHelperFingerprint.method.apply {
                val strMatches = FeedReplaceAudioDialogHelperFingerprint.stringMatches
                if (strMatches.isEmpty()) {
                    throw PatchException("Failed to find string matches in FeedReplaceAudioDialogHelperFingerprint")
                }
                val strIndex = strMatches[0].index
                val addingReelButtonMethodCallIndex = indexOfFirstInstruction(strIndex, Opcode.INVOKE_DIRECT_RANGE) + 1
                if (addingReelButtonMethodCallIndex <= 0) {
                     throw PatchException("Failed to find INVOKE_DIRECT_RANGE after string in FeedReplaceAudioDialogHelperFingerprint")
                }

                val addingReelButtonMethodName = getInstruction(addingReelButtonMethodCallIndex).methodExtractor().name
                AddReelButtonExtensionFingerprint.changeFirstString(addingReelButtonMethodName)
            }

            val currentViewingMediaFieldData =
                EditMediaInfoGetCurrentMediaIdFingerprint.method.instructions
                    .firstOrNull { it.opcode == Opcode.IGET }
                    ?.fieldExtractor()
                    ?: throw PatchException("Failed to find IGET in EditMediaInfoGetCurrentMediaIdFingerprint")

            AddFeedButtonFingerprint.method.apply {
                var arrayListRegister = -1
                var checkCastRegister = -1
                var checkCastIndex = -1

                if (getInstruction(0).opcode == Opcode.INVOKE_STATIC) {
                    arrayListRegister = getInstruction(1).registersUsed[0]
                    checkCastIndex = indexOfFirstInstruction(Opcode.CHECK_CAST)
                    if (checkCastIndex >= 0) {
                        checkCastRegister = getInstruction(checkCastIndex).registersUsed[0]
                    }
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
                        invoke-static {v$checkCastRegister,v$arrayListRegister},$FEED_BUTTON_DESCRIPTOR->addFeedButton(Ljava/lang/Object;Ljava/util/ArrayList;)V
                        """.trimIndent(),
                    )
                }
            }

            FeedButtonOnClickFingerprint.method.apply {
                val classDef = FeedButtonOnClickFingerprint.classDef
                val classFields = classDef.fields

                val appActivityField = classFields.firstOrNull { it.type == appActivity }
                    ?: throw PatchException("Failed to find appActivityField in ${classDef.type}")

                val getMediaObjectMethod =
                    classDef.methods.firstOrNull {
                        AccessFlags.FINAL.isSet(it.accessFlags) && it.implementation?.registerCount == 1
                    } ?: throw PatchException("Failed to find getMediaObjectMethod in ${classDef.type}")

                val mediaExtraDataClass = currentViewingMediaFieldData.definingClass
                val mediaExtraDataField = classDef.fields.firstOrNull { it.type == extensionToClassName(mediaExtraDataClass) }
                     ?: throw PatchException("Failed to find mediaExtraDataField in ${classDef.type}")
                val currentViewingMediaIndexField = currentViewingMediaFieldData.name

                addInstructionsWithLabels(
                    0,
                    """
                    move-object/from16 v1, p1
                    invoke-static {v1}, $FEED_BUTTON_DESCRIPTOR->isDownloadButton(Lcom/instagram/feed/media/mediaoption/MediaOption${'$'}Option;)Z
                    move-result v0
                    if-eqz v0, :piko
                    
                    move-object/from16 v0, p0
                    iget-object v5, v0, $appActivityField
                    invoke-static {v0}, $getMediaObjectMethod
                    move-result-object v2
                    iget-object v4, v0, $mediaExtraDataField
                    iget v4, v4, ${mediaExtraDataField.type}->$currentViewingMediaIndexField:I
                    
                    invoke-static {v5, v2, v4}, $FEED_BUTTON_DESCRIPTOR->downloadPost(Landroid/content/Context;Ljava/lang/Object;I)V
                    return-void
                    
                    """.trimIndent(),
                    ExternalLabel("piko", getInstruction(0)),
                )
            }

            AddReelButtonFingerprint.method.apply {
                val classDef = AddReelButtonFingerprint.classDef
                val classFields = classDef.fields

                val appActivityField = classFields.firstOrNull { it.type == appActivity }
                    ?: throw PatchException("Failed to find appActivityField in ${classDef.type}")

                val moveObjectFrom16Index = indexOfFirstInstruction(Opcode.MOVE_OBJECT_FROM16)
                if (moveObjectFrom16Index < 0) throw PatchException("Failed to find MOVE_OBJECT_FROM16 in AddReelButtonFingerprint")
                val selfClassRegister = instructions[moveObjectFrom16Index].registersUsed[0]

                val newInstanceIndex = indexOfFirstInstruction(Opcode.NEW_INSTANCE)
                if (newInstanceIndex < 0) throw PatchException("Failed to find NEW_INSTANCE in AddReelButtonFingerprint")
                val buttonAdderInstanceRegister = instructions[newInstanceIndex].registersUsed[0]

                val firstIfEqzIndex = indexOfFirstInstruction(Opcode.IF_EQZ)
                if (firstIfEqzIndex <= 0) throw PatchException("Failed to find IF_EQZ in AddReelButtonFingerprint")
                val mediaObjectRegister = instructions[firstIfEqzIndex - 1].registersUsed[0]

                val moveResultObjectIndex = indexOfFirstInstruction(Opcode.MOVE_RESULT_OBJECT)
                if (moveResultObjectIndex < 0) throw PatchException("Failed to find MOVE_RESULT_OBJECT in AddReelButtonFingerprint")
                val freeRegisterOne = instructions[moveResultObjectIndex].registersUsed[0]

                addInstructions(
                    firstIfEqzIndex,
                    """
                    iget-object v$freeRegisterOne, v$selfClassRegister, $appActivityField
                    invoke-static {v$freeRegisterOne,v$buttonAdderInstanceRegister,v$mediaObjectRegister},$REEL_BUTTON_DESCRIPTOR->addReelButton(Landroid/content/Context;Ljava/lang/Object;Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }

            // Make Save option available for all DM content.
            DMLongPressButtonAdderFingerprint.method.apply {
                val allIfNez = instructions.filter { it.opcode == Opcode.IF_NEZ }
                allIfNez.firstOrNull { instruction ->
                    val index = instruction.location.index
                    if (index > 0 && index + 1 < instructions.size) {
                        val opCodeOfPrevInstruction = getInstruction(index - 1).opcode
                        val opCodeOfNextInstruction = getInstruction(index + 1).opcode

                        if (opCodeOfPrevInstruction == Opcode.IF_EQZ && opCodeOfNextInstruction == Opcode.SGET_OBJECT) {
                            removeInstruction(index - 1)
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
            }
            // DM media downloader.
            GetDirectThreadMediaSaverModuleNameFingerprint.apply {

                val appActivityField = classDef.fields.firstOrNull { it.type == "Landroid/app/Activity;" }
                    ?: throw PatchException("Failed to find Activity field in ${classDef.type}")

                val method = classDef.methods
                    .firstOrNull { it.returnType == "V" && it.name != "<init>" }
                    ?: throw PatchException("Failed to find method in ${classDef.type}")

                method.apply {
                    addInstructionsWithLabels(
                        0,
                        """
                        iget-object v0, p1, $appActivityField
                        move-object v1, p2
                        invoke-static {v0, v1}, $DOWNLOAD_DESCRIPTOR/MessageUtils;->messageDownloadCheck(Landroid/content/Context;Ljava/lang/Object;)Z
                        move-result v1
                        if-nez v1, :piko
                        return-void
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(0)),
                    )
                }
            }

            enableSettings("downloadMedia")
            addFlags("simpleOverflowMenuFlags")
        }
    }
