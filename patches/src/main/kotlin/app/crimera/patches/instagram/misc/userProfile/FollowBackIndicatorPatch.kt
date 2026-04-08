/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.userProfile

import app.crimera.patches.instagram.entity.profileinfo.ProfileUserInfoViewBinderFingerprint
import app.crimera.patches.instagram.entity.profileinfo.profileInfoEntity
import app.crimera.patches.instagram.entity.userfriendshipstatus.userFriendshipStatusEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal object BindInternalBadgeFingerprint : Fingerprint(
    strings = listOf("bindInternalBadges"),
)

@Suppress("unused")
val followBackIndicatorPatch =
    bytecodePatch(
        name = "Follow back indicator",
        description = "Adds a label on the profile page, indicating whether a user is follows you back.",
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
                    val internalBadgeStringIndex = BindInternalBadgeFingerprint.stringMatches[0].index
                    val profileInfoClassType = ProfileUserInfoViewBinderFingerprint.method.parameters[1].type

                    // Identify the profile info in the method parameter, which is later passed to our custom hook.
                    var profileInfoParameter = parameters.indexOfFirst { it.type == profileInfoClassType }

                    // If it is not a static function, then we need to increase the parameter count by one.
                    if (!isStaticMethod) {
                        profileInfoParameter += 1
                    }

                    val internalBadgeInstructionIndex =
                        indexOfFirstInstruction(internalBadgeStringIndex, Opcode.IGET_OBJECT)
                    val internalBadgeInstruction = getInstruction(internalBadgeInstructionIndex)
                    // Internal badge is an element/view, which is used internally to mark developers.
                    // We hook and update its text to display the follow back status.
                    val internalBadgeRegistries = internalBadgeInstruction.registersUsed
                    val internalBadgeRegistry = internalBadgeRegistries[0]
                    // User profile page (obfuscated) contains all the elements that are present on the user page.
                    // We are hooking it in order to find user session, which is used to get info on logged in user.
//                val userProfilePageRegistry = internalBadgeRegistries[1]

                    // Finding the necessary dummy registries.
                    val dummyRegistryInstructionIndex =
                        indexOfFirstInstruction(internalBadgeInstructionIndex + 1, Opcode.IGET_OBJECT)
                    val dummyRegistry1 = getInstruction(dummyRegistryInstructionIndex).registersUsed[0]
                    val dummyRegistry2 = getInstruction(internalBadgeStringIndex).registersUsed[0]

                    // Instruction to which the call needs to transfer after our hook.
                    val invokeStaticRangeIndex =
                        indexOfFirstInstruction(internalBadgeInstructionIndex, Opcode.INVOKE_STATIC_RANGE)

                    // Added instructions:
                    // Move the profile info parameter to a suitable registry.
                    // Call our hook, which will update the badge.
                    addInstructionsWithLabels(
                        internalBadgeInstructionIndex + 1,
                        """
                        move-object/from16 v$dummyRegistry2, p$profileInfoParameter
                        invoke-static {v$dummyRegistry2, v$internalBadgeRegistry}, ${PATCHES_DESCRIPTOR}/userprofile/FriendshipStatusIndicator;->indicators(Ljava/lang/Object;Ljava/lang/Object;)V
                        goto :piko
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(invokeStaticRangeIndex)),
                    )

                    enableSettings("followBackIndicator")
                }
            }
        }
    }
