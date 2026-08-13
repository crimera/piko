/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.mediadata

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
            GetHelperClassExtensionFingerprint.changeFirstString(classNameToExtension(MEDIAEXT_CLASS_NAME))

            // Instagram 442 / VC148 Media layout.
            GetExtendedDataExtensionFingerprint.changeFirstString("A04")
            GetMediaListExtensionFingerprint.changeFirstString("A8c")
            GetTrackDataIntfExtensionFingerprint.changeFirstString("A0H")

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

            // Compatibility-only reel mention mapping for Instagram 442.
            GetMentionSetExtensionFingerprint.changeFirstString(LiveTreeMediaDictReelsMentionFingerprint.method.name)

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
            // The first interface call in this fingerprint is List.isEmpty() on Instagram 442.
            // Select the actual list-returning method instead.
            VideoMediaInIGTVFeedHasVideoVariantsFingerprint.method.apply {
                val getVideoVariantsMethodName = instructions
                    .filter { it.opcode == Opcode.INVOKE_INTERFACE }
                    .map { getInstruction(it.location.index).methodExtractor() }
                    .firstOrNull { it.returnType == "Ljava/util/List;" }
                    ?.name
                    ?: error("Could not find the video variants list method")
                GetVideoVariantsV1ExtensionFingerprint.changeFirstString(getVideoVariantsMethodName)
            }

            // Extracting method is video used in media class.
            AslSessionRelatedFingerprint.method.apply {
                val stringIndex = AslSessionRelatedFingerprint.stringMatches[1].index
                val isVideoVirtualInvokeIndex = indexOfFirstInstruction(stringIndex, Opcode.INVOKE_VIRTUAL)
                val isVideoCallingMethodName = getInstruction(isVideoVirtualInvokeIndex).methodExtractor().name
                IsVideoExtensionFingerprint.changeFirstString(isVideoCallingMethodName)
            }

            // Extraction of media pkid from media class.
            FanClubContentPreviewInteractorImplFingerprint.method.apply {
                val strIndex = FanClubContentPreviewInteractorImplFingerprint.stringMatches[1].index

                val mediaPkIdMethodName = instructions[indexOfFirstInstruction(strIndex, Opcode.INVOKE_VIRTUAL)].methodExtractor().name
                GetMediaPkIdExtensionFingerprint.changeFirstString(mediaPkIdMethodName)
            }

            // Instagram 442 exposes the user getter directly on Media.
            GetUserDataWithoutUserSessionExtensionFingerprint.changeFirstString(LiveTreeMediaDictGetUserFingerprint.method.name)

            // Extraction of description.
            val commentObjectClassName: String
            CommentToStringFingerprint.apply {
                commentObjectClassName = classDef.type
                method.apply {
                    val getCommentTextFieldName = instructions.last { it.opcode == Opcode.IGET_OBJECT }.fieldExtractor().name
                    GetDescriptionTextExtensionFingerprint.changeStringAt(1, getCommentTextFieldName)
                }
            }

            val getCommentDataFromMediaMethodName =
                mutableClassDefBy { it.type == MEDIAEXT_CLASS_NAME }
                    .methods
                    .first { it.returnType == commentObjectClassName }
                    .name
            GetDescriptionTextExtensionFingerprint.changeFirstString(getCommentDataFromMediaMethodName)

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
                    LiveTreeMediaDictReelsMentionFingerprint.classDef.fields
                        .first { it.type == classDef.type }
                        .name
                GetMoreExtendedDataExtensionFingerprint.changeFirstString(moreExtendedMediaDataFieldName)

                // In 442 the variants field follows the explicit video_versions key.
                val videoVersionsIndex = stringMatches.first { it.string == "video_versions" }.index
                method.apply {
                    val fieldIndex = indexOfFirstInstruction(videoVersionsIndex, Opcode.IGET_OBJECT)
                    val videoVariantsListFieldName = getInstruction(fieldIndex).fieldExtractor().name
                    GetVideoVariantsV2ExtensionFingerprint.changeFirstString(videoVariantsListFieldName)
                }

                ExtMediaDictImageInfoMapperFingerprint.apply {
                    val strIndex = stringMatches.first().index
                    method.apply {
                        val imageInfoListFieldName = getInstruction(strIndex + 2).fieldExtractor().name
                        GetImageVariantsExtensionFingerprint.changeStringAt(1, imageInfoListFieldName)
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
        }
    }
