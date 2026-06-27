/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.userProfile

import app.crimera.patches.instagram.entity.profileinfo.ProfileUserInfoViewBinderFingerprint
import app.crimera.patches.instagram.entity.profileinfo.profileInfoEntity
import app.crimera.patches.instagram.entity.userfriendshipstatus.userFriendshipStatusEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal object BindInternalBadgeFingerprint : Fingerprint(
    strings = listOf("bindInternalBadges"),
)

@Suppress("unused")
val friendshipStatusIndicatorPatch =
    bytecodePatch(
        name = "Friendship status indicator",
        description =
            "Adds a follows you back status label on the profile page and" +
                "shows a detailed friendship status breakdown on click",
    ) {

        dependsOn(settingsPatch, userFriendshipStatusEntity, profileInfoEntity)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // This constant stores the value of the obfuscated profile info class,
            // which is later used to find the index of the parameter.
            var profileInfoClassName: String

            // This fingerprint is used to identify the internal badge, which is used for displaying follow back status.
            BindInternalBadgeFingerprint.apply {

                // This is needed in order to find the profile info parameter.
                val isStaticMethod = AccessFlags.STATIC.isSet(method.accessFlags)

                method.apply {
                    val viewType = "Landroid/view/View;"

                    val internalBadgeStringIndex = BindInternalBadgeFingerprint.stringMatches[0].index
                    val profileInfoClassType = ProfileUserInfoViewBinderFingerprint.method.parameters[1].type

                    // Identify the profile info in the method parameter, which is later passed to our custom hook.
                    var profileInfoParameter = parameters.indexOfFirst { it.type == profileInfoClassType }

                    // Identify the View parameter, which contents all the elements on profile view.
                    var viewParameter = parameters.indexOfFirst { it.type == viewType }

                    // If it is not a static function, then we need to increase the parameter count by one.
                    if (!isStaticMethod) {
                        profileInfoParameter += 1
                        viewParameter += 1
                    }

                    val internalBadgeInstructionIndex =
                        indexOfFirstInstruction(internalBadgeStringIndex, Opcode.IGET_OBJECT)
                    val internalBadgeInstructionExtraction = getInstruction(internalBadgeInstructionIndex).fieldExtractor()
                    val internalBadgeDefiningClassName = extensionToClassName(internalBadgeInstructionExtraction.definingClass)
                    val internalBadgeFieldName = internalBadgeInstructionExtraction.name
                    val internalBadgeReturnType = extensionToClassName(internalBadgeInstructionExtraction.returnType)

                    // Instruction to which the call needs to transfer after our hook.
                    val moveFrom16Index =
                        indexOfFirstInstruction(internalBadgeInstructionIndex, Opcode.MOVE_OBJECT_FROM16)

                    // Added instructions:
                    // Bypass the internal badge visibility checks.
                    addInstructionsWithLabels(
                        internalBadgeInstructionIndex + 1,
                        """
                        goto :piko
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(moveFrom16Index)),
                    )

                    // Added instructions:
                    // Get the view  and check if its not null
                    // and then cast it to the profile model class*.
                    // class the indicator hook.
                    addInstructionsWithLabels(
                        0,
                        """
                        invoke-virtual/range {p$viewParameter .. p$viewParameter}, $viewType->getTag()Ljava/lang/Object;
                        move-result-object v1
                        if-eqz v1, :cond_piko
                        check-cast v1, $internalBadgeDefiningClassName
                        iget-object v2, v1, $internalBadgeDefiningClassName->$internalBadgeFieldName:$internalBadgeReturnType
                        move-object/from16 v0, p$profileInfoParameter
                        invoke-static {v0, v2}, ${PATCHES_DESCRIPTOR}/userprofile/FriendshipStatusIndicator;->indicators(Ljava/lang/Object;Ljava/lang/Object;)V
                        """.trimIndent(),
                        ExternalLabel("cond_piko", getInstruction(0)),
                    )

                    enableSettings("followBackIndicator")
                }
            }
        }
    }
