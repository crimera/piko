/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.userdata

import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.entity.userfriendshipstatus.userFriendshipStatusEntity
import app.crimera.utils.changeFirstString
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

val userDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of the user data",
    ) {
        dependsOn(decoderEntity, userFriendshipStatusEntity)

        execute {

            fun Fingerprint.getMethodName(): String = method.name

            // Pin the getter class before any fingerprint resolves.
            userModelClass =
                if (classDefByOrNull(LIVE_TREE_USER_DICT_CLASS) != null) LIVE_TREE_USER_DICT_CLASS else USER_MODEL_CLASS_NAME

            GetUsernameExtensionFingerprint.changeFirstString(UserNameLiveTreeUserDictFingerprint.getMethodName())
            val userClass = classDefBy(USER_MODEL_CLASS_NAME)
            val idGetterName = userClass.methods.singleOrNull {
                it.name == "getId" && it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/String;"
            }?.name ?: userClass.methods.flatMap { method ->
                val code = method.implementation?.instructions?.toList().orEmpty()
                code.mapIndexedNotNull { index, instruction ->
                    if (instruction.getReference<StringReference>()?.string != "strong_id__") {
                        return@mapIndexedNotNull null
                    }
                    val call = code.getOrNull(index + 1) ?: return@mapIndexedNotNull null
                    call.getReference<MethodReference>()?.takeIf {
                        call.opcode == Opcode.INVOKE_VIRTUAL && it.definingClass == USER_MODEL_CLASS_NAME &&
                            it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/String;"
                    }?.name
                }
            }.distinct().singleOrNull() ?: throw PatchException("Expected one native user ID getter")
            if (userClass.methods.none {
                    it.name == idGetterName && it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/String;" &&
                        AccessFlags.PUBLIC.isSet(it.accessFlags) && !AccessFlags.STATIC.isSet(it.accessFlags)
                }
            ) throw PatchException("Native user ID getter is not public")
            GetUserIdExtensionFingerprint.changeFirstString(idGetterName)
            GetFullNameExtensionFingerprint.changeFirstString(FullNameLiveTreeUserDictFingerprint.getMethodName())
            GetUserFriendshipStatusExtensionFingerprint.changeFirstString(
                FriendshipStatusLiveTreeUserDictFingerprint.matchAll(1..1).single().method.name,
            )
            GetBioExtensionFingerprint.changeFirstString(BiographyLiveTreeUserDictFingerprint.getMethodName())
            GetProfilePictureUrlExtensionFingerprint.changeFirstString(HDProfileInfoUserTreeDictFingerprint.getMethodName())
            GetLowResProfilePictureExtensionFingerprint.changeFirstString(LowResProfilePictureUserTreeDictFingerprint.getMethodName())
            IsVerifiedExtensionFingerprint.changeFirstString(IsVerifiedUserTreeDictFingerprint.getMethodName())

            if (userModelClass == USER_MODEL_CLASS_NAME) {
                val userObjectField = GetAdditionalUserInfoExtensionFingerprint.classDef.fields.filter {
                    it.type == "Ljava/lang/Object;" && !AccessFlags.STATIC.isSet(it.accessFlags)
                }.singleOrNull() ?: throw PatchException("Expected one extension user object field")
                GetAdditionalUserInfoExtensionFingerprint.method.apply {
                    removeInstructions(0, instructions.size)
                    addInstructions(
                        """
                        iget-object v0, p0, $userObjectField
                        return-object v0
                        """.trimIndent(),
                    )
                }
            } else {
                val fields = classDefBy(USER_MODEL_CLASS_NAME).fields.filter {
                    it.type == userModelClass && !AccessFlags.STATIC.isSet(it.accessFlags)
                }
                val userInfoField = fields.singleOrNull()?.name
                    ?: throw PatchException("Expected one user info field, found ${fields.size}")
                GetAdditionalUserInfoExtensionFingerprint.changeFirstString(userInfoField)
            }
        }
    }
