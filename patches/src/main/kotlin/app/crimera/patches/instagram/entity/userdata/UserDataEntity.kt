/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.userdata

import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.entity.userfriendshipstatus.userFriendshipStatusEntity
import app.crimera.utils.changeFirstString
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.AccessFlags

val userDataEntity =
    patch(
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
                FriendshipStatusLiveTreeUserDictFingerprint.originalMethod.name,
            )
            GetBioExtensionFingerprint.changeFirstString(BiographyLiveTreeUserDictFingerprint.originalMethod.name)
            GetProfilePictureUrlExtensionFingerprint.changeFirstString(HDProfileInfoUserTreeDictFingerprint.originalMethod.name)
            GetLowResProfilePictureExtensionFingerprint.changeFirstString(
                LowResProfilePictureUserTreeDictFingerprint.originalMethod.name,
            )
            IsVerifiedExtensionFingerprint.changeFirstString(IsVerifiedUserTreeDictFingerprint.originalMethod.name)

            val request = ProfileUserRequestFingerprint.originalMethod
            val repository = ProfileUserRequestFingerprint.originalClassDef.staticFields.single {
                it.type == request.definingClass && AccessFlags.PUBLIC.isSet(it.accessFlags)
            }
            FetchProfileUserExtensionFingerprint.method.apply {
                removeInstructions(0, instructions.size)
                addInstructions(
                    0,
                    """
                    sget-object p0, $repository
                    invoke-virtual/range {p0 .. p2}, $request
                    move-result-object p0
                    return-object p0
                    """.trimIndent(),
                )
            }

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
