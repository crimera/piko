/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.mediadata

import app.crimera.patches.instagram.misc.download.EditMediaInfoGetCurrentMediaIdFingerprint
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
        execute {
            // Extracting the media helper class name.
            ReelsInlineQualitySurveyRelatedFingerprint.apply {
                GetHelperClassExtensionFingerprint.changeFirstString(classNameToExtension(classDef.toString()))

                // Get all the methods inside media helper class.
                val mediaHelperMethods = mutableClassDefBy { it.type == classDef.type }.methods

                val imageExtractionMethodName =
                    mediaHelperMethods
                        .first { it.parameterTypes.first() == "Landroid/content/Context;" && it.returnType == "Ljava/lang/String;" }
                        .name
                GetPhotoLinkExtensionFingerprint.changeFirstString(imageExtractionMethodName)

                val originalSoundDataExtractionMethodName =
                    mediaHelperMethods
                        .first {
                            it.returnType.endsWith(
                                "OriginalSoundDataIntf;",
                            )
                        }.name

                GetOriginalSoundDataIntfExtensionFingerprint.changeFirstString(originalSoundDataExtractionMethodName)
            }

            // Extracting the get mention set method used media helper class.
            ReelsMentionDoubleTapFingerprint.method.apply {
                val secondInvokeStaticMethodData = instructions.filter { it.opcode == Opcode.INVOKE_STATIC }[1].methodExtractor()

                GetMentionSetExtensionFingerprint.changeFirstString(secondInvokeStaticMethodData.name)
            }

            // Extracting get video variants.
            VideoMediaInIGTVFeedHasVideoVariantsFingerprint.method.apply {
                val firstInvokeInterfaceInstruction = getInstruction(indexOfFirstInstruction(Opcode.INVOKE_INTERFACE))
                val getVideoVariantsMethodName = firstInvokeInterfaceInstruction.methodExtractor().name
                GetVideoVariantsExtensionFingerprint.changeFirstString(getVideoVariantsMethodName)
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
                val firstConst = indexOfFirstInstruction(Opcode.CONST_4)
                val userDataMethodName = instructions[indexOfFirstInstruction(firstConst, Opcode.INVOKE_INTERFACE)].methodExtractor().name
                GetUserDataExtensionFingerprint.changeFirstString(userDataMethodName)
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
            ClipsAudioUtilGetTitleFingerprint.classDef.apply {
                methods.last { it.parameters.size == 3 && it.returnType == "Ljava/lang/String;" }.apply {
                    val filterInvokeStatic = instructions.filter { it.opcode == Opcode.INVOKE_STATIC }

                    val getTrackInfoFromMediaMethodName = filterInvokeStatic[2].methodExtractor().name
                    GetTrackDataIntfExtensionFingerprint.changeFirstString(getTrackInfoFromMediaMethodName)
                }
            }
        }
    }
