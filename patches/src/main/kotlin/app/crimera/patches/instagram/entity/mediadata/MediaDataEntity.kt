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
            GetHelperClassExtensionFingerprint.changeFirstString(classNameToExtension(MEDIAEXT_CLASS_NAME))
            GetOriginalSoundDataIntfExtensionFingerprint.changeFirstString(GetOriginalSoundDataIntfFromMediaFingerprint.method.name)
            GetUserDataWithUserSessionExtensionFingerprint.changeFirstString(GetUserDataFromMediaFingerprint.method.name)

            AyuMidcardMediaHelperImageObjectMethodFingerprint.method.apply {
                val imageVariantsIndex = instructions.indexOfLast { it.opcode == Opcode.INVOKE_INTERFACE }
                GetImageVariantsExtensionFingerprint.changeStringAt(1, getInstruction(imageVariantsIndex).methodExtractor().name)
            }

            ReelsMentionDoubleTapFingerprint.method.apply {
                GetMentionSetExtensionFingerprint.changeFirstString(instructions.first { it.opcode == Opcode.INVOKE_INTERFACE }.methodExtractor().name)
            }
            InstagramMainActivityNotificationRelatedFingerprint.apply {
                val strIndex = stringMatches.last().index
                method.apply {
                    val getUserDataInvokeIndex = instructions.indexOfLast { it.opcode == Opcode.INVOKE_INTERFACE && it.location.index < strIndex }
                    GetMentionSetExtensionFingerprint.changeStringAt(1, getInstruction(getUserDataInvokeIndex).methodExtractor().name)
                }
            }

            VideoMediaInIGTVFeedHasVideoVariantsFingerprint.method.apply {
                GetVideoVariantsV1ExtensionFingerprint.changeFirstString(getInstruction(indexOfFirstInstruction(Opcode.INVOKE_INTERFACE)).methodExtractor().name)
            }

            AslSessionRelatedFingerprint.method.apply {
                val stringIndex = AslSessionRelatedFingerprint.stringMatches[1].index
                val isVideoVirtualInvokeIndex = indexOfFirstInstruction(stringIndex, Opcode.INVOKE_VIRTUAL)
                IsVideoExtensionFingerprint.changeFirstString(getInstruction(isVideoVirtualInvokeIndex).methodExtractor().name)
            }

            EditMediaInfoFragmentMediaSizeFingerprint.method.apply {
                val firstReturnIndex = indexOfFirstInstruction(Opcode.RETURN)
                val extendedDataFieldIndex = indexOfFirstInstruction(firstReturnIndex, Opcode.IGET_OBJECT)
                if (extendedDataFieldIndex >= 0) {
                    val extendedDataFieldName = getInstruction(extendedDataFieldIndex).fieldExtractor().name
                    val mediaListInvokeIndex = indexOfFirstInstruction(extendedDataFieldIndex + 1, Opcode.INVOKE_INTERFACE)
                    if (mediaListInvokeIndex >= 0) {
                        GetExtendedDataExtensionFingerprint.changeFirstString(extendedDataFieldName)
                        GetMediaListExtensionFingerprint.changeFirstString(getInstruction(mediaListInvokeIndex).methodExtractor().name)
                    }
                }
            }

            FanClubContentPreviewInteractorImplFingerprint.method.apply {
                val strIndex = FanClubContentPreviewInteractorImplFingerprint.stringMatches[1].index
                GetMediaPkIdExtensionFingerprint.changeFirstString(instructions[indexOfFirstInstruction(strIndex, Opcode.INVOKE_VIRTUAL)].methodExtractor().name)
            }

            DirectShareTargetRelatedFingerprint.method.apply {
                val firstIfEqz = indexOfFirstInstruction(Opcode.IF_EQZ)
                GetUserDataWithoutUserSessionExtensionFingerprint.changeFirstString(getInstruction(indexOfFirstInstruction(firstIfEqz, Opcode.INVOKE_INTERFACE)).methodExtractor().name)
            }

            EditMediaInfoGetCurrentMediaIdFingerprint.method.apply {
                GetDescriptionTextExtensionFingerprint.changeFirstString(getInstruction(instructions.indexOfLast { it.opcode == Opcode.INVOKE_STATIC }).methodExtractor().name)
                GetDescriptionTextExtensionFingerprint.changeStringAt(1, getInstruction(instructions.indexOfLast { it.opcode == Opcode.IGET_OBJECT }).fieldExtractor().name)
            }

            MusicAudioTypeEnumStringFingerprint.matchOrNull()?.method?.apply {
                instructions.filter { it.opcode == Opcode.INVOKE_STATIC }.firstOrNull {
                    val methodExt = it.methodExtractor()
                    if (methodExt.returnType != "V") {
                        GetTrackDataIntfExtensionFingerprint.changeFirstString(methodExt.name)
                        true
                    } else false
                }
            }

            IgPlayerControllerRelatedFingerprint.method.apply {
                instructions.filter { it.opcode == Opcode.INVOKE_INTERFACE }.firstOrNull {
                    val methodExt = it.methodExtractor()
                    if (methodExt.returnType.endsWith("AudioIntf")) {
                        GetMessageAudioUrlExtensionFingerprint.changeFirstString(methodExt.name)
                        true
                    } else false
                }
            }

            AudioIntfMapperFingerprint.apply {
                val strIndex = stringMatches.first { it.string == AUDIO_SRC_KEY }.index
                method.apply {
                    GetMessageAudioUrlExtensionFingerprint.changeStringAt(1, getInstruction(indexOfFirstInstruction(strIndex, Opcode.INVOKE_INTERFACE)).methodExtractor().name)
                }
            }

            ExtMediaDictVideoInfoMapperFingerprint.apply {
                val moreExtendedMediaDataFieldName = LiveTreeMediaDictClinitFingerprint.classDef.fields.first { it.type == classDef.type }.name
                GetMoreExtendedDataExtensionFingerprint.changeFirstString(moreExtendedMediaDataFieldName)

                val strIndex = stringMatches.last().index
                method.apply {
                    val videoVariantsFieldIndex = indexOfFirstInstruction(strIndex, Opcode.IGET_OBJECT)
                    if (videoVariantsFieldIndex >= 0) {
                        GetVideoVariantsV2ExtensionFingerprint.changeFirstString(getInstruction(videoVariantsFieldIndex).fieldExtractor().name)
                    }
                }

                ExtMediaDictImageInfoMapperFingerprint.apply {
                    val strIndex = stringMatches.first().index
                    method.apply {
                        val imageInfoFieldIndex = indexOfFirstInstruction(strIndex, Opcode.IGET_OBJECT)
                        if (imageInfoFieldIndex >= 0) {
                            GetImageVariantsExtensionFingerprint.changeFirstString(getInstruction(imageInfoFieldIndex).fieldExtractor().name)
                        }
                    }
                }
            }

            ProductInfoMapperFingerprint.apply {
                val strIndex = stringMatches.last().index
                method.apply {
                    val fieldIndex = indexOfFirstInstruction(strIndex, Opcode.IGET_OBJECT)
                    if (fieldIndex >= 0) {
                        GetPostTypeExtensionFingerprint.changeFirstString(getInstruction(fieldIndex).fieldExtractor().name)
                    }
                }
            }
        }
    }
