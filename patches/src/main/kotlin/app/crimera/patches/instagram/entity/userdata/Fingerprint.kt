/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.userdata

import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.twitter.logging.responseLogging.JACKSON_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.util.getReference
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.resourceLiteral
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/UserData;"
internal const val LIVE_TREE_USER_DICT_CLASS = "Lcom/instagram/user/model/LiveTreeUserDict;"

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

internal object SelectHighlightsCoverFragmentOnCreateFingerprint : Fingerprint(
    definingClass = "SelectHighlightsCoverFragment;",
    name = "onCreate",
)

internal object FullNameLiveTreeUserDictFingerprint : Fingerprint(
    strings = listOf("full_name"),
    definingClass = LIVE_TREE_USER_DICT_CLASS,
    custom = { methodDef, _ ->
        methodDef.returnType != "V"
    },
)

internal object UserNameLiveTreeUserDictFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    definingClass = LIVE_TREE_USER_DICT_CLASS,
    filters =
        listOf(
            literal(31),
            literal(8),
            literal(0),
        ),
)

internal object FriendshipStatusLiveTreeUserDictFingerprint : Fingerprint(
    definingClass = LIVE_TREE_USER_DICT_CLASS,
    returnType = "FriendshipStatus;",
)

internal object BiographyLiveTreeUserDictFingerprint : Fingerprint(
    strings = listOf("biography"),
    definingClass = LIVE_TREE_USER_DICT_CLASS,
    returnType = "Ljava/lang/String;",
    // "biography" also occurs inside "translated_biography" and
    // "has_biography_translation", and a string match is a substring match: without
    // this the accessor that resolves is the translated one, which is null for any
    // account whose bio was never translated — i.e. almost all of them.
    custom = { methodDef, _ ->
        methodDef.implementation
            ?.instructions
            ?.any { it.getReference<StringReference>()?.string == "biography" } == true
    },
)

internal object LowResProfilePictureUserTreeDictFingerprint : Fingerprint(
    strings = listOf("profile_pic_url"),
    definingClass = LIVE_TREE_USER_DICT_CLASS,
    returnType = "ImageUrl;",
    filters =
        listOf(
            opcode(Opcode.CONST_STRING_JUMBO, InstructionLocation.MatchFirst()),
        ),
)

internal object HDProfileInfoUserTreeDictFingerprint : Fingerprint(
    strings = listOf("hd_profile_pic_url_info"),
    definingClass = LIVE_TREE_USER_DICT_CLASS,
    custom = { methodDef, _ ->
        methodDef.returnType != "V"
    },
)

internal object IsVerifiedUserTreeDictFingerprint : Fingerprint(
    definingClass = LIVE_TREE_USER_DICT_CLASS,
    strings = listOf("is_verified"),
    returnType = "Ljava/lang/Boolean;",
)
