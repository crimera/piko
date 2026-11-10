/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.profileinfo

import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.utils.Constants.USER_DETAIL_VIEW_MODEL_CLASS
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.utils.changeFirstString
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.AccessFlags

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
                    val userFields = viewModel.fields.filter {
                        it.type == USER_MODEL_CLASS_NAME && !AccessFlags.STATIC.isSet(it.accessFlags)
                    }
                    val userObjectFieldName = userFields.singleOrNull()?.name
                        ?: throw PatchException("Expected one profile user field, found ${userFields.size}")
                    GetUserDataExtensionFingerprint.changeFirstString(userObjectFieldName)

                    val isSelfProfileFieldName =
                        profileRelatedDetailsClass.fields
                            .last { it.type == "Z" }
                            .name
                    IsSelfProfileExtensionFingerprint.changeFirstString(isSelfProfileFieldName)
                }
            }
        }
    }
