/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.entity.profileinfo

import app.crimera.patches.instagram.utils.Constants.ENTITY_CLASS
import app.morphe.patcher.Fingerprint

internal const val EXTENSION_CLASS = "$ENTITY_CLASS/ProfileInfo;"
internal const val USER_DETAIL_VIEW_MODEL_CLASS = "Lcom/instagram/profile/fragment/UserDetailViewModel;"

internal object GetProfileRelatedDetailsExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "getProfileRelatedDetails",
)

internal object GetUserDetailViewModelExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "getUserDetailViewModel",
)

internal object IsSelfProfileExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "isSelfProfile",
)

internal object GetUserDataExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "getUserData",
)

// This fingerprint will also be using for follow back indicator patch && settings button && profile more options
object ProfileUserInfoViewBinderFingerprint : Fingerprint(
    strings = listOf("ProfileUserInfoViewBinder.newView"),
)

internal object ProfileRelatedDetailsFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    strings = listOf("is_self", "trigger", "content_source", "destination"),
)

internal object GetUsernameFromUserDetailViewModelFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    definingClass = USER_DETAIL_VIEW_MODEL_CLASS,
    strings = listOf("INVALID_USER_NAME"),
)
