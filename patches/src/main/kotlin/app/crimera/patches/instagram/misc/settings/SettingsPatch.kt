/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.settings

import app.crimera.patches.instagram.entity.profileinfo.ProfileUserInfoViewBinderFingerprint
import app.crimera.patches.instagram.entity.profileinfo.profileInfoEntity
import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.LINKS_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.SSTS_DESCRIPTOR
import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
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
        dependsOn(sharedExtensionPatch, addSettingsActivityPatch, profileInfoEntity)
        execute {

            ProfileUserInfoViewBinderFingerprint.method.apply {
                val moveResObj = indexOfFirstInstruction(Opcode.MOVE_RESULT_OBJECT)
                addInstructions(
                    moveResObj + 1,
                    """
                    invoke-static {p1,p2}, ${PATCHES_DESCRIPTOR}/userprofile/PikoSettingsButton;->addPikoSettingsButton(Landroid/view/ViewGroup;Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }

            PikoSettingsButtonExtensionFingerprint.method.apply {
                val buttonStyleClass =
                    IgdsButtonSetStyleFingerprint.method.parameters
                        .first()
                        .type
                PikoSettingsButtonStyleExtensionFingerprint.changeFirstString(classNameToExtension(buttonStyleClass))

                val buttonStyleInstructionIndex = indexOfFirstInstruction(Opcode.INVOKE_STATIC)
                val dummyRegister = findFreeRegister(buttonStyleInstructionIndex)
                val buttonRegister = instructions.first { it.opcode == Opcode.NEW_INSTANCE }.registersUsed[0]

                addInstructions(
                    buttonStyleInstructionIndex + 1,
                    """
                    move-result-object v$dummyRegister
                    invoke-virtual {v$buttonRegister, v$dummyRegister}, ${IgdsButtonSetStyleFingerprint.definingClass}->setStyle($buttonStyleClass)V
                    """.trimIndent(),
                )
            }

            ExtensionsUtilsFingerprint.method.addInstruction(
                0,
                SSTS_DESCRIPTOR.format("load"),
            )

            // The following handles the signature check while sharing a link externally and opening a link.
            UriTrustingMethodFingerprint.classDef.methods
                .first {
                    it.returnType == "Z" && it.parameters.size == 2 &&
                        it.parameterTypes.last() == "Z"
                }.apply {
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
                }

            AppIdentityToStringFingerprint.method.apply {
                val strIndex = AppIdentityToStringFingerprint.stringMatches[1].index

                val firstIGetObject = getInstruction(indexOfFirstInstruction(strIndex, Opcode.IGET_OBJECT))
                SignatureCheckExtensionFingerprint.changeFirstString(firstIGetObject.fieldExtractor().name)
            }
        }
    }
