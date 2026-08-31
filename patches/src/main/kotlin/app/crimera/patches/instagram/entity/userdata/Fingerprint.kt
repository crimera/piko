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
import app.morphe.patcher.string
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.resourceLiteral
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/UserData;"

internal const val LIVE_TREE_USER_DICT_CLASS = "Lcom/instagram/user/model/LiveTreeUserDict;"

/**
 * Class carrying the LiveTree-backed user getters. Both candidates ship together and `User` has
 * look-alike delegates, so `userDataEntity` pins one before these resolve.
 */
internal var userModelClass: String = LIVE_TREE_USER_DICT_CLASS

private fun Method.inUserModel(): Boolean = definingClass == userModelClass

private const val GET_OPTIONAL_STRING_VALUE_NATIVE = "getOptionalStringValueNative"

/**
 * True for the `username` getter, which — unlike its siblings — never spells its JSON key out as a
 * `const-string`; it decodes it from a byte blob at runtime.
 *
 * The field id alone can match several String-returning methods, so also require that the method
 * reads a LiveTree string value and carries no literal key — that leaves exactly one.
 */
private fun Method.readsLiveTreeStringWithoutLiteralKey(): Boolean {
    val instructions = implementation?.instructions ?: return false
    var readsLiveTreeString = false
    for (instruction in instructions) {
        when {
            instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO -> return false
            instruction is ReferenceInstruction &&
                (instruction.reference as? MethodReference)?.name == GET_OPTIONAL_STRING_VALUE_NATIVE ->
                readsLiveTreeString = true
        }
    }
    return readsLiveTreeString
}

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
    custom = { methodDef, _ -> methodDef.inUserModel() },
    filters =
        listOf(
            string("full_name"),
        ),
    returnType = "Ljava/lang/String;",
)

internal object UserNameLiveTreeUserDictFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    custom = { methodDef, _ ->
        methodDef.inUserModel() && methodDef.readsLiveTreeStringWithoutLiteralKey()
    },
    filters =
        listOf(
            literal(-265713450),
        ),
)

internal object FriendshipStatusLiveTreeUserDictFingerprint : Fingerprint(
    custom = { methodDef, _ -> methodDef.inUserModel() },
    returnType = "FriendshipStatus;",
)

internal object BiographyLiveTreeUserDictFingerprint : Fingerprint(
    custom = { methodDef, _ -> methodDef.inUserModel() },
    returnType = "Ljava/lang/String;",
    filters =
        listOf(
            string("biography"),
        ),
)

internal object LowResProfilePictureUserTreeDictFingerprint : Fingerprint(
    custom = { methodDef, _ -> methodDef.inUserModel() },
    returnType = "ImageUrl;",
    filters =
        listOf(
            string("profile_pic_url"),
        ),
)

internal object HDProfileInfoUserTreeDictFingerprint : Fingerprint(
    custom = { methodDef, _ -> methodDef.inUserModel() },
    filters =
        listOf(
            string("hd_profile_pic_url_info"),
        ),
    returnType = "ProfilePicUrlInfo",
)

internal object IsVerifiedUserTreeDictFingerprint : Fingerprint(
    strings = listOf("is_verified"),
    returnType = "Ljava/lang/Boolean;",
    custom = { methodDef, _ -> methodDef.inUserModel() },
    filters =
        listOf(
            literal(1565553213),
        ),
)
