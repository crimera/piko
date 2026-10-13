/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.methodReference
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.matchSingle
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

context(_: BytecodePatchContext)
internal fun hideReelsSuggestions() {
    val classifier = ReelsSuggestedUsersExitFingerprint.matchSingle().instructionMatches.first().methodReference

    ReelsHiddenItemsFingerprint.matchSingle().let { match ->
        val method = match.method
        val loopIndex = match.instructionMatches[1].index
        val scratch = match.instructionMatches[2].getInstruction<OneRegisterInstruction>().registerA
        val castIndex = match.instructionMatches.last().index
        val itemRegister = match.instructionMatches.last().getInstruction<OneRegisterInstruction>().registerA

        // Native classification covers both blending and netego cards; skip the item before it gets a page.
        method.addInstructionsWithLabels(
            castIndex + 1,
            """
            invoke-static/range {v$itemRegister .. v$itemRegister}, $classifier
            move-result v$scratch
            invoke-static/range {v$scratch .. v$scratch}, $PATCHES_DESCRIPTOR/SuggestedProfiles;->hideReelsSuggestedUsers(Z)Z
            move-result v$scratch
            if-nez v$scratch, :next_item
            """.trimIndent(),
            ExternalLabel("next_item", method.getInstruction(loopIndex)),
        )
    }
}
