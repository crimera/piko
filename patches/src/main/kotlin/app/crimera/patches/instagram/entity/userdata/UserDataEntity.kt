/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.userdata

import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.utils.Constants.FRIENDSHIP_STATUS_CLASS
import app.crimera.utils.changeFirstString
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.crimera.utils.getReference
import app.crimera.utils.methodExtractor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedMethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference

val userDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of the user data",
    ) {
        dependsOn(decoderEntity)

        execute {

            fun Fingerprint.getMethodName(): String = method.name

            GetUsernameExtensionFingerprint.changeFirstString(UserNameLiveTreeUserDictFingerprint.getMethodName())
            GetFullNameExtensionFingerprint.changeFirstString(FullNameLiveTreeUserDictFingerprint.getMethodName())
            GetUserFriendshipStatusExtensionFingerprint.changeFirstString(FriendshipStatusLiveTreeUserDictFingerprint.getMethodName())
            GetBioExtensionFingerprint.changeFirstString(BiographyLiveTreeUserDictFingerprint.getMethodName())
            GetProfilePictureUrlExtensionFingerprint.changeFirstString(HDProfileInfoUserTreeDictFingerprint.getMethodName())
            GetLowResProfilePictureExtensionFingerprint.changeFirstString(LowResProfilePictureUserTreeDictFingerprint.getMethodName())
            IsVerifiedExtensionFingerprint.changeFirstString(IsVerifiedUserTreeDictFingerprint.getMethodName())

            SelectHighlightsCoverFragmentOnCreateFingerprint.method.apply {
                val firstIGetObjectIndex = indexOfFirstInstruction(Opcode.IGET_OBJECT)
                val mutableUserDictIntfFieldName = getInstruction(firstIGetObjectIndex).fieldExtractor().name
                GetAdditionalUserInfoExtensionFingerprint.changeFirstString(mutableUserDictIntfFieldName)
            }
        }
    }
