/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.mediadata

import app.crimera.patches.instagram.entity.decoder.EditMediaInfoGetCurrentMediaIdFingerprint
import app.crimera.patches.instagram.entity.decoder.MEDIAEXT_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.crimera.utils.classNameToExtension
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

val mediaDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of the media data",
    ) {
        dependsOn(decoderEntity)
        execute {
            var mediaHelperClass: String

            GetHelperClassExtensionFingerprint.changeFirstString(classNameToExtension(MEDIAEXT_CLASS_NAME))

            // Extracting get original sound info data using media and user session.
            GetOriginalSoundDataIntfExtensionFingerprint.changeFirstString(GetOriginalSoundDataIntfFromMediaFingerprint.method.name)

            // Extracting get user data using media and user session.
            GetUserDataWithUserSessionExtensionFingerprint.changeFirstString(GetUserDataFromMediaFingerprint.method.name)

            // Extracting image variants list.
            AyuMidcardMediaHelperImageObjectMethodFingerprint.method.apply {
                val imageVariantsIndex = instructions.indexOfLast { it.opcode == Opcode.INVOKE_INTERFACE }
                val imageVariantsMethodName = getInstruction(imageVariantsIndex).methodExtractor().name
                GetImageVariantsExtensionFingerprint.changeStringAt(1, imageVariantsMethodName)
            }

            // Extracting the get mention set method used media helper class.
            ReelsMentionDoubleTapFingerprint.method.apply {
                val userInteractionListMethodInvoke = instructions.first { it.opcode == Opcode.INVOKE_INTERFACE }.methodExtractor()
                GetMentionSetExtensionFingerprint.changeFirstString(userInteractionListMethodInvoke.name)
            }
            InstagramMainActivityNotificationRelatedFingerprint.apply {
                val strIndex = stringMatches.last().index
                method.apply {
                    val getUserDataInvokeIndex =
                        instructions.indexOfLast {
                            it.opcode == Opcode.INVOKE_INTERFACE &&
                                it.location.index < strIndex
                        }
                    val methodName = getInstruction(getUserDataInvokeIndex).methodExtractor().name
                    GetMentionSetExtensionFingerprint.changeStringAt(1, methodName)
                }
            }

            // Extracting get video variants.
            VideoMediaInIGTVFeedHasVideoVariantsFingerprint.method.apply {
                val firstInvokeInterfaceInstruction = getInstruction(indexOfFirstInstruction(Opcode.INVOKE_INTERFACE))
                val getVideoVariantsMethodName = firstInvokeInterfaceInstruction.methodExtractor().name
                GetVideoVariantsV1ExtensionFingerprint.changeFirstString(getVideoVariantsMethodName)
            }

            // Extracting method is video used in media class.
            AslSessionRelatedFingerprint.method.apply {
                val stringIndex = AslSessionRelatedFingerprint.stringMatches[1].index
                val isVideoVirtualInvokeIndex = indexOfFirstInstruction(stringIndex, Opcode.INVOKE_VIRTUAL)
                val isVideoCallingMethodName = getInstruction(isVideoVirtualInvokeIndex).methodExtractor().name
                IsVideoExtensionFingerprint.changeFirstString(isVideoCallingMethodName)
            }

            // Extraction of extended media data field.
            // Extraction of media list from extended media data.
            var foundMediaListMethod = false
            EditMediaInfoFragmentMediaSizeFingerprint.method.apply {
                val firstReturnIndex = indexOfFirstInstruction(Opcode.RETURN)

                val extendedDataFieldIndex = indexOfFirstInstruction(firstReturnIndex, Opcode.IGET_OBJECT)
                // If iget-object is found after return instruction.
                if (extendedDataFieldIndex > 0) {
                    val extendedDataFieldName =
                        getInstruction(
                            extendedDataFieldIndex,
                        ).fieldExtractor().name
                    val mediaListMethodName = getInstruction(extendedDataFieldIndex + 1).methodExtractor().name

                    GetExtendedDataExtensionFingerprint.changeFirstString(extendedDataFieldName)
                    GetMediaListExtensionFingerprint.changeFirstString(mediaListMethodName)
                    foundMediaListMethod = true
                }
            }

            // Backup for media list extraction if the first fingerprint fails.
            if (!foundMediaListMethod) {
                GetAndroidLinkFromMediaObject.method.apply {
                    val firstIfNeIndex = indexOfFirstInstruction(Opcode.IF_NE)

                    val extendedDataFieldIndex = indexOfFirstInstruction(firstIfNeIndex, Opcode.IGET_OBJECT)
                    val extendedDataFieldName =
                        getInstruction(
                            extendedDataFieldIndex,
                        ).fieldExtractor().name
                    val mediaListMethodName = getInstruction(extendedDataFieldIndex + 1).methodExtractor().name

                    GetExtendedDataExtensionFingerprint.changeFirstString(extendedDataFieldName)
                    GetMediaListExtensionFingerprint.changeFirstString(mediaListMethodName)
                    foundMediaListMethod = true
                }
            }

            // Extraction of media pkid from media class.
            FanClubContentPreviewInteractorImplFingerprint.method.apply {
                val strIndex = FanClubContentPreviewInteractorImplFingerprint.stringMatches[1].index

                val mediaPkIdMethodName = instructions[indexOfFirstInstruction(strIndex, Opcode.INVOKE_VIRTUAL)].methodExtractor().name
                GetMediaPkIdExtensionFingerprint.changeFirstString(mediaPkIdMethodName)
            }

            // Extraction of user data used in extended media class.
            DirectShareTargetRelatedFingerprint.method.apply {
                val firstIfEqz = indexOfFirstInstruction(Opcode.IF_EQZ)
                val userDataMethodName = getInstruction(indexOfFirstInstruction(firstIfEqz, Opcode.INVOKE_INTERFACE)).methodExtractor().name
                GetUserDataWithoutUserSessionExtensionFingerprint.changeFirstString(userDataMethodName)
            }

            // Extraction of description
            EditMediaInfoGetCurrentMediaIdFingerprint.method.apply {

                val getCommentDataFromMediaMethodName =
                    getInstruction(
                        instructions.indexOfLast { it.opcode == Opcode.INVOKE_STATIC },
                    ).methodExtractor().name

                val getCommentTextFieldName =
                    getInstruction(
                        instructions.indexOfLast { it.opcode == Opcode.IGET_OBJECT },
                    ).fieldExtractor().name

                GetDescriptionTextExtensionFingerprint.changeFirstString(getCommentDataFromMediaMethodName)
                GetDescriptionTextExtensionFingerprint.changeStringAt(1, getCommentTextFieldName)
            }

            // Extraction of trackInfo
            MusicAudioTypeEnumStringFingerprint.method.apply {
                instructions.filter { it.opcode == Opcode.INVOKE_STATIC }.firstOrNull {
                    val methodExt = it.methodExtractor()
                    if (methodExt.returnType != "V") {
                        GetTrackDataIntfExtensionFingerprint.changeFirstString(methodExt.name)
                        true
                    } else {
                        false
                    }
                }
            }

            // Message audio.
            IgPlayerControllerRelatedFingerprint.method.apply {
                instructions.filter { it.opcode == Opcode.INVOKE_INTERFACE }.firstOrNull {
                    val methodExt = it.methodExtractor()
                    if (methodExt.returnType.endsWith("AudioIntf")) {
                        GetMessageAudioUrlExtensionFingerprint.changeFirstString(methodExt.name)
                        true
                    } else {
                        false
                    }
                }
            }

            AudioIntfMapperFingerprint.apply {
                val strIndex = stringMatches.first { it.string == AUDIO_SRC_KEY }.index
                method.apply {
                    val getAudioSrcInvokeIndex = indexOfFirstInstruction(strIndex, Opcode.INVOKE_INTERFACE)
                    val methodName = getInstruction(getAudioSrcInvokeIndex).methodExtractor().name
                    GetMessageAudioUrlExtensionFingerprint.changeStringAt(1, methodName)
                }
            }

            // More extended data.
            ExtMediaDictVideoInfoMapperFingerprint.apply {
                val moreExtendedMediaDataFieldName =
                    LiveTreeMediaDictClinitFingerprint.classDef.fields
                        .first { it.type == classDef.type }
                        .name
                GetMoreExtendedDataExtensionFingerprint.changeFirstString(moreExtendedMediaDataFieldName)

                val strIndex = stringMatches.last().index
                method.apply {
                    val videoVariantsListFieldName = getInstruction(strIndex + 2).fieldExtractor().name

                    GetVideoVariantsV2ExtensionFingerprint.changeFirstString(videoVariantsListFieldName)
                }

                ExtMediaDictImageInfoMapperFingerprint.apply {
                    val strIndex = stringMatches.first().index
                    method.apply {
                        val imageInfoListFieldName = getInstruction(strIndex + 2).fieldExtractor().name
                        GetImageVariantsExtensionFingerprint.changeFirstString(imageInfoListFieldName)
                    }
                }
            }

            ProductInfoMapperFingerprint.apply {
                val strIndex = stringMatches.last().index
                method.apply {
                    val productTypeIGetObjectInstruction = getInstruction(indexOfFirstInstruction(strIndex, Opcode.IGET_OBJECT))
                    val productTypeFieldName = productTypeIGetObjectInstruction.fieldExtractor().name
                    GetPostTypeExtensionFingerprint.changeFirstString(productTypeFieldName)
                }
            }

            // End.
        }
    }
