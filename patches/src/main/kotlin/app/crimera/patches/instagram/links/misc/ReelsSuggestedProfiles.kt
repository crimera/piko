/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.methodReference
import app.crimera.patches.instagram.utils.typeReference
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel

context(context: BytecodePatchContext)
internal fun hideReelsSuggestions() {
    val classifier = ReelsSuggestedUsersExitFingerprint.matchOrNull()
        ?.instructionMatches?.first()?.methodReference

    ReelsHiddenItemsFingerprint.let {
        val method = it.method
        val loopIndex = it.instructionMatches[1].index
        val scratch = it.instructionMatches[2].instruction.registersUsed.single()
        val castMatch = it.instructionMatches.last()
        val castIndex = castMatch.index
        val cast = castMatch.instruction
        val itemRegister = cast.registersUsed.single()
        val itemType = castMatch.typeReference
        val classify = if (classifier != null) {
            if (classifier.parameterTypes.map(CharSequence::toString) != listOf(itemType)) {
                throw PatchException("Reels classifier does not accept the filtered item")
            }
            """
            invoke-static/range {v$itemRegister .. v$itemRegister}, $classifier
            move-result v$scratch
            invoke-static/range {v$scratch .. v$scratch}, $PATCHES_DESCRIPTOR/SuggestedProfiles;->hideReelsSuggestedUsers(Z)Z
            """.trimIndent()
        } else {
            // Newer builds inline the classifier; the card's enum retains both suggested-user kinds.
            val enumClass = ReelsItemTypeFingerprint.originalClassDef
            val typeField = context.classDefBy(itemType).instanceFields.singleOrNull { it.type == enumClass.type }
                ?: throw PatchException("Could not resolve a unique Reels item type field")
            if (scratch == itemRegister || scratch > 15 || itemRegister > 15) {
                throw PatchException("Reels item type has no safe field-read registers")
            }
            """
            iget-object v$scratch, v$itemRegister, $typeField
            invoke-static/range {v$scratch .. v$scratch}, $PATCHES_DESCRIPTOR/SuggestedProfiles;->hideReelsSuggestedUsers(Ljava/lang/Enum;)Z
            """.trimIndent()
        }

        // Native classification covers both blending and netego cards; skip the item before it gets a page.
        method.addInstructionsWithLabels(
            castIndex + 1,
            """
            $classify
            move-result v$scratch
            if-nez v$scratch, :next_item
            """.trimIndent(),
            ExternalLabel("next_item", method.getInstruction(loopIndex)),
        )
    }
}
