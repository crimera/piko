/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.mediadata

import app.crimera.patches.instagram.entity.decoder.MEDIAEXT_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.EDIT_MEDIA_INFO_FRAGMENT_CLASS
import app.crimera.patches.instagram.utils.Constants.MUSIC_INFO_CLASS
import app.crimera.patches.instagram.utils.Constants.ORIGINAL_SOUND_DATA_INTF
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val AUDIO_SRC_KEY = "audio_src"
internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/MediaData;"
internal object GetHelperClassExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getHelperClass",
)

internal object GetMentionSetExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getMentionSet",
)

internal object GetImageVariantsExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getImageVariants",
)

internal object GetVideoVariantsV1ExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getVideoVariantsV1",
)

internal object GetVideoVariantsV2ExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getVideoVariantsV2",
)

internal object IsVideoExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "isVideo",
)

internal object GetMediaListExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getMediaList",
)

internal object GetExtendedDataExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getExtendedData",
)

internal object GetUserDataWithoutUserSessionExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getUserDataWithoutUserSession",
)

internal object GetUserDataWithUserSessionExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getUserDataWithUserSession",
)

internal object GetMediaPkIdExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getMediaPkId",
)

internal object GetTakenAtSecondsExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getTakenAtSeconds",
)

internal object GetDescriptionTextExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getDescriptionText",
)

internal object GetOriginalSoundDataIntfExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getOriginalSoundDataIntf",
)

internal object GetTrackDataIntfExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getTrackDataIntf",
)

internal object GetMessageAudioUrlExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getMessageAudioUrl",
)

internal object GetMoreExtendedDataExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getMoreExtendedData",
)

internal object GetPostTypeKeyExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getPostTypeKey",
)

// -----------------------------------

internal object InstagramMainActivityNotificationRelatedFingerprint : Fingerprint(
    definingClass = "/InstagramMainActivity;",
    filters = listOf(
        string("nme_ig_post_post_creation_notif"),
        // Mentioned user.
        methodCall(returnType = USER_MODEL_CLASS_NAME, parameters = listOf(), opcode = Opcode.INVOKE_INTERFACE),
        string("nme_ig_post_story_creation_notif"),
    ),
)

internal object MediaIsVideoFingerprint : Fingerprint(
    parameters = listOf(MEDIA_CLASS_NAME),
    returnType = "V",
    strings = listOf("is_carousel"),
    filters = listOf(
        methodCall(definingClass = MEDIA_CLASS_NAME, parameters = listOf(), returnType = "Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        string("is_video", location = MatchAfterImmediately()),
    ),
)

internal object EditMediaInfoFragmentMediaSizeFingerprint : Fingerprint(
    parameters = listOf(EDIT_MEDIA_INFO_FRAGMENT_CLASS),
    returnType = "F",
    definingClass = EDIT_MEDIA_INFO_FRAGMENT_CLASS,
    filters = listOf(
        // Carousel media list.
        methodCall(definingClass = MEDIA_CLASS_NAME, parameters = listOf(), returnType = "Ljava/util/List;"),
    ),
)

internal object FanClubContentPreviewInteractorImplFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/fanclub/preview/impl/FanClubContentPreviewInteractorImpl;",
    strings = listOf("subscription_exclusive_content_public_preview_select"),
    filters = listOf(
        string("creator_igid"),
        // Media pk id.
        methodCall(definingClass = MEDIA_CLASS_NAME, returnType = "Ljava/lang/String;"),
    ),
)

internal object AudioIntfMapperFingerprint : Fingerprint(
    returnType = "Ljava/util/Map;",
    strings = listOf("audio_src_expiration_timestamp_us", "codec", "duration", "fallback", "file_format"),
    filters = listOf(
        string(AUDIO_SRC_KEY),
        // Audio URL of the audio interface.
        methodCall(returnType = "Ljava/lang/String;", opcode = Opcode.INVOKE_INTERFACE, location = MatchAfterImmediately()),
    ),
)

/**
 * Media's getter of a value of its media dict, under the dict key: it returns the dict's cached
 * field, or reads the value from the native tree when that field is still null.
 */
