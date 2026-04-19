/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.entity.mediadata.mediaDataEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.misc.stories.handleStoryButtonPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.shared.misc.mapping.ResourceType
import app.morphe.shared.misc.mapping.getResourceId
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstLiteralInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

@Suppress("unused")
val downloadMediaPatch =
    bytecodePatch(
        name = "Download media",
        description = "Adds ability to download posts, reels, stories and highlights",
    ) {
        dependsOn(settingsPatch, mediaDataEntity, handleStoryButtonPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val appActivity = "Landroidx/fragment/app/FragmentActivity;"

            val enumBtnClass = classNameToExtension(EnumButtonClassFingerprint.classDef.type)
            GetEnumButtonClassExtensionFingerprint.changeFirstString(enumBtnClass)

            MediaOptionsOverflowMenuCreatorConstructorFingerprint.classDef.apply {
                val addingFeedButtonMethodName = methods.first { it.parameters.size > 1 }.name
                AddFeedButtonExtensionFingerprint.changeFirstString(addingFeedButtonMethodName)
            }

            FeedReplaceAudioDialogHelperFingerprint.method.apply {
                val strIndex = FeedReplaceAudioDialogHelperFingerprint.stringMatches[0].index
                val addingReelButtonMethodCallIndex = indexOfFirstInstruction(strIndex, Opcode.INVOKE_DIRECT_RANGE) + 1

                val addingReelButtonMethodName = getInstruction(addingReelButtonMethodCallIndex).methodExtractor().name
                AddReelButtonExtensionFingerprint.changeFirstString(addingReelButtonMethodName)
            }

            val currentViewingMediaFieldData =
                EditMediaInfoFragmentFingerprint.method.instructions
                    .last { it.opcode == Opcode.IGET }
                    .fieldExtractor()

            AddFeedButtonFingerprint.method.apply {
                var arrayListRegister = -1
                var checkCastRegister = -1
                var checkCastIndex = -1

                if (getInstruction(0).opcode == Opcode.INVOKE_STATIC) {
                    arrayListRegister = getInstruction(1).registersUsed[0]
                    checkCastIndex = indexOfFirstInstruction(Opcode.CHECK_CAST)
                    checkCastRegister = getInstruction(checkCastIndex).registersUsed[0]
                } else {
                    val arrayListIndex =
                        indexOfFirstInstructionOrThrow {
                            opcode == Opcode.NEW_INSTANCE &&
                                getReference<TypeReference>()?.type == "Ljava/util/ArrayList;"
                        }

                    val arrayInitInstruction = getInstruction(arrayListIndex + 1)
                    arrayListRegister = arrayInitInstruction.registersUsed[0]
                    checkCastIndex = indexOfFirstInstruction(arrayListIndex, Opcode.CHECK_CAST)
                    checkCastRegister = getInstruction(checkCastIndex).registersUsed[0]
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
                val className = classDef.type
                val classFields = classDef.fields

                val appActivityField = classFields.first { it.type == appActivity }

                val mediaObjectIGetFieldData = instructions[indexOfFirstInstruction(Opcode.IGET_OBJECT)].fieldExtractor()
                val mediaObjectClass = extensionToClassName(mediaObjectIGetFieldData.returnType)
                val mediaObjectField = mediaObjectIGetFieldData.name

                val mediaExtraDataClass = currentViewingMediaFieldData.definingClass
                val mediaExtraDataField = classDef.fields.first { it.type == extensionToClassName(mediaExtraDataClass) }
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
                    iget-object v2, v0, $className->$mediaObjectField:$mediaObjectClass
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
                val className = classDef.type
                val classFields = classDef.fields

                val appActivityField = classFields.first { it.type == appActivity }

                val selfClassRegister = instructions[indexOfFirstInstruction(Opcode.MOVE_OBJECT_FROM16)].registersUsed[0]
                val buttonAdderInstanceRegister = instructions[indexOfFirstInstruction(Opcode.NEW_INSTANCE)].registersUsed[0]

                val firstIfEqzIndex = indexOfFirstInstruction(Opcode.IF_EQZ)
                val mediaObjectRegister = instructions[firstIfEqzIndex - 1].registersUsed[0]

                val freeRegisterOne = instructions[indexOfFirstInstruction(Opcode.MOVE_RESULT_OBJECT)].registersUsed[0]

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
                val resourceLiteralIndex =
                    indexOfFirstLiteralInstruction(getResourceId(ResourceType.DRAWABLE, "instagram_meta_gen_ai_outline_24"))
                val firstIfEqzAfterLiteralIndex = indexOfFirstInstruction(resourceLiteralIndex, Opcode.IF_EQZ)

                removeInstruction(firstIfEqzAfterLiteralIndex)
            }
            // DM media downloader.
            GetDirectThreadMediaSaverModuleNameFingerprint.classDef.methods.first { it.returnType == "V" && it.name != "<init>" }.apply {
                addInstructionsWithLabels(
                    0,
                    """
                    move-object v1, p2
                    invoke-static {v1}, $DOWNLOAD_DESCRIPTOR/MessageUtils;->messageDownloadCheck(Ljava/lang/Object;)Z
                    move-result v1
                    if-nez v1, :piko
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko", getInstruction(0)),
                )
            }
            enableSettings("downloadMedia")
        }
    }
