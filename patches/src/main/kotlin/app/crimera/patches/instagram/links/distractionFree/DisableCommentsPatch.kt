/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.distractionFree

import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.patches.instagram.utils.hideFeedUfiStates
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import app.morphe.patcher.util.matchSingle
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val HIDE_COMMENT_BUTTONS = "$PREF_CALL_DESCRIPTOR->hideCommentButtons()Z"

private object ReelsCommentStateFingerprint : Fingerprint(
    returnType = "L",
    filters = listOf(
        string("android_purge_26_q3_UfiUseCase_getCommentButtonAndCount"),
        string("ReelsCommentButton"),
        methodCall(location = MatchAfterImmediately()),
        // Hidden comment state, a static instance of its own class.
        fieldAccess(opcode = Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
        // Button, button-with-count and hidden paths all join here.
        opcode(Opcode.CONST_4, location = MatchAfterImmediately()),
    ),
)

@Suppress("unused")
val disableCommentsPatch =
    bytecodePatch(
        name = "Disable comments",
        description = "Disables comments and hides comment buttons and counts on posts and reels.",
    ) {
        dependsOn(settingsPatch, interceptUriPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            hideFeedUfiStates(
                HIDE_COMMENT_BUTTONS,
                ", isCommentsEnabled=",
                ", shouldShowCommentCountInUfi=",
            )

            val reelsMatch = ReelsCommentStateFingerprint.matchSingle()
            val reelsMethod = reelsMatch.method
            val hiddenField = reelsMatch.instructionMatches[3].fieldReference
            val stateRegister = reelsMatch.instructionMatches[3].getInstruction<OneRegisterInstruction>().registerA
            val mergeIndex = reelsMatch.instructionMatches[4].index
            val scratch = reelsMatch.instructionMatches[4].getInstruction<OneRegisterInstruction>().registerA
            // Button, button-with-count and hidden paths all join here. Retarget their
            // branches to the hook, then let Instagram initialize the scratch register.
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

            enableSettings("disableComments")
        }
    }
