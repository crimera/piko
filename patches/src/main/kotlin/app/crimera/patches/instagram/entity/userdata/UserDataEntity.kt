/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
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
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

val userDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of the user data",
    ) {
        execute {
            OneTapLoginUserInitFingerprint.method.apply {
                val additionalUserInfoField =
                    instructions.first { it.opcode == Opcode.IGET_OBJECT }.fieldExtractor()

                val additionalUserInfoFieldName = additionalUserInfoField.name
                GetAdditionalUserInfoExtensionFingerprint.changeFirstString(additionalUserInfoFieldName)

                val firstInvokeInterface = instructions.first { it.opcode == Opcode.INVOKE_INTERFACE }
                GetUsernameExtensionFingerprint.changeFirstString(firstInvokeInterface.methodExtractor().name)

                val lastInvokeInterface = instructions.last { it.opcode == Opcode.INVOKE_INTERFACE }
                GetFullNameExtensionFingerprint.changeFirstString(lastInvokeInterface.methodExtractor().name)

                val additionalUserInfoMethods =
                    mutableClassDefBy(extensionToClassName(additionalUserInfoField.returnType))
                        .methods

                val friendshipStatusFromUserMethodName =
                    additionalUserInfoMethods
                        .first {
                            it.returnType ==
                                FRIENDSHIP_STATUS_CLASS &&
                                it.parameters.isEmpty()
                        }.name

                GetUserFriendshipStatusExtensionFingerprint.changeFirstString(friendshipStatusFromUserMethodName)
            }

            DirectStoryViewerFragmentRelatedFingerprint.method.apply {
                val firstInvokeInterface = instructions.first { it.opcode == Opcode.INVOKE_INTERFACE }.methodExtractor().name
                GetProfilePictureUrlExtensionFingerprint.changeFirstString(firstInvokeInterface)
            }

            EditProfileNuxFragmentOnCreateFingerprint.apply {
                val strIndex = stringMatches[1].index
                method.apply {
                    val firstInvokeInterfaceAfterStrIndex = indexOfFirstInstruction(strIndex, Opcode.INVOKE_INTERFACE)
                    val bioMethod = getInstruction(firstInvokeInterfaceAfterStrIndex).methodExtractor().name
                    GetBioExtensionFingerprint.changeFirstString(bioMethod)
                }
            }
        }
    }
