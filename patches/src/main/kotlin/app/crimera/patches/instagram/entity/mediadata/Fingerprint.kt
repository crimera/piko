/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.mediadata

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.ReelsInlineQualitySurveyRelatedFingerprint
import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.EDIT_MEDIA_INFO_FRAGMENT_CLASS
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal const val AUDIO_SRC_KEY = "audio_src"
internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/MediaData;"
internal const val VIDEO_INFO_MAPPER_KEY = "video_to_carousel_cut_info"

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

internal object GetPostTypeExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getPostType",
)

// -----------------------------------

internal object ReelsMentionDoubleTapFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("userSession", "direct_add_mention_tap"),
)

internal object InstagramMainActivityNotificationRelatedFingerprint : Fingerprint(
    definingClass = "/InstagramMainActivity;",
    strings = listOf("nme_ig_post_post_creation_notif", "nme_ig_post_story_creation_notif"),
)

internal object VideoMediaInIGTVFeedHasVideoVariantsFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf("id: ", " type: ", "InvalidVideoMediaInIGTVFeed"),
)

internal object AslSessionRelatedFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("asl_session_id", "is_video", "is_carousel"),
)

internal object EditMediaInfoFragmentMediaSizeFingerprint : Fingerprint(
    parameters = listOf(EDIT_MEDIA_INFO_FRAGMENT_CLASS),
    returnType = "F",
    definingClass = EDIT_MEDIA_INFO_FRAGMENT_CLASS,
)

// Backup fingerprint to find a media list method.
internal object GetAndroidLinkFromMediaObject : Fingerprint(
    returnType = "Lcom/instagram/model/androidlink/AndroidLink;",
    definingClass = "Lcom/instagram/profile/fragment/UserDetailFragment;",
)

internal object FanClubContentPreviewInteractorImplFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/fanclub/preview/impl/FanClubContentPreviewInteractorImpl;",
    strings = listOf("subscription_exclusive_content_public_preview_select", "creator_igid"),
)

internal object DirectShareTargetRelatedFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("", "https://www.instagram.com/p/"),
    custom = { methodDef, _ ->
        methodDef.parameters.size == 3 && methodDef.parameters.last().type == "Lcom/instagram/model/direct/DirectShareTarget;"
    },
)

internal object MusicAudioTypeEnumStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf("Landroid/content/Context;", USER_SESSION_CLASS, MEDIA_CLASS_NAME),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    filters =
        listOf(
            opcode(
                opcode = Opcode.IF_EQZ,
                location = MatchAfterWithin(4),
            ),
        ),
)

internal object AudioIntfMapperFingerprint : Fingerprint(
    returnType = "Ljava/util/Map;",
    strings = listOf(AUDIO_SRC_KEY, "audio_src_expiration_timestamp_us", "codec", "duration", "fallback", "file_format"),
)

internal object IgPlayerControllerRelatedFingerprint : Fingerprint(
    strings =
        listOf(
            "igPlayerController must be initialized",
            "audioMetadata must be set before preparing",
        ),
)

internal object ExtMediaDictVideoInfoMapperFingerprint : Fingerprint(
    strings =
        listOf(
            "video_subtitles_uri",
            VIDEO_INFO_MAPPER_KEY,
        ),
    returnType = "Ljava/util/Map;",
)

internal object LiveTreeMediaDictClinitFingerprint : Fingerprint(
    name = "<clinit>",
    strings = listOf(VIDEO_INFO_MAPPER_KEY),
)

internal object ExtMediaDictImageInfoMapperFingerprint : Fingerprint(
    strings =
        listOf(
            "igtv_shopping_info",
            "image_versions2",
        ),
    returnType = "Ljava/util/Map;",
)

internal object GetProductTileMediaFromUserSessionFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/model/shopping/productfeed/ProductTile;",
    parameters = listOf(USER_SESSION_CLASS),
    returnType = "Lcom/instagram/model/shopping/productfeed/ProductTileMedia;",
)

internal object ProductInfoMapperFingerprint : Fingerprint(
    strings =
        listOf(
            "product_suggestions",
            "product_tags",
            "product_type",
        ),
    returnType = "Ljava/util/Map;",
)

internal object AyuMidcardMediaHelperImageObjectMethodFingerprint : Fingerprint(
    definingClass = "AyuMidcardMediaHelper;",
    returnType = "Ljava/lang/Object;",
)

internal object GetOriginalSoundDataIntfFromMediaFingerprint : Fingerprint(
    classFingerprint = ReelsInlineQualitySurveyRelatedFingerprint,
    returnType = "OriginalSoundDataIntf;",
)

internal object GetUserDataFromMediaFingerprint : Fingerprint(
    classFingerprint = ReelsInlineQualitySurveyRelatedFingerprint,
    parameters = listOf(USER_SESSION_CLASS, MEDIA_CLASS_NAME),
    returnType = USER_MODEL_CLASS_NAME,
)
