/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.entity.mediadata

import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getMutableMethod
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

val mediaDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of the media data",
    ) {
        execute {
            // Extracting the media helper class name.
            AdCollectionMediaDebugFingerprint.apply {
                GetHelperClassExtensionFingerprint.changeFirstString(classNameToExtension(classDef.toString()))

                // Get all the methods inside media helper class.
                val mediaHelperMethods = mutableClassDefBy { it.type == classDef.type }.methods

                val imageExtractionMethodName =
                    mediaHelperMethods
                        .first { it.parameterTypes.first() == "Landroid/content/Context;" && it.returnType == "Ljava/lang/String;" }
                        .name
                GetPhotoLinkExtensionFingerprint.changeFirstString(imageExtractionMethodName)
            }

            // Extracting the get mention set method used media helper class.
            ReelsMentionDoubleTapFingerprint.method.apply {
                val secondInvokeStaticMethodData = instructions.filter { it.opcode == Opcode.INVOKE_STATIC }[1].methodExtractor()

                GetMentionSetExtensionFingerprint.changeFirstString(secondInvokeStaticMethodData.name)
            }

            // Extracting get video link method used media helper class.
            ClipsEditMetadataControllerRunFingerprint.method.apply {
                val firstInvokeStaticCallingMethodName = instructions.first { it.opcode == Opcode.INVOKE_STATIC }.methodExtractor().name
                GetVideoLinkExtensionFingerprint.changeFirstString(firstInvokeStaticCallingMethodName)
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

                val igetAfterReturn = indexOfFirstInstruction(firstReturnIndex, Opcode.IGET_OBJECT)
                if (igetAfterReturn > 0) {
                    // Old pattern: IGET_OBJECT after RETURN, followed directly by the media list method call
                    val extendedDataFieldName =
                        getInstruction(igetAfterReturn).fieldExtractor().name
                    val mediaListMethodName = getInstruction(igetAfterReturn + 1).methodExtractor().name

                    GetExtendedDataExtensionFingerprint.changeFirstString(extendedDataFieldName)
                    GetMediaListExtensionFingerprint.changeFirstString(mediaListMethodName)
                    foundMediaListMethod = true
                } else {
                    // v423 pattern: all IGET_OBJECTs are before RETURN; INVOKE_STATIC after RETURN
                    // delegates through a static helper to the real INVOKE_INTERFACE method.
                    val lastIgetBeforeReturn = instructions
                        .take(firstReturnIndex)
                        .indexOfLast { it.opcode == Opcode.IGET_OBJECT }
                    if (lastIgetBeforeReturn >= 0) {
                        val extendedDataFieldName =
                            getInstruction(lastIgetBeforeReturn).fieldExtractor().name
                        GetExtendedDataExtensionFingerprint.changeFirstString(extendedDataFieldName)

                        val invokeStaticAfterReturn = indexOfFirstInstructionOrThrow(firstReturnIndex, Opcode.INVOKE_STATIC)
                        val staticMethodRef = (getInstruction(invokeStaticAfterReturn) as ReferenceInstruction)
                            .reference as MethodReference
                        val staticBody = staticMethodRef.getMutableMethod()
                        val interfaceCallIndex = staticBody.indexOfFirstInstructionOrThrow(Opcode.INVOKE_INTERFACE)
                        val realMethodName = ((staticBody.getInstruction(interfaceCallIndex) as ReferenceInstruction)
                            .reference as MethodReference).name

                        GetMediaListExtensionFingerprint.changeFirstString(realMethodName)
                        foundMediaListMethod = true
                    }
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
        }
    }
