/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.userProfileActionBarButton

import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.addFlags
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal object ProfileActionBarFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/profile/actionbar/ProfileActionBar;",
    strings = listOf("IG_PROFILE"),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
)

val userProfileActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on user profile action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            ProfileActionBarFingerprint.apply {
                val strIndex = stringMatches.first().index
                method.apply {
                    val userIGetObjectIndex =
                        instructions.indexOfLast {
                            it.opcode == Opcode.IGET_OBJECT &&
                                it.location.index < strIndex
                        }
                    val userFieldRef = getInstruction(userIGetObjectIndex).getReference<FieldReference>()
                    val userHelperClassName = userFieldRef!!.definingClass

                    val leftActionBarElementListIteratorIndex = indexOfFirstInstruction(Opcode.INVOKE_INTERFACE)
                    val actionBarLeftLayout = getInstruction(leftActionBarElementListIteratorIndex - 1)
                    val layoutRegister = actionBarLeftLayout.registersUsed[0]

                    // + 1 before the method is not static.
                    val userHelperClassParameterIndex = parameters.indexOfFirst { it.type == userHelperClassName } + 1

                    val freeRegister =
                        findFreeRegister(
                            leftActionBarElementListIteratorIndex,
                            listOf(layoutRegister),
                        )

                    addInstructionsWithLabels(
                        leftActionBarElementListIteratorIndex,
                        """
                        move-object/from16 v$freeRegister, p$userHelperClassParameterIndex
                        if-eqz v$freeRegister, :piko
                        iget-object v$freeRegister, v$freeRegister, $userFieldRef
                        invoke-static {v$layoutRegister, v$freeRegister}, $ACTIONBAR_DESCRIPTOR/UserProfileActionBar;->addActionBarButton(Landroid/view/ViewGroup;Ljava/lang/Object;)V
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(leftActionBarElementListIteratorIndex)),
                    )

                    addFlags("profileActionBarFlags")
                }
            }
        }
    }
