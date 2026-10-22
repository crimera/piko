/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.userdata

import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.entity.userfriendshipstatus.userFriendshipStatusEntity
import app.crimera.patches.instagram.utils.methodReference
import app.crimera.utils.changeFirstString
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.matchSingle
import com.android.tools.smali.dexlib2.AccessFlags

val userDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of the user data",
    ) {
        dependsOn(decoderEntity, userFriendshipStatusEntity)

        execute {
            GetUsernameExtensionFingerprint.changeFirstString(UserNameLiveTreeUserDictFingerprint.originalMethod.name)
            GetUserIdExtensionFingerprint.changeFirstString(
                UserIdLiveTreeUserDictFingerprint.instructionMatches.last().methodReference.name,
            )
            GetFullNameExtensionFingerprint.changeFirstString(FullNameLiveTreeUserDictFingerprint.originalMethod.name)
            GetUserFriendshipStatusExtensionFingerprint.changeFirstString(
                FriendshipStatusLiveTreeUserDictFingerprint.matchSingle().originalMethod.name,
            )
            GetBioExtensionFingerprint.changeFirstString(BiographyLiveTreeUserDictFingerprint.originalMethod.name)
            GetProfilePictureUrlExtensionFingerprint.changeFirstString(HDProfileInfoUserTreeDictFingerprint.originalMethod.name)
            GetLowResProfilePictureExtensionFingerprint.changeFirstString(
                LowResProfilePictureUserTreeDictFingerprint.originalMethod.name,
            )
            IsVerifiedExtensionFingerprint.changeFirstString(IsVerifiedUserTreeDictFingerprint.originalMethod.name)

            // The user object itself carries the additional user info.
            GetAdditionalUserInfoExtensionFingerprint.method.apply {
                val userObjectField = GetAdditionalUserInfoExtensionFingerprint.classDef.fields.single {
                    it.type == "Ljava/lang/Object;" && !AccessFlags.STATIC.isSet(it.accessFlags)
                }
                removeInstructions(0, instructions.size)
                addInstructions(
                    0,
                    """
                    iget-object v0, p0, $userObjectField
                    return-object v0
                    """.trimIndent(),
                )
            }
        }
    }
