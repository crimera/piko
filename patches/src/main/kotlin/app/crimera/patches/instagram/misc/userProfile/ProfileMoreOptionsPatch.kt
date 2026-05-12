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
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.entity.userfriendshipstatus.userFriendshipStatusEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val profileMoreOptionsPatch =
    bytecodePatch(
        name = "More options on profile",
        description = "Adds a new button to handle user related data like copy handle, download profile picture etc",
    ) {
        dependsOn(settingsPatch, userDataEntity)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            ProfileUserInfoViewBinderFingerprint.method.apply {
                addInstructions(
                    0,
                    """
                    invoke-static {p1,p2}, ${PATCHES_DESCRIPTOR}/userprofile/ProfileMoreOption;->addProfileMoreOptionsButton(Landroid/view/ViewGroup;Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }
        }
    }
