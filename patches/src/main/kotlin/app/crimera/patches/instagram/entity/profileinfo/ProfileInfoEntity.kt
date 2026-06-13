/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.profileinfo

import app.crimera.utils.changeFirstString
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
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

                    val profileRelatedDetailsField = fields.lastOrNull { it.type == profileRelatedDetailsClass.type }
                        ?: throw PatchException("Failed to find profileRelatedDetailsField in ${type}")
                    GetProfileRelatedDetailsExtensionFingerprint.changeFirstString(profileRelatedDetailsField.name)

                    val userDetailViewModelField =
                        fields
                            .lastOrNull { it.type == USER_DETAIL_VIEW_MODEL_CLASS }
                            ?: throw PatchException("Failed to find userDetailViewModelField in ${type}")
                    GetUserDetailViewModelExtensionFingerprint.changeFirstString(userDetailViewModelField.name)

                    GetUsernameFromUserDetailViewModelFingerprint.method.apply {
                        val igetIndex = indexOfFirstInstruction(Opcode.IGET_OBJECT)
                        if (igetIndex < 0) throw PatchException("Failed to find IGET_OBJECT in GetUsernameFromUserDetailViewModelFingerprint")
                        val userObjectFieldName = getInstruction(igetIndex).fieldExtractor().name
                        GetUserDataExtensionFingerprint.changeFirstString(userObjectFieldName)
                    }

                    val isSelfProfileField =
                        profileRelatedDetailsClass.fields
                            .lastOrNull { it.type == "Z" }
                            ?: throw PatchException("Failed to find isSelfProfileField in ${profileRelatedDetailsClass.type}")
                    IsSelfProfileExtensionFingerprint.changeFirstString(isSelfProfileField.name)
                }
            }
        }
    }
