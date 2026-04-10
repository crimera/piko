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

import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val userFriendshipStatusEntity =
    bytecodePatch(
        description = "Used to decode user friendship status",
    ) {

        execute {

            FriendshipStatusMappingsFingerprint.method.apply {
                GetMappingsFingerprint.changeFirstString(classNameToExtension(definingClass))
                GetMappingsFingerprint.changeStringAt(1, name)
            }
        }
    }
