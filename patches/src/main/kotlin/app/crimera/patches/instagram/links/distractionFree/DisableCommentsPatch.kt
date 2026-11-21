/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.hideFeedUfiStates
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import app.morphe.patcher.util.findFreeRegister
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val HIDE_COMMENT_BUTTONS = "$PREF_CALL_DESCRIPTOR->hideCommentButtons()Z"

private object ReelsCommentStateFingerprint : Fingerprint(
    returnType = "L",
    filters = listOf(
        string("ReelsCommentButton"),
        methodCall(location = MatchAfterImmediately()),
        // Hidden comment state, a static instance of its own class.
        fieldAccess(opcode = Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
    ),
)

@Suppress("unused")
val disableCommentsPatch =
    patch(
        name = "Disable comments",
        description = "Disables comments and hides comment buttons on posts, reels and stories",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            hideFeedUfiStates(
                HIDE_COMMENT_BUTTONS,
                ", isCommentsEnabled=",
                ", shouldShowCommentCountInUfi=",
            )

            val reelsMatch = ReelsCommentStateFingerprint.match()
            val reelsMethod = reelsMatch.method
            val hidden = reelsMatch.instructionMatches.last()
            val hiddenField = hidden.fieldReference
            val stateRegister = hidden.getInstruction<OneRegisterInstruction>().registerA
            val mergeIndex = hidden.index + 1
            val scratch = reelsMethod.findFreeRegister(mergeIndex, stateRegister)
            // The button, button-with-count and hidden branches converge after the hidden state read.
            reelsMethod.addInstructionsAtControlFlowLabel(
                mergeIndex,
                """
                $HIDE_COMMENT_BUTTONS
                move-result v$scratch
                if-eqz v$scratch, :piko_comments_original
                sget-object v$stateRegister, $hiddenField
                :piko_comments_original
                nop
                """.trimIndent(),
            )

            hideStoryComments(HIDE_COMMENT_BUTTONS)
            disableCommentReads(HIDE_COMMENT_BUTTONS)
            enableSettings("disableComments")
        }
    }
