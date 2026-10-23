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
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import app.morphe.patcher.util.matchSingle
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val PREF_CLASS_DESCRIPTOR = "Lapp/morphe/extension/instagram/utils/Pref;"

// AuthorInfoUseCase.shouldShowFollowButton(ClipsViewerConfig), inlined in the Reels UFI state.
private object ReelsInlineFollowButtonFingerprint : Fingerprint(
    returnType = "L",
    filters = listOf(
        string("android_purge_26_q3_AuthorInfoUseCase_shouldShowFollowButton"),
        literal(1),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        literal(0, location = MatchAfterImmediately()),
        string("android_purge_26_q3_FollowUseCase_getUiState"),
    ),
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
            ReelsInlineFollowButtonFingerprint.matchSingle().let {
                // Both follow results merge after the disabled result.
                val disabledIndex = it.instructionMatches[3].index
                val register = it.instructionMatches[3].getInstruction<OneRegisterInstruction>().registerA
                it.method.addInstructionsAtControlFlowLabel(
                    disabledIndex + 1,
                    """
                    invoke-static/range {v$register .. v$register}, $PREF_CLASS_DESCRIPTOR->showReelsFollowButton(Z)Z
                    move-result v$register
                """,
                )
            }

            enableSettings("hideReelsFollowButton")
        }
    }
