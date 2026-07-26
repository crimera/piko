/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.userProfileActionBarButton

import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.USER_DETAIL_VIEW_MODEL_CLASS
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.utils.methodExtractor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableField
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal object ProfileActionBarRelatedFingerprint : Fingerprint(
    strings = listOf("notifications_entry_point_impression", "impression_cast_to_tv"),
    returnType = "V",
)

internal object ProfileHeaderRelatedFingerprint : Fingerprint(
    strings = listOf("profile_user_id", "click_point", "user_profile_header"),
    returnType = "V",
    custom = { methodDef, _ ->
        methodDef.parameters.size == 3
    },
)

internal object ProfileActionBarFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/profile/actionbar/ProfileActionBar;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
)

val userProfileActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on user profile action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(decoderEntity)

        execute {

            val actionBarRelatedClass: String
            val profileHeaderFieldInActionBarRelatedClass: MutableField

            ProfileActionBarRelatedFingerprint.apply {
                actionBarRelatedClass = classDef.type
                profileHeaderFieldInActionBarRelatedClass =
                    classDef.fields.first { it.type == ProfileHeaderRelatedFingerprint.classDef.type }
            }

            val userDetailViewModelFieldInProfileHeaderRelatedClass: MutableField =
                ProfileHeaderRelatedFingerprint.classDef.fields.first {
                    it.type == USER_DETAIL_VIEW_MODEL_CLASS
                }

            val userDetailsClassFields = classDefBy { it.type == USER_DETAIL_VIEW_MODEL_CLASS }.fields

            val userDataFieldInUserDetailClass = userDetailsClassFields.first { it.type == USER_MODEL_CLASS_NAME }

            ProfileActionBarFingerprint.method.apply {
                val actionBarRelatedObjectParameterRegister =
                    parameters.indexOfFirst {
                        it.type ==
                            ProfileActionBarRelatedFingerprint.classDef.type
                    } + 1
                val userSessionParameterRegister = parameters.indexOfFirst { it.type == USER_SESSION_CLASS }

                val profileActionBarIconInjectMethodIndex = instructions.last { it.opcode == Opcode.INVOKE_STATIC_RANGE }.location.index
                val layoutRegister = getInstruction(profileActionBarIconInjectMethodIndex - 1).registersUsed[0]
                val nextReturnVoidIndex = indexOfFirstInstruction(profileActionBarIconInjectMethodIndex, Opcode.RETURN_VOID)
                val freeRegister = 3
                val freeRegister2 = 4
                val freeRegister3 = 5
                val CODE =
                    """
                    move-object/from16 v$freeRegister, p$actionBarRelatedObjectParameterRegister
                            if-eqz v$actionBarRelatedObjectParameterRegister, :piko
                            iget-object v$freeRegister, v$actionBarRelatedObjectParameterRegister, $profileHeaderFieldInActionBarRelatedClass
                            
                            if-eqz v$freeRegister, :piko
                            iget-object v$freeRegister,v$freeRegister, $userDetailViewModelFieldInProfileHeaderRelatedClass
                            
                            if-eqz v$freeRegister, :piko
                            iget-object v$freeRegister,v$freeRegister, $userDataFieldInUserDetailClass
                            move-object/from16 v$freeRegister2, p$userSessionParameterRegister
                            move-object/from16 v$freeRegister3, v$layoutRegister
                            invoke-static {v$freeRegister3, v$freeRegister2, v$freeRegister}, $ACTIONBAR_DESCRIPTOR->userProfileActionBarButton(Landroid/view/ViewGroup;${USER_SESSION_CLASS}Ljava/lang/Object;)V
                            return-void
                    """.trimIndent()

                addInstructionsWithLabels(
                    profileActionBarIconInjectMethodIndex + 1,
                    CODE,
                    ExternalLabel("piko", getInstruction(nextReturnVoidIndex)),
                )

                addFlags("profileActionBarFlags")
            }
        }
    }
