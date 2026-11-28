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
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val PREF_CLASS_DESCRIPTOR = "Lapp/morphe/extension/instagram/utils/Pref;"

// AuthorInfoUseCase.shouldShowFollowButton(ClipsViewerConfig), inlined in the Reels UFI state.
private object ReelsInlineFollowButtonFingerprint : Fingerprint(
    returnType = "L",
    filters = listOf(
        fieldAccess(definingClass = "Lcom/instagram/clips/intf/ClipsViewerSource;", opcode = Opcode.SGET_OBJECT),
        opcode(Opcode.IF_EQ, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_OBJECT_FROM16, location = MatchAfterImmediately()),
        fieldAccess(
            definingClass = "Lcom/instagram/clips/intf/ClipsViewerConfig;", type = "Z",
            opcode = Opcode.IGET_BOOLEAN, location = MatchAfterImmediately(),
        ),
        literal(1, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        literal(0, location = MatchAfterImmediately()),
        string("clips_viewer_follow_button"),
    ),
)

@Suppress("unused")
val hideReelsFollowButtonPatch =
    patch(
        name = "Hide Reels follow button",
        description = "Removes the follow button from Reels.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        dependsOn(settingsPatch)

        execute {
            ReelsInlineFollowButtonFingerprint.let {
                // Both follow results merge after the disabled result.
                val disabled = it.instructionMatches[6]
                val register = disabled.getInstruction<OneRegisterInstruction>().registerA
                it.method.addInstructionsAtControlFlowLabel(
                    disabled.index + 1,
                    """
                    invoke-static/range {v$register .. v$register}, $PREF_CLASS_DESCRIPTOR->showReelsFollowButton(Z)Z
                    move-result v$register
                """,
                )
            }

            enableSettings("hideReelsFollowButton")
        }
    }
