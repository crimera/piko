/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.settings

import app.crimera.patches.instagram.entity.developerOptions.developerOptionsEntity
import app.crimera.patches.instagram.entity.instagramButton.instagramButtonEntity
import app.crimera.patches.instagram.entity.profileinfo.ProfileUserInfoViewBinderFingerprint
import app.crimera.patches.instagram.entity.profileinfo.profileInfoEntity
import app.crimera.patches.instagram.misc.extension.hooks.instagramInitHook
import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.LINKS_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.LOAD_FLAGS_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.SSTS_DESCRIPTOR
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.utils.changeFirstString
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val settingsPatch =
    bytecodePatch(
        name = "Add settings",
        description = "Adds settings to control preferences are patching",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            sharedExtensionPatch,
            addSettingsActivityPatch,
            hookFlagsPatch,
            profileInfoEntity,
            instagramButtonEntity,
            developerOptionsEntity,
        )
        execute {

            ProfileUserInfoViewBinderFingerprint.method.apply {
                val moveResObj = indexOfFirstInstruction(Opcode.MOVE_RESULT_OBJECT)
                if (moveResObj < 0) throw PatchException("Failed to find MOVE_RESULT_OBJECT in ProfileUserInfoViewBinderFingerprint")
                addInstructions(
                    moveResObj + 1,
                    """
                    invoke-static {p1,p2}, ${PATCHES_DESCRIPTOR}/userprofile/PikoSettingsButton;->addPikoSettingsButton(Landroid/view/ViewGroup;Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }

            instagramInitHook.fingerprint.method.apply {

                addInstruction(
                    0,
                    SSTS_DESCRIPTOR.format("load"),
                )

                val firstInvokeSuperIndex = indexOfFirstInstruction(Opcode.INVOKE_SUPER)
                if (firstInvokeSuperIndex < 0) throw PatchException("Failed to find INVOKE_SUPER in instagramInitHook")
                addInstruction(
                    firstInvokeSuperIndex + 1,
                    LOAD_FLAGS_DESCRIPTOR.format("load"),
                )
            }

            // The following handles the signature check while sharing a link externally and opening a link.
            UriTrustingMethodFingerprint.classDef.methods
                .firstOrNull {
                    it.returnType == "Z" && it.parameters.size == 2 &&
                        it.parameterTypes.last().type == "Z"
                }?.apply {
                    addInstructionsWithLabels(
                        0,
                        """
                        invoke-static {p1}, ${LINKS_DESCRIPTOR}->signatureCheck(Ljava/lang/Object;)Z
                        move-result v0
                        if-eqz v0, :piko
                        return v0
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(0)),
                    )
                } ?: throw PatchException("Failed to find signature check method in ${UriTrustingMethodFingerprint.definingClass}")

            AppIdentityToStringFingerprint.method.apply {
                if (AppIdentityToStringFingerprint.stringMatches.size < 2) throw PatchException("Failed to find enough string matches in AppIdentityToStringFingerprint")
                val strIndex = AppIdentityToStringFingerprint.stringMatches[1].index

                val firstIGetObjectIndex = indexOfFirstInstruction(strIndex, Opcode.IGET_OBJECT)
                if (firstIGetObjectIndex < 0) throw PatchException("Failed to find IGET_OBJECT after string in AppIdentityToStringFingerprint")
                val firstIGetObject = getInstruction(firstIGetObjectIndex)
                SignatureCheckExtensionFingerprint.changeFirstString(firstIGetObject.fieldExtractor().name)
            }

            // For welcome message.
            MainFeedFragmentOnCreateFingerprint.apply {
                if (stringMatches.isEmpty()) throw PatchException("Failed to find string matches in MainFeedFragmentOnCreateFingerprint")
                val strIndex = stringMatches[0].index

                method.apply {
                    val contextIndex = indexOfFirstInstruction(strIndex, Opcode.MOVE_RESULT_OBJECT)
                    if (contextIndex < 0) throw PatchException("Failed to find MOVE_RESULT_OBJECT after string in MainFeedFragmentOnCreateFingerprint")
                    val contextInstruction = getInstruction(contextIndex)
                    val contextRegister = contextInstruction.registersUsed[0]

                    addInstruction(
                        contextIndex + 1,
                        """
                        invoke-static{v$contextRegister}, $PATCHES_DESCRIPTOR/WelcomeMessage;->openWelcomeMessage(Landroid/content/Context;)V
                        """.trimIndent(),
                    )
                }
            }

            addFlags("contactPermissionConsentFlags")
        }
    }
