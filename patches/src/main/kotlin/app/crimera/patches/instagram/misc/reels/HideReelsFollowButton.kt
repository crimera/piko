/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.reels

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private const val PREF_CLASS_DESCRIPTOR = "Lapp/morphe/extension/instagram/utils/Pref;"

// AuthorInfoUseCase.shouldShowFollowButton(ClipsViewerConfig)
private object ReelsShouldShowFollowButtonFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Z",
    strings = listOf("android_purge_26_q3_AuthorInfoUseCase_shouldShowFollowButton"),
)

@Suppress("unused")
val hideReelsFollowButtonPatch =
    bytecodePatch(
        name = "Hide Reels follow button",
        description = "Removes the follow button from Reels.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        dependsOn(settingsPatch)

        execute {
            ReelsShouldShowFollowButtonFingerprint.method.apply {
                val returnIndex = indexOfFirstInstructionOrThrow { opcode == Opcode.RETURN }

                addInstructionsAtControlFlowLabel(
                    returnIndex,
                    """
                        invoke-static {v0}, $PREF_CLASS_DESCRIPTOR->showReelsFollowButton(Z)Z
                        move-result v0
                    """,
                )
            }

            enableSettings("hideReelsFollowButton")
        }
    }
