/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.profileinfo

import app.crimera.patches.instagram.utils.Constants.USER_DETAIL_VIEW_MODEL_CLASS
import app.crimera.utils.changeFirstString
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

// Field type (extension/dotted form, as returned by fieldExtractor) of the User object held
// on UserDetailViewModel and read by the username getter.
private const val USER_MODEL_CLASS_NAME = "com.instagram.user.model.User"

@Suppress("unused")
val profileInfoEntity =
    bytecodePatch(
        description = "Used to decode profile info",
    ) {

        execute {

            ProfileUserInfoViewBinderFingerprint.method.apply {
                mutableClassDefBy(parameters[1].type).apply {
                    val profileRelatedDetailsClass = ProfileRelatedDetailsFingerprint.classDef

                    val profileRelatedDetailsFieldName = fields.last { it.type == profileRelatedDetailsClass.type }.name
                    GetProfileRelatedDetailsExtensionFingerprint.changeFirstString(profileRelatedDetailsFieldName)

                    val userDetailViewModelFieldName =
                        fields
                            .last { it.type == USER_DETAIL_VIEW_MODEL_CLASS }
                            .name
                    GetUserDetailViewModelExtensionFingerprint.changeFirstString(userDetailViewModelFieldName)

                    GetUsernameFromUserDetailViewModelFingerprint.apply {
                        method.apply {
                            // The user-object field (iget-object …->A01:User) holds the User the
                            // extension reads. In v426 it appears BEFORE the "INVALID_USER_NAME"
                            // string rather than after it, so the old forward search from the
                            // string index returned -1. Locate it by its User field type instead,
                            // which is stable regardless of instruction ordering.
                            val userObjectFieldName =
                                instructions
                                    .filter { it.opcode == Opcode.IGET_OBJECT }
                                    .map { it.fieldExtractor() }
                                    .first { it.returnType == USER_MODEL_CLASS_NAME }
                                    .name
                            GetUserDataExtensionFingerprint.changeFirstString(userObjectFieldName)
                        }
                    }

                    val isSelfProfileFieldName =
                        profileRelatedDetailsClass.fields
                            .last { it.type == "Z" }
                            .name
                    IsSelfProfileExtensionFingerprint.changeFirstString(isSelfProfileFieldName)
                }
            }
        }
    }
