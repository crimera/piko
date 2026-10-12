/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.userdata

import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.entity.userfriendshipstatus.FRIENDSHIP_STATUS_CLASS
import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

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
    definingClass = USER_MODEL_CLASS_NAME,
    returnType = "Ljava/lang/String;",
    filters = listOf(string("full_name")),
)

/**
 * The username getter decodes its JSON key from a byte blob at runtime, so it is matched by the field id.
 */
internal object UserNameLiveTreeUserDictFingerprint : Fingerprint(
    definingClass = USER_MODEL_CLASS_NAME,
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        literal(-265713450),
        methodCall(name = "getOptionalStringValueNative", location = MatchAfterWithin(1)),
    ),
)

internal object FriendshipStatusLiveTreeUserDictFingerprint : Fingerprint(
    definingClass = USER_MODEL_CLASS_NAME,
    parameters = listOf(),
    returnType = FRIENDSHIP_STATUS_CLASS,
)

internal object GetUserIdExtensionFingerprint : Fingerprint(
    name = "getUserId",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object UserIdLiveTreeUserDictFingerprint : Fingerprint(
    definingClass = USER_MODEL_CLASS_NAME,
    filters = listOf(
        string("strong_id__"),
        methodCall(
            definingClass = "this",
            parameters = listOf(),
            returnType = "Ljava/lang/String;",
            opcode = Opcode.INVOKE_VIRTUAL,
            location = MatchAfterImmediately(),
        ),
    ),
)

internal object BiographyLiveTreeUserDictFingerprint : Fingerprint(
    definingClass = USER_MODEL_CLASS_NAME,
    returnType = "Ljava/lang/String;",
    filters = listOf(string("biography")),
)

internal object LowResProfilePictureUserTreeDictFingerprint : Fingerprint(
    definingClass = USER_MODEL_CLASS_NAME,
    returnType = "ImageUrl;",
    filters = listOf(string("profile_pic_url")),
)

internal object HDProfileInfoUserTreeDictFingerprint : Fingerprint(
    definingClass = USER_MODEL_CLASS_NAME,
    returnType = "ProfilePicUrlInfo",
    filters = listOf(string("hd_profile_pic_url_info")),
)

internal object IsVerifiedUserTreeDictFingerprint : Fingerprint(
    definingClass = USER_MODEL_CLASS_NAME,
    returnType = "Ljava/lang/Boolean;",
    strings = listOf("is_verified"),
    filters = listOf(literal(1565553213)),
)
