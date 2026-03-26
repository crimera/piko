/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.entity.userdata

import app.crimera.patches.instagram.utils.Constants.FRIENDSHIP_STATUS_CLASS
import app.crimera.utils.changeFirstString
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

val userDataPatch =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of the user data",
    ) {
        compatibleWith("com.instagram.android")

        execute {

            GetMatrixCursorFingerprint.method.apply {
                val additionalUserInfoField =
                    instructions.filter { it.opcode == Opcode.IGET_OBJECT }[1].fieldExtractor()

                val additionalUserInfoFieldName = additionalUserInfoField.name
                GetAdditionalUserInfoExtensionFingerprint.changeFirstString(additionalUserInfoFieldName)

                val invokeInterfaceList = instructions.filter { it.opcode == Opcode.INVOKE_INTERFACE }
                GetUsernameExtensionFingerprint.changeFirstString(invokeInterfaceList[0].methodExtractor().name)
                GetFullNameExtensionFingerprint.changeFirstString(invokeInterfaceList[1].methodExtractor().name)

                val additionalUserInfoMethods =
                    classDefBy(extensionToClassName(additionalUserInfoField.returnType))
                        .methods

                val friendshipStatusFromUserMethodName =
                    additionalUserInfoMethods
                        .first {
                            it.returnType ==
                                FRIENDSHIP_STATUS_CLASS
                        }.name

                GetUserFriendshipStatusExtensionFingerprint.changeFirstString(friendshipStatusFromUserMethodName)
            }
        }
    }
