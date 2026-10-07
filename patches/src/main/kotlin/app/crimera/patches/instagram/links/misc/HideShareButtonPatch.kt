/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.hideFeedUfiStates
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.matchSingle
import com.android.tools.smali.dexlib2.AccessFlags

private const val HIDE_SHARE_BUTTON = "$PREF_CALL_DESCRIPTOR->hideShareButton()Z"

@Suppress("unused")
val hideShareButtonPatch = bytecodePatch(
    name = "Hide share button",
    description = "Hides the share button on posts and reels",
) {
    dependsOn(settingsPatch)
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        hideFeedUfiStates(
            HIDE_SHARE_BUTTON,
            ", isShareEnabled=",
            ", shouldShowShareCountInUfi=",
        )

        val reels = Fingerprint(
            accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
            returnType = "Z",
            filters = listOf(string("android_purge_26_q3_UfiUseCase_shouldShowShareButton")),
            custom = { method, _ ->
                "Lcom/instagram/clips/intf/ClipsViewerConfig;" in method.parameterTypes
            },
        ).matchSingle().method
        val scratch = reels.findFreeRegister(0)
        if (scratch > 15) throw PatchException("No encodable register for the Reels share setting")
        // Instagram's false result selects the hidden state for both the button and count.
        reels.addInstructionsWithLabels(
            0,
            """
            $HIDE_SHARE_BUTTON
            move-result v$scratch
            if-eqz v$scratch, :original
            const/4 v$scratch, 0x0
            return v$scratch
            """.trimIndent(),
            ExternalLabel("original", reels.getInstruction(0)),
        )
        enableSettings("hideShareButton")
    }
}
