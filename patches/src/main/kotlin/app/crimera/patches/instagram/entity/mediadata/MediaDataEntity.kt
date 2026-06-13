/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
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
import app.morphe.patcher.patch.PatchException
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

                val imageExtractionMethod =
                    mediaHelperMethods
                        .firstOrNull { it.parameterTypes.firstOrNull() == "Landroid/content/Context;" && it.returnType == "Ljava/lang/String;" }
                        ?: throw PatchException("Failed to find image extraction method in ${classDef.type}")
                GetPhotoLinkExtensionFingerprint.changeFirstString(imageExtractionMethod.name)
            }

            // Extracting the get mention set method used media helper class.
            ReelsMentionDoubleTapFingerprint.method.apply {
                val invokeStatics = instructions.filter { it.opcode == Opcode.INVOKE_STATIC }
                if (invokeStatics.size < 2) {
                    throw PatchException("Failed to find enough INVOKE_STATIC instructions in ReelsMentionDoubleTapFingerprint")
                }
                val secondInvokeStaticMethodData = invokeStatics[1].methodExtractor()

                GetMentionSetExtensionFingerprint.changeFirstString(secondInvokeStaticMethodData.name)
            }

            // Extracting get video link method used media helper class.
            ClipsEditMetadataControllerRunFingerprint.method.apply {
                val firstInvokeStatic = instructions.firstOrNull { it.opcode == Opcode.INVOKE_STATIC }
                    ?: throw PatchException("Failed to find INVOKE_STATIC in ClipsEditMetadataControllerRunFingerprint")
                val firstInvokeStaticCallingMethodName = firstInvokeStatic.methodExtractor().name
                GetVideoLinkExtensionFingerprint.changeFirstString(firstInvokeStaticCallingMethodName)
            }

            // Extracting method is video used in media class.
            AslSessionRelatedFingerprint.method.apply {
                if (AslSessionRelatedFingerprint.stringMatches.size < 2) {
                     throw PatchException("Failed to find enough string matches in AslSessionRelatedFingerprint")
                }
                val stringIndex = AslSessionRelatedFingerprint.stringMatches[1].index
                val isVideoVirtualInvokeIndex = indexOfFirstInstruction(stringIndex, Opcode.INVOKE_VIRTUAL)
                if (isVideoVirtualInvokeIndex < 0) {
                     throw PatchException("Failed to find INVOKE_VIRTUAL after string match in AslSessionRelatedFingerprint")
                }
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
            }

            // Extraction of media pkid from media class.
            FanClubContentPreviewInteractorImplFingerprint.method.apply {
                if (FanClubContentPreviewInteractorImplFingerprint.stringMatches.size < 2) {
                    throw PatchException("Failed to find enough string matches in FanClubContentPreviewInteractorImplFingerprint")
                }
                val strIndex = FanClubContentPreviewInteractorImplFingerprint.stringMatches[1].index
                val invokeVirtualIndex = indexOfFirstInstruction(strIndex, Opcode.INVOKE_VIRTUAL)
                if (invokeVirtualIndex < 0) {
                    throw PatchException("Failed to find INVOKE_VIRTUAL after string match in FanClubContentPreviewInteractorImplFingerprint")
                }
                val mediaPkIdMethodName = instructions[invokeVirtualIndex].methodExtractor().name
                GetMediaPkIdExtensionFingerprint.changeFirstString(mediaPkIdMethodName)
            }

            // Extraction of user data used in extended media class.
            DirectShareTargetRelatedFingerprint.method.apply {
                val firstConst = indexOfFirstInstruction(Opcode.CONST_4)
                val invokeInterfaceIndex = indexOfFirstInstruction(firstConst, Opcode.INVOKE_INTERFACE)
                if (invokeInterfaceIndex < 0) {
                    throw PatchException("Failed to find INVOKE_INTERFACE in DirectShareTargetRelatedFingerprint")
                }
                val userDataMethodName = instructions[invokeInterfaceIndex].methodExtractor().name
                GetUserDataExtensionFingerprint.changeFirstString(userDataMethodName)
            }

            // Extraction of description
            EditMediaInfoGetCurrentMediaIdFingerprint.method.apply {
                val lastInvokeStaticIndex = instructions.indexOfLast { it.opcode == Opcode.INVOKE_STATIC }
                if (lastInvokeStaticIndex < 0) {
                    throw PatchException("Failed to find INVOKE_STATIC in EditMediaInfoGetCurrentMediaIdFingerprint")
                }
                val getCommentDataFromMediaMethodName =
                    getInstruction(
                        lastInvokeStaticIndex,
                    ).methodExtractor().name

                val lastIgetObjectIndex = instructions.indexOfLast { it.opcode == Opcode.IGET_OBJECT }
                if (lastIgetObjectIndex < 0) {
                     throw PatchException("Failed to find IGET_OBJECT in EditMediaInfoGetCurrentMediaIdFingerprint")
                }
                val getCommentTextFieldName =
                    getInstruction(
                        lastIgetObjectIndex,
                    ).fieldExtractor().name

                GetDescriptionTextExtensionFingerprint.changeFirstString(getCommentDataFromMediaMethodName)
                GetDescriptionTextExtensionFingerprint.changeStringAt(1, getCommentTextFieldName)
            }
        }
    }
