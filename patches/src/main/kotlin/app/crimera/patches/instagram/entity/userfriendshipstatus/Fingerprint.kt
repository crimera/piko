/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.entity.userfriendshipstatus

import app.crimera.patches.instagram.utils.Constants.ENTITY_CLASS
import app.crimera.patches.instagram.utils.Constants.FRIENDSHIP_STATUS_CLASS
import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal const val EXTENSION_CLASS = "$ENTITY_CLASS/UserFriendshipStatus;"

internal object GetMappingsFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "getMappings",
)

internal object FriendshipStatusMappingsFingerprint : Fingerprint(
    returnType = "Ljava/util/Map;",
    parameters = listOf(FRIENDSHIP_STATUS_CLASS),
    accessFlags = listOf(AccessFlags.STATIC, AccessFlags.PUBLIC),
)
