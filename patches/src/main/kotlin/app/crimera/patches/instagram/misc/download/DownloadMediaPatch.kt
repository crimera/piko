/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.entity.decoder.CURRENT_MEDIA_FIELD
import app.crimera.patches.instagram.entity.decoder.MEDIA_ADD_INFO_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.entity.mediadata.mediaDataEntity
import app.crimera.patches.instagram.entity.originalSoundDataIntf.originalSoundDataIntfEntity
import app.crimera.patches.instagram.entity.trackDataIntf.trackDataIntfEntity
import app.crimera.patches.instagram.misc.directMessage.saveAllMessages.saveAllMessagesPatch
import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.overflowMenuButton.addOverflowMenuButtonAttributes
import app.crimera.patches.instagram.misc.overflowMenuButton.debugOverflowButton.debugOverflowMenuButtonPatch
import app.crimera.patches.instagram.misc.overflowMenuButton.hookOverflowMenuButton
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.misc.stories.handleStoryButtonPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.FEED_OVERFLOW_MENU_BUTTON_CLASS
import app.crimera.patches.instagram.utils.Constants.FRAGMENT_ACTIVITY
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeFirstString
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

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
            saveAllMessagesPatch,
            decoderEntity,
            hookOverflowMenuButton,
            debugOverflowMenuButtonPatch,
        )
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            addOverflowMenuButtonAttributes("PIKO_DOWNLOAD", "downloadOverflowButton")

            FeedButtonOnClickFingerprint.method.apply {
                val classDef = FeedButtonOnClickFingerprint.classDef
                val classFields = classDef.fields

                val appActivityField = classFields.first { it.type == FRAGMENT_ACTIVITY }

                val getMediaObjectMethod =
                    classDef.methods.first {
                        AccessFlags.FINAL.isSet(it.accessFlags) && it.implementation?.registerCount == 1
                    }

                val mediaExtraDataField = classDef.fields.first { it.type == MEDIA_ADD_INFO_CLASS_NAME }

                addInstructionsWithLabels(
                    0,
                    """
                    move-object/from16 v1, p1
                    invoke-static {v1}, $FEED_OVERFLOW_MENU_BUTTON_CLASS->isDownloadButton(Lcom/instagram/feed/media/mediaoption/MediaOption${'$'}Option;)Z
                    move-result v0
                    if-eqz v0, :piko
                    
                    move-object/from16 v0, p0
                    iget-object v5, v0, $appActivityField
                    invoke-static {v0}, $getMediaObjectMethod
                    move-result-object v2
                    iget-object v4, v0, $mediaExtraDataField
                    iget v4, v4, $CURRENT_MEDIA_FIELD
                    
                    invoke-static {v5, v2, v4}, $DOWNLOAD_DESCRIPTOR/DownloadUtils;->downloadPost(Landroid/content/Context;Ljava/lang/Object;I)V
                    return-void
                    
                    """.trimIndent(),
                    ExternalLabel("piko", getInstruction(0)),
                )
            }

            FeedReplaceAudioDialogHelperFingerprint.method.apply {
                val strIndex = FeedReplaceAudioDialogHelperFingerprint.stringMatches[0].index
                val addingReelButtonMethodCallIndex = indexOfFirstInstruction(strIndex, Opcode.INVOKE_DIRECT_RANGE) + 1

                val addingReelButtonMethodName = getInstruction(addingReelButtonMethodCallIndex).methodExtractor().name
                AddReelButtonExtensionFingerprint.changeFirstString(addingReelButtonMethodName)
            }

            AddReelButtonFingerprint.method.apply {
                val classDef = AddReelButtonFingerprint.classDef
                val className = classDef.type
                val classFields = classDef.fields

                val appActivityField = classFields.first { it.type == FRAGMENT_ACTIVITY }

                val selfClassRegister = instructions[indexOfFirstInstruction(Opcode.MOVE_OBJECT_FROM16)].registersUsed[0]
                val buttonAdderInstanceRegister = instructions[indexOfFirstInstruction(Opcode.NEW_INSTANCE)].registersUsed[0]

                val sPutIndex = indexOfFirstInstruction(Opcode.SPUT)
                val mediaObjectFromParameterIndex = indexOfFirstInstruction(sPutIndex, Opcode.MOVE_OBJECT_FROM16)
                val mediaObjectRegister = instructions[mediaObjectFromParameterIndex].registersUsed[0]

                val freeRegisterOne = instructions[indexOfFirstInstruction(mediaObjectFromParameterIndex, Opcode.CONST_4)].registersUsed[0]

                addInstructions(
                    mediaObjectFromParameterIndex + 1,
                    """
                    iget-object v$freeRegisterOne, v$selfClassRegister, $appActivityField
                    invoke-static {v$freeRegisterOne,v$buttonAdderInstanceRegister,v$mediaObjectRegister},$REEL_BUTTON_DESCRIPTOR->addReelButton(Landroid/content/Context;Ljava/lang/Object;Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }

            // DM media downloader.
            GetDirectThreadMediaSaverModuleNameFingerprint.apply {

                val appActivityField = classDef.fields.first { it.type == "Landroid/app/Activity;" }

                classDef.methods
                    .first { it.returnType == "V" && it.name != "<init>" }
                    .apply {
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
