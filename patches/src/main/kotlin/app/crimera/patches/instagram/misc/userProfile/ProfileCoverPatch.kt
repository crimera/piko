/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.userProfile

import app.crimera.patches.instagram.entity.profileinfo.ProfileUserInfoViewBinderFingerprint
import app.crimera.patches.instagram.entity.profileinfo.profileInfoEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

private const val EXTENSION_CLASS = "$PATCHES_DESCRIPTOR/userprofile/ProfileCover;"

@Suppress("unused")
val profileCoverPatch =
    bytecodePatch(
        name = "Profile cover",
        description = "Shows an image or GIF of your choice behind your own profile header. Only visible to you",
        default = false,
    ) {
        dependsOn(settingsPatch, profileInfoEntity)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // Same method the friendship status indicator hooks: it gets the profile header view and the profile info.
            BindInternalBadgeFingerprint.method.apply {
                val profileInfoClassType = ProfileUserInfoViewBinderFingerprint.method.parameters[1].type
                val thisOffset = if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
                val profileInfoParameter = parameters.indexOfFirst { it.type == profileInfoClassType } + thisOffset
                val viewParameter = parameters.indexOfFirst { it.type == "Landroid/view/View;" } + thisOffset

                addInstructions(
                    0,
                    """
                    move-object/from16 v0, p$viewParameter
                    move-object/from16 v1, p$profileInfoParameter
                    invoke-static {v0, v1}, $EXTENSION_CLASS->apply(Landroid/view/View;Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }

            enableSettings("profileCover")
        }
    }
