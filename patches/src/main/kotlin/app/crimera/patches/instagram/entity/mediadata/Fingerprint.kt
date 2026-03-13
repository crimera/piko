package app.crimera.patches.instagram.entity.mediadata

import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint

internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/MediaData;"

internal object GetHelperClassExtensionFingerprint: Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getHelperClass"
)

internal object GetMentionSetExtensionFingerprint: Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getMentionSet"
)

internal object GetPhotoLinkExtensionFingerprint: Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getPhotoLink"
)

internal object GetVideoLinkExtensionFingerprint: Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getVideoLink"
)

internal object IsVideoExtensionFingerprint: Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "isVideo"
)

internal object GetMediaListExtensionFingerprint: Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getMediaList"
)

internal object GetExtendedDataExtensionFingerprint: Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getExtendedData"
)

internal object GetUserDataExtensionFingerprint: Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getUserData"
)

internal object GetMediaPkIdExtensionFingerprint: Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "getMediaPkId"
)

internal object ReelsMentionDoubleTapFingerprint: Fingerprint(
    returnType = "V",
    strings = listOf("userSession","direct_add_mention_tap")
)

internal object ClipsEditMetadataControllerRunFingerprint: Fingerprint(
    returnType = "V",
    name = "run",
    strings = listOf("ClipsEditMetadataController")
)

internal object MediaUpdateFieldsFingerprint: Fingerprint(
    strings = listOf("Media#updateFields")
)

internal object AslSessionRelatedFingerprint: Fingerprint(
    returnType = "V",
    strings = listOf("asl_session_id","is_video","is_carousel")
)

internal object UserDetailFragmentGetAndroidLinkFingerprint: Fingerprint(
    returnType = "Lcom/instagram/model/androidlink/AndroidLink;",
    definingClass = "Lcom/instagram/profile/fragment/UserDetailFragment;"
)

internal object FanClubContentPreviewInteractorImplFingerprint: Fingerprint(
    definingClass = "Lcom/instagram/fanclub/preview/impl/FanClubContentPreviewInteractorImpl;",
    strings = listOf("subscription_exclusive_content_public_preview_select","creator_igid")
)

internal object DirectShareTargetRelatedFingerprint: Fingerprint (
    returnType = "V",
    strings = listOf("https://www.instagram.com/p/","unknown")
)
