/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.userfriendshipstatus

import app.crimera.utils.changeString
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

internal lateinit var friendshipStatusClass: String

@Suppress("unused")
val userFriendshipStatusEntity =
    bytecodePatch(
        description = "Used to decode user friendship status",
    ) {

        execute {

            val mappings = classDefByStrings("followed_by").flatMap { it.methods }
                .filter { it.friendshipStatusParameterOrNull() != null }
            val mapping = mappings.singleOrNull()
                ?: throw PatchException("Expected one friendship status mapping method, found ${mappings.size}")
            friendshipStatusClass = mapping.friendshipStatusParameterOrNull()!!
            if (!AccessFlags.INTERFACE.isSet(classDefBy(friendshipStatusClass).accessFlags)) {
                throw PatchException("Expected a friendship status interface")
            }
            GetMappingsFingerprint.changeString("classname", classNameToExtension(mapping.definingClass))
            GetMappingsFingerprint.changeString("methodname", mapping.name)
            GetMappingsFingerprint.changeString("friendshipstatusclass", classNameToExtension(friendshipStatusClass))
        }
    }
