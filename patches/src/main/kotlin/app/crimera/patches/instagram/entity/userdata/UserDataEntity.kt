/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.userdata

import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.entity.userfriendshipstatus.userFriendshipStatusEntity
import app.crimera.utils.changeFirstString
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch

val userDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of the user data",
    ) {
        dependsOn(decoderEntity, userFriendshipStatusEntity)

        execute {
            fun Fingerprint.getMethodName(): String = method.name

            GetUsernameExtensionFingerprint.changeFirstString(UserNameLiveTreeUserDictFingerprint.getMethodName())
            GetFullNameExtensionFingerprint.changeFirstString(FullNameLiveTreeUserDictFingerprint.getMethodName())
            GetUserFriendshipStatusExtensionFingerprint.changeFirstString(FriendshipStatusLiveTreeUserDictFingerprint.getMethodName())
            GetBioExtensionFingerprint.changeFirstString(BiographyLiveTreeUserDictFingerprint.getMethodName())
            GetProfilePictureUrlExtensionFingerprint.changeFirstString(HDProfileInfoUserTreeDictFingerprint.getMethodName())
            GetLowResProfilePictureExtensionFingerprint.changeFirstString(LowResProfilePictureUserTreeDictFingerprint.getMethodName())
            IsVerifiedExtensionFingerprint.changeFirstString(IsVerifiedUserTreeDictFingerprint.getMethodName())
        }
    }