internal fun mediaDictGetterFingerprint(key: String, type: String) = Fingerprint(
    definingClass = MEDIA_CLASS_NAME,
    returnType = type,
    parameters = listOf(),
    filters = listOf(
        string(key),
        // Extended media data.
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(type = type, opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
    ),
)

internal val VideoVersionsGetterFingerprint = mediaDictGetterFingerprint("video_versions", "Ljava/util/List;")

internal val VideoDashManifestGetterFingerprint = mediaDictGetterFingerprint("video_dash_manifest", "Ljava/lang/String;")

/** Media's has_audio getter: the same shape, except its key is loaded after the two reads. */
internal object HasAudioGetterFingerprint : Fingerprint(
    definingClass = MEDIA_CLASS_NAME,
    returnType = "Ljava/lang/Boolean;",
    parameters = listOf(),
    filters = listOf(
        // Extended media data.
        fieldAccess(opcode = Opcode.IGET_OBJECT),
        fieldAccess(type = "Ljava/lang/Boolean;", opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        string("has_audio"),
    ),
)

internal object GetVideoDashManifestExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getVideoDashManifest",
)

internal object HasAudioTrackExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "hasAudioTrack",
)

internal object TakenAtGetterFingerprint : Fingerprint(
    definingClass = MEDIA_CLASS_NAME,
    returnType = "Ljava/lang/Long;",
    parameters = listOf(),
    strings = listOf("taken_at"),
)

internal object ReelsMentionGetterFingerprint : Fingerprint(
    definingClass = MEDIA_CLASS_NAME,
    returnType = "Ljava/util/List;",
    strings = listOf("reel_mentions"),
)

internal object MediaGetUserFingerprint : Fingerprint(
    definingClass = MEDIA_CLASS_NAME,
    returnType = USER_MODEL_CLASS_NAME,
    strings = listOf("user"),
)

internal object ExtMediaDictImageInfoMapperFingerprint : Fingerprint(
    returnType = "Ljava/util/Map;",
    filters = listOf(
        string("igtv_shopping_info"),
        fieldAccess(type = "Lcom/instagram/model/mediasize/ImageInfo;", opcode = Opcode.IGET_OBJECT),
        string("image_versions2"),
    ),
)

internal object ProductInfoMapperFingerprint : Fingerprint(
    returnType = "Ljava/util/Map;",
    strings = listOf("product_suggestions", "product_tags"),
    filters = listOf(
        string("product_type"),
        fieldAccess(type = "Ljava/lang/String;", opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
    ),
)

internal object ImageInfoCandidatesMapperFingerprint : Fingerprint(
    parameters = listOf("Lcom/instagram/model/mediasize/ImageInfo;"),
    returnType = "Ljava/util/Map;",
    strings = listOf("additional_candidates"),
    filters = listOf(
        // Image candidates.
        methodCall(definingClass = "Lcom/instagram/model/mediasize/ImageInfo;", parameters = listOf(), returnType = "Ljava/util/List;"),
        string("candidates"),
    ),
)

internal object GetOriginalSoundDataIntfFromMediaFingerprint : Fingerprint(
    definingClass = MEDIAEXT_CLASS_NAME,
    returnType = ORIGINAL_SOUND_DATA_INTF,
)

internal object GetUserDataFromMediaFingerprint : Fingerprint(
    definingClass = MEDIAEXT_CLASS_NAME,
    parameters = listOf(USER_SESSION_CLASS, MEDIA_CLASS_NAME),
    returnType = USER_MODEL_CLASS_NAME,
)

internal object CommentToStringFingerprint : Fingerprint(
    name = "toString",
    filters = listOf(
        // Comment text.
        fieldAccess(definingClass = "this", type = "Ljava/lang/String;", opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.FILLED_NEW_ARRAY, location = MatchAfterWithin(1)),
        string("Comment{mCreatedAtSeconds=%d, mUser=@%s, mText=\'%s\'}"),
    ),
)

/**
 * Media helper method returning the comment of a media.
 */
internal object MediaCaptionGetterFingerprint : Fingerprint(
    definingClass = MEDIA_CLASS_NAME,
    parameters = emptyList(),
    returnType = "L",
    filters = listOf(string("caption")),
)

internal fun mediaCommentGetterFingerprint(commentClass: String, captionGetter: MethodReference) = Fingerprint(
    definingClass = MEDIAEXT_CLASS_NAME,
    parameters = listOf(MEDIA_CLASS_NAME),
    returnType = commentClass,
    filters = listOf(methodCall(reference = captionGetter)),
)

internal fun mediaMetadataGetterFingerprint(key: String) = Fingerprint(
    definingClass = MEDIA_CLASS_NAME,
    parameters = emptyList(),
    returnType = "L",
    strings = listOf(key),
)

internal fun musicInfoGetterFingerprint(metadataClass: String) = Fingerprint(
    definingClass = metadataClass,
    parameters = emptyList(),
    returnType = MUSIC_INFO_CLASS,
)

/**
 * Media getter of the audio interface.
 */
internal fun messageAudioGetterFingerprint(audioInterface: String) = Fingerprint(
    definingClass = MEDIA_CLASS_NAME,
    parameters = listOf(),
    returnType = audioInterface,
)
