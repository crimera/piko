/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.userdata

import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/UserData;"

internal object GetAdditionalUserInfoExtensionFingerprint : Fingerprint(
    name = "getAdditionalUserInfo",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetUsernameExtensionFingerprint : Fingerprint(
    name = "getUsername",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetFullNameExtensionFingerprint : Fingerprint(
    name = "getFullname",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetUserFriendshipStatusExtensionFingerprint : Fingerprint(
    name = "getUserFriendshipStatus",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetBioExtensionFingerprint : Fingerprint(
    name = "getBio",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetProfilePictureUrlExtensionFingerprint : Fingerprint(
    name = "getProfilePictureUrl",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object EditProfileNuxFragmentOnCreateFingerprint : Fingerprint(
    name = "onCreate",
    strings = listOf("arg_full_name", "arg_bio"),
)

internal object OneTapLoginUserInitFingerprint : Fingerprint(
    strings = listOf("OneTapLoginUser", "OneTapLoginUser was created w/ NULL username - should never happen."),
    returnType = "V",
    custom = { methodDef, _ ->
        methodDef.parameters.size == 2
    },
)

internal object DirectStoryViewerFragmentRelatedFingerprint : Fingerprint(
    strings =
        listOf(
            "DirectStoryViewerFragment.ARGUMENTS_THREAD_KEY",
            "card_gallery_collection_title",
            "collection_id",
            "thread_id",
            "direct_channel_audience_type",
            "DirectFragment.DIRECT_FRAGMENT_ARGUMENT_THREAD_V2_ID",
        ),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL, AccessFlags.BRIDGE, AccessFlags.SYNTHETIC),
)
