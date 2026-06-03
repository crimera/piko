/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.userProfile

import app.crimera.patches.instagram.entity.profileinfo.ProfileUserInfoViewBinderFingerprint
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

val userProfileButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on user profile.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            ProfileUserInfoViewBinderFingerprint.method.apply {
                val moveResObj = indexOfFirstInstruction(Opcode.MOVE_RESULT_OBJECT)
                addInstructions(
                    moveResObj + 1,
                    """
                    invoke-static {p1,p2}, ${PATCHES_DESCRIPTOR}/userprofile/UserProfileButton;->addButtons(Landroid/view/ViewGroup;Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }
        }
    }
