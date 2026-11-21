/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.profileinfo

import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.utils.Constants.USER_DETAIL_VIEW_MODEL_CLASS
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.utils.changeFirstString
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.patch

@Suppress("unused")
val profileInfoEntity =
    patch(
        description = "Used to decode profile info",
    ) {
        dependsOn(decoderEntity)

        execute {

            ProfileUserInfoViewBinderFingerprint.originalMethod.apply {
                classDefBy(parameterTypes[1].toString()).apply {
                    val profileRelatedDetailsClass = ProfileRelatedDetailsFingerprint.originalClassDef

                    val profileRelatedDetailsFieldName = fields.last { it.type == profileRelatedDetailsClass.type }.name
                    GetProfileRelatedDetailsExtensionFingerprint.changeFirstString(profileRelatedDetailsFieldName)

                    val userDetailViewModelFieldName =
                        fields
                            .last { it.type == USER_DETAIL_VIEW_MODEL_CLASS }
                            .name
                    GetUserDetailViewModelExtensionFingerprint.changeFirstString(userDetailViewModelFieldName)

                    val viewModel = classDefBy(USER_DETAIL_VIEW_MODEL_CLASS)
                    val session = viewModel.instanceFields.single { it.type == USER_SESSION_CLASS }
                    GetProfileSessionExtensionFingerprint.changeFirstString(session.name)

                    val isSelfProfileFieldName =
                        profileRelatedDetailsClass.fields
                            .last { it.type == "Z" }
                            .name
                    IsSelfProfileExtensionFingerprint.changeFirstString(isSelfProfileFieldName)
                }
            }

            val profileRead = ProfileUserIdFingerprint.instructionMatches
            val state = profileRead[0].fieldReference
            val getValue = profileRead[1].methodReference
            val user = profileRead[5].fieldReference
            GetProfileUserExtensionFingerprint.method.apply {
                removeInstructions(0, instructions.size)
                addInstructions(
                    0,
                    """
                    if-eqz p0, :done
                    check-cast p0, $USER_DETAIL_VIEW_MODEL_CLASS
                    iget-object p0, p0, $state
                    invoke-interface {p0}, $getValue
                    move-result-object p0
                    if-eqz p0, :done
                    check-cast p0, ${user.definingClass}
                    iget-object p0, p0, $user
                    :done
                    return-object p0
                    """.trimIndent(),
                )
            }
        }
    }
