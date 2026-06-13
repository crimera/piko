/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.userdata

import app.crimera.patches.instagram.utils.Constants.FRIENDSHIP_STATUS_CLASS
import app.crimera.utils.changeFirstString
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

val userDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of the user data",
    ) {
        execute {

            GetMatrixCursorFingerprint.method.apply {
                val igetObjects = instructions.filter { it.opcode == Opcode.IGET_OBJECT }
                if (igetObjects.size < 2) throw PatchException("Failed to find enough IGET_OBJECT in GetMatrixCursorFingerprint")
                val additionalUserInfoField = igetObjects[1].fieldExtractor()

                val additionalUserInfoFieldName = additionalUserInfoField.name
                GetAdditionalUserInfoExtensionFingerprint.changeFirstString(additionalUserInfoFieldName)

                val invokeInterfaceList = instructions.filter { it.opcode == Opcode.INVOKE_INTERFACE }
                if (invokeInterfaceList.size < 2) throw PatchException("Failed to find enough INVOKE_INTERFACE in GetMatrixCursorFingerprint")
                GetUsernameExtensionFingerprint.changeFirstString(invokeInterfaceList[0].methodExtractor().name)
                GetFullNameExtensionFingerprint.changeFirstString(invokeInterfaceList[1].methodExtractor().name)

                val additionalUserInfoMethods =
                    mutableClassDefBy(extensionToClassName(additionalUserInfoField.returnType))
                        .methods

                val friendshipStatusFromUserMethod =
                    additionalUserInfoMethods
                        .firstOrNull {
                            it.returnType ==
                                FRIENDSHIP_STATUS_CLASS &&
                                it.parameters.isEmpty()
                        } ?: throw PatchException("Failed to find friendshipStatusFromUserMethod in ${additionalUserInfoField.returnType}")
                GetUserFriendshipStatusExtensionFingerprint.changeFirstString(friendshipStatusFromUserMethod.name)

                val profilePicUrlInfoMethod =
                    additionalUserInfoMethods
                        .firstOrNull {
                            it.returnType == "Lcom/instagram/api/schemas/ProfilePicUrlInfo;"
                        } ?: throw PatchException("Failed to find profilePicUrlInfoMethod in ${additionalUserInfoField.returnType}")
                GetProfilePictureUrlExtensionFingerprint.changeFirstString(profilePicUrlInfoMethod.name)
            }

            EditProfileNuxFragmentOnCreateFingerprint.apply {
                if (stringMatches.size < 2) throw PatchException("Failed to find enough string matches in EditProfileNuxFragmentOnCreateFingerprint")
                val strIndex = stringMatches[1].index
                method.apply {
                    val firstInvokeInterfaceIndex = indexOfFirstInstruction(strIndex, Opcode.INVOKE_INTERFACE)
                    if (firstInvokeInterfaceIndex < 0) throw PatchException("Failed to find INVOKE_INTERFACE after string in EditProfileNuxFragmentOnCreateFingerprint")
                    val bioMethod = getInstruction(firstInvokeInterfaceIndex).methodExtractor().name
                    GetBioExtensionFingerprint.changeFirstString(bioMethod)
                }
            }
        }
    }
