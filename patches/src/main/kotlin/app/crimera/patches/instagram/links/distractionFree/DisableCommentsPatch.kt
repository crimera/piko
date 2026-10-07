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
import app.crimera.patches.instagram.utils.hideFeedUfiStates
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val HIDE_COMMENT_BUTTONS = "$PREF_CALL_DESCRIPTOR->hideCommentButtons()Z"

private object ReelsCommentStateFingerprint : Fingerprint(
    filters = listOf(
        string("android_purge_26_q3_UfiUseCase_getCommentButtonAndCount"),
        string("ReelsCommentButton"),
    ),
    custom = { method, _ ->
        "Lcom/instagram/clips/intf/ClipsViewerConfig;" in method.parameterTypes &&
            method.returnType.startsWith("L")
    },
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
            val reelsInstructions = reelsMethod.implementation!!.instructions
            val logIndex = reelsMatch.instructionMatches[1].index
            val hiddenIndex = logIndex + 2
            val hidden = reelsInstructions.getOrNull(hiddenIndex)
            val hiddenField = hidden?.getReference<FieldReference>()
            val stateRegister = hidden?.registersUsed?.singleOrNull()
            val mergeIndex = hiddenIndex + 1
            val reset = reelsInstructions.getOrNull(mergeIndex)
            val scratch = reset?.registersUsed?.singleOrNull()
            if (hidden?.opcode != Opcode.SGET_OBJECT || hiddenField == null ||
                hiddenField.type != hiddenField.definingClass || stateRegister == null ||
                reset?.opcode != Opcode.CONST_4 || (reset as? WideLiteralInstruction)?.wideLiteral != 0L ||
                scratch == null || scratch == stateRegister
            ) {
                throw PatchException("Could not resolve the hidden Reels comment state")
            }
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
