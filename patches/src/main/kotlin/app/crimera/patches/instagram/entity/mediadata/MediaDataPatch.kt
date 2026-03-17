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
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

val mediaDataPatch =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of the media data",
    ) {
        compatibleWith("com.instagram.android")

        execute {

            ReelsMentionDoubleTapFingerprint.method.apply {
                val secondInvokeStaticMethodData = instructions.filter { it.opcode == Opcode.INVOKE_STATIC }[1].methodExtractor()
                GetHelperClassExtensionFingerprint.changeFirstString(secondInvokeStaticMethodData.definingClass)
                GetMentionSetExtensionFingerprint.changeFirstString(secondInvokeStaticMethodData.name)
            }

            ClipsEditMetadataControllerRunFingerprint.method.apply {
                val firstInvokeStaticCallingMethodName = instructions.first { it.opcode == Opcode.INVOKE_STATIC }.methodExtractor().name
                GetVideoLinkExtensionFingerprint.changeFirstString(firstInvokeStaticCallingMethodName)
            }

            MediaUpdateFieldsFingerprint.classDef.apply {
                val extendedImageUrlFieldName = fields.first { it.type == "Lcom/instagram/model/mediasize/ExtendedImageUrl;" }.name
                GetPhotoLinkExtensionFingerprint.changeFirstString(extendedImageUrlFieldName)
            }

            AslSessionRelatedFingerprint.method.apply {
                val stringIndex = AslSessionRelatedFingerprint.stringMatches[1].index
                val isVideoVirtualInvokeIndex = indexOfFirstInstruction(stringIndex, Opcode.INVOKE_VIRTUAL)
                val isVideoCallingMethodName = getInstruction(isVideoVirtualInvokeIndex).methodExtractor().name
                IsVideoExtensionFingerprint.changeFirstString(isVideoCallingMethodName)
            }

            UserDetailFragmentGetAndroidLinkFingerprint.method.apply {
                val extendedDataFieldName = instructions.first { it.opcode == Opcode.IGET_OBJECT }.fieldExtractor().name
                val mediaListMethodName = instructions.first { it.opcode == Opcode.INVOKE_INTERFACE }.methodExtractor().name

                GetExtendedDataExtensionFingerprint.changeFirstString(extendedDataFieldName)
                GetMediaListExtensionFingerprint.changeFirstString(mediaListMethodName)
            }

            FanClubContentPreviewInteractorImplFingerprint.method.apply {
                val strIndex = FanClubContentPreviewInteractorImplFingerprint.stringMatches[1].index

                val mediaPkIdMethodName = instructions[indexOfFirstInstruction(strIndex, Opcode.INVOKE_VIRTUAL)].methodExtractor().name
                GetMediaPkIdExtensionFingerprint.changeFirstString(mediaPkIdMethodName)
            }

            DirectShareTargetRelatedFingerprint.method.apply {
                val firstConst = indexOfFirstInstruction(Opcode.CONST_4)
                val userDataMethodName = instructions[indexOfFirstInstruction(firstConst, Opcode.INVOKE_INTERFACE)].methodExtractor().name
                GetUserDataExtensionFingerprint.changeFirstString(userDataMethodName)
            }
        }
    }
