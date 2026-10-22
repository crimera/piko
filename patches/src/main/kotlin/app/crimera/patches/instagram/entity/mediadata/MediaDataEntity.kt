/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.mediadata

import app.crimera.patches.instagram.entity.decoder.MEDIAEXT_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.entity.originalSoundDataIntf.originalSoundDataIntfEntity
import app.crimera.patches.instagram.entity.trackDataIntf.trackDataIntfEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.entity.videoData.videoDataEntity
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.patches.instagram.utils.methodReference
import app.crimera.utils.changeFirstString
import app.crimera.utils.changeString
import app.crimera.utils.changeStringAt
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.matchSingle

val mediaDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of the media data",
    ) {
        dependsOn(decoderEntity, userDataEntity, videoDataEntity, originalSoundDataIntfEntity, trackDataIntfEntity)
        execute {
            GetHelperClassExtensionFingerprint.changeFirstString(classNameToExtension(MEDIAEXT_CLASS_NAME))

            // Extracting get original sound info data using media and user session.
            GetOriginalSoundDataIntfExtensionFingerprint.changeFirstString(
                GetOriginalSoundDataIntfFromMediaFingerprint.originalMethod.name,
            )

            // Extracting get user data using media and user session.
            GetUserDataWithUserSessionExtensionFingerprint.changeFirstString(
                GetUserDataFromMediaFingerprint.originalMethod.name,
            )

            // Extracting image variants list.
            GetImageVariantsExtensionFingerprint.changeStringAt(
                1,
                ImageInfoCandidatesMapperFingerprint.instructionMatches.first().methodReference.name,
            )

            // Extracting the get mention set method used media helper class.
            GetMentionSetExtensionFingerprint.changeFirstString(ReelsMentionGetterFingerprint.originalMethod.name)
            GetMentionSetExtensionFingerprint.changeStringAt(
                1,
                InstagramMainActivityNotificationRelatedFingerprint.instructionMatches[1].methodReference.name,
            )

            VideoVersionsGetterFingerprint.let {
                GetVideoVariantsV1ExtensionFingerprint.changeFirstString(it.originalMethod.name)
                GetMoreExtendedDataExtensionFingerprint.changeFirstString(it.instructionMatches[1].fieldReference.name)
                GetVideoVariantsV2ExtensionFingerprint.changeFirstString(it.instructionMatches[2].fieldReference.name)
            }

            // DASH manifest and audio flag, for downloads above the progressive 720p.
            GetVideoDashManifestExtensionFingerprint.changeFirstString(VideoDashManifestGetterFingerprint.originalMethod.name)
            HasAudioTrackExtensionFingerprint.changeFirstString(HasAudioGetterFingerprint.originalMethod.name)

            // Extracting method is video used in media class.
            IsVideoExtensionFingerprint.changeFirstString(
                MediaIsVideoFingerprint.matchSingle().instructionMatches.first().methodReference.name,
            )

            // Extraction of the media list.
            GetMediaListExtensionFingerprint.changeFirstString(
                EditMediaInfoFragmentMediaSizeFingerprint.instructionMatches.first().methodReference.name,
            )

            // Extraction of media pkid from media class.
            GetMediaPkIdExtensionFingerprint.changeFirstString(
                FanClubContentPreviewInteractorImplFingerprint.instructionMatches.last().methodReference.name,
            )

            GetTakenAtSecondsExtensionFingerprint.changeFirstString(TakenAtGetterFingerprint.originalMethod.name)

            // Extraction of user data used in extended media class.
            GetUserDataWithoutUserSessionExtensionFingerprint.changeFirstString(MediaGetUserFingerprint.originalMethod.name)

            // Extraction of description.
            CommentToStringFingerprint.let {
                GetDescriptionTextExtensionFingerprint.changeStringAt(1, it.instructionMatches.first().fieldReference.name)
                GetDescriptionTextExtensionFingerprint.changeFirstString(
                    mediaCommentGetterFingerprint(
                        it.originalClassDef.type,
                        MediaCaptionGetterFingerprint.matchSingle().originalMethod,
                    ).matchSingle().originalMethod.name,
                )
            }

            // Both metadata interfaces expose MusicInfo without requiring a user session.
            listOf("clips", "music").forEach { kind ->
                val key = "${kind}_metadata"
                val metadataGetter = mediaMetadataGetterFingerprint(key).matchSingle().originalMethod
                GetTrackDataIntfExtensionFingerprint.changeString("${kind}Metadata", metadataGetter.name)
                GetTrackDataIntfExtensionFingerprint.changeString(
                    if (kind == "clips") "clipsMusicInfo" else "musicInfo",
                    musicInfoGetterFingerprint(metadataGetter.returnType).matchSingle().originalMethod.name,
                )
            }

            // Message audio (voice notes): media -> audio interface -> url. The interface is taken
            // from the "audio_src" getter, since its name changes between builds.
            AudioIntfMapperFingerprint.instructionMatches.last().methodReference.let {
                GetMessageAudioUrlExtensionFingerprint.changeStringAt(1, it.name)
                GetMessageAudioUrlExtensionFingerprint.changeFirstString(
                    messageAudioGetterFingerprint(it.definingClass).originalMethod.name,
                )
            }

            ExtMediaDictImageInfoMapperFingerprint.instructionMatches[1].fieldReference.let {
                GetImageVariantsExtensionFingerprint.changeFirstString(it.name)
            }

            GetPostTypeKeyExtensionFingerprint.changeFirstString(
                ProductInfoMapperFingerprint.instructionMatches.last().fieldReference.name,
            )
        }
    }
