/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.userfriendshipstatus

import app.crimera.utils.changeString
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val userFriendshipStatusEntity =
    bytecodePatch(
        description = "Used to decode user friendship status",
    ) {

        execute {

            val mapping = FriendshipStatusMapperFingerprint.originalMethod
            GetMappingsFingerprint.changeString("classname", classNameToExtension(mapping.definingClass))
            GetMappingsFingerprint.changeString("methodname", mapping.name)
            GetMappingsFingerprint.changeString("friendshipstatusclass", classNameToExtension(FRIENDSHIP_STATUS_CLASS))
        }
    }
