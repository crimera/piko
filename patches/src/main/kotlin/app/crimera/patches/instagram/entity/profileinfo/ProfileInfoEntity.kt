/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.entity.profileinfo

import app.crimera.utils.changeFirstString
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

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

                    GetUsernameFromUserDetailViewModelFingerprint.method.apply {
                        val userObjectFieldName = getInstruction(indexOfFirstInstruction(Opcode.IGET_OBJECT)).fieldExtractor().name
                        GetUserDataExtensionFingerprint.changeFirstString(userObjectFieldName)
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
