/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.mediadata

import app.crimera.patches.instagram.entity.decoder.EditMediaInfoGetCurrentMediaIdFingerprint
import app.crimera.patches.instagram.entity.decoder.MEDIAEXT_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.utils.Constants.MUSIC_INFO_CLASS
import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.crimera.utils.classNameToExtension
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import kotlin.math.abs

// How far from a json key the field holding its value can sit in the mapper.
private const val FIELD_SEARCH_WINDOW = 8

val mediaDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of the media data",
    ) {
        dependsOn(decoderEntity)
        execute {
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
            // Up to v439 the list lives on a mutable dict hanging off the media object
            // (media.A04.A89()), so the getter is preceded by the iget-object for that field. v441
            // folded the dict into Media, so the list is fetched straight off the media object
            // (media.A8V()) and the extended-data placeholder is left alone — the extension then
            // falls back to the media object itself. Anchoring on the first List-returning virtual
            // call covers both shapes.
            fun Method.resolveMediaList(): Boolean {
                val listInvokeIndex =
                    instructions.indexOfFirst {
                        it.opcode == Opcode.INVOKE_VIRTUAL && it.methodExtractor().returnType == "java.util.List"
                    }
                if (listInvokeIndex < 0) return false

                GetMediaListExtensionFingerprint.changeFirstString(getInstruction(listInvokeIndex).methodExtractor().name)

                val fieldReadIndex = listInvokeIndex - 1
                if (fieldReadIndex >= 0 && getInstruction(fieldReadIndex).opcode == Opcode.IGET_OBJECT) {
                    GetExtendedDataExtensionFingerprint.changeFirstString(getInstruction(fieldReadIndex).fieldExtractor().name)
                }
                return true
            }

            var foundMediaListMethod = EditMediaInfoFragmentMediaSizeFingerprint.method.resolveMediaList()

            // Backup for media list extraction if the first fingerprint fails.
            if (!foundMediaListMethod) {
                foundMediaListMethod =
                    GetAndroidLinkFromMediaObject.matchOrNull()?.method?.resolveMediaList() == true
            }
            if (!foundMediaListMethod) {
                throw PatchException("Could not resolve the media list method")
            }

            // Extraction of media pkid from media class.
            FanClubContentPreviewInteractorImplFingerprint.method.apply {
                val strIndex = FanClubContentPreviewInteractorImplFingerprint.stringMatches[1].index

                val mediaPkIdMethodName = instructions[indexOfFirstInstruction(strIndex, Opcode.INVOKE_VIRTUAL)].methodExtractor().name
                GetMediaPkIdExtensionFingerprint.changeFirstString(mediaPkIdMethodName)
            }

            // Extraction of user data used in extended media class.
            GetUserDataWithoutUserSessionExtensionFingerprint.changeFirstString(LiveTreeMediaDictGetUserFingerprint.method.name)

            // Extraction of description
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
                    .first {
                        it.returnType ==
                            commentObjectClassName
                    }.name
            GetDescriptionTextExtensionFingerprint.changeFirstString(getCommentDataFromMediaMethodName)

            // Extraction of trackInfo.
            // The track container is the object the media helper builds out of a MusicInfo, so
            // anchor on that constructor call. Up to v439 this was reached indirectly through the
            // display-artist helper, but v441 moved that helper onto the container itself, so the
            // old anchor no longer exists.
            val trackContainerBuilderName =
                mutableClassDefBy { it.type == MEDIAEXT_CLASS_NAME }
                    .methods
                    .firstOrNull { method ->
                        method.implementation?.instructions?.any {
                            it.opcode == Opcode.INVOKE_DIRECT &&
                                it.getReference<MethodReference>()?.let { ref ->
                                    ref.name == "<init>" &&
                                        ref.parameterTypes.singleOrNull()?.toString() == MUSIC_INFO_CLASS
                                } == true
                        } == true
                    }?.name

            if (trackContainerBuilderName != null) {
                GetTrackDataIntfExtensionFingerprint.changeFirstString(trackContainerBuilderName)
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
                    LiveTreeMediaDictReelsMentionFingerprint.classDef.fields
                        .first { it.type == classDef.type }
                        .name
                GetMoreExtendedDataExtensionFingerprint.changeFirstString(moreExtendedMediaDataFieldName)

                // The mapper writes each json key next to the field holding its value, but whether
                // that field is read before or after the key depends on which serialiser helper
                // the key goes through, so match on the field type near the key instead of a fixed
                // offset from it.
                method.fieldNameNearString("video_versions") { it == "Ljava/util/List;" }?.let {
                    GetVideoVariantsV2ExtensionFingerprint.changeFirstString(it)
                }

                ExtMediaDictImageInfoMapperFingerprint.method
                    .fieldNameNearString("image_versions2") { it.endsWith("/ImageInfo;") }
                    ?.let { GetImageVariantsExtensionFingerprint.changeFirstString(it) }
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

/**
 * Name of the field read closest to where [value] is loaded, restricted to fields whose type
 * satisfies [matchesType]. Returns null when the string or a matching field read is absent.
 */
private fun Method.fieldNameNearString(
    value: String,
    matchesType: (String) -> Boolean,
): String? {
    val stringIndex =
        instructions.indexOfFirst {
            (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
                it.getReference<StringReference>()?.string == value
        }
    if (stringIndex < 0) return null

    return instructions
        .withIndex()
        .filter { (index, instruction) ->
            instruction.opcode == Opcode.IGET_OBJECT && abs(index - stringIndex) <= FIELD_SEARCH_WINDOW
        }.sortedBy { (index, _) -> abs(index - stringIndex) }
        .firstNotNullOfOrNull { (_, instruction) ->
            instruction.getReference<FieldReference>()?.takeIf { matchesType(it.type) }?.name
        }
}
