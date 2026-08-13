/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.userdata

import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import app.morphe.patcher.string

internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/UserData;"
internal const val USER_CLASS = "Lcom/instagram/user/model/User;"

internal object GetAdditionalUserInfoExtensionFingerprint : Fingerprint(
    name = "getAdditionalUserInfo",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetUsernameExtensionFingerprint : Fingerprint(
    name = "getUsername",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetFullNameExtensionFingerprint : Fingerprint(
    name = "getFullName",
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

internal object GetLowResProfilePictureExtensionFingerprint : Fingerprint(
    name = "getLowResProfilePicture",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object IsVerifiedExtensionFingerprint : Fingerprint(
    name = "isVerified",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

// -----------------------------------

internal object FullNameLiveTreeUserDictFingerprint : Fingerprint(
    definingClass = USER_CLASS,
    filters = listOf(string("full_name")),
    returnType = "Ljava/lang/String;",
)

internal object UserNameLiveTreeUserDictFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    definingClass = USER_CLASS,
    filters = listOf(literal(-265713450)),
)

internal object FriendshipStatusLiveTreeUserDictFingerprint : Fingerprint(
    definingClass = USER_CLASS,
    returnType = "FriendshipStatus;",
)

internal object BiographyLiveTreeUserDictFingerprint : Fingerprint(
    definingClass = USER_CLASS,
    returnType = "Ljava/lang/String;",
    filters = listOf(string("biography")),
)

internal object LowResProfilePictureUserTreeDictFingerprint : Fingerprint(
    definingClass = USER_CLASS,
    returnType = "ImageUrl;",
    filters = listOf(string("profile_pic_url")),
)

internal object HDProfileInfoUserTreeDictFingerprint : Fingerprint(
    definingClass = USER_CLASS,
    filters = listOf(string("hd_profile_pic_url_info")),
    returnType = "ProfilePicUrlInfo",
)

internal object IsVerifiedUserTreeDictFingerprint : Fingerprint(
    definingClass = USER_CLASS,
    strings = listOf("is_verified"),
    returnType = "Ljava/lang/Boolean;",
    filters = listOf(literal(1565553213)),
)
