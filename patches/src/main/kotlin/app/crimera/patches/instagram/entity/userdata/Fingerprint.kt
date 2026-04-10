/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.entity.userdata

import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint

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

internal object GetMatrixCursorFingerprint : Fingerprint(
    returnType = "Landroid/database/MatrixCursor;",
    definingClass = "Lcom/instagram/contentprovider/FamilyAppsUserValuesProvider;",
)
