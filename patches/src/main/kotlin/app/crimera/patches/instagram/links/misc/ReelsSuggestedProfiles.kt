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
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

context(context: BytecodePatchContext)
internal fun hideReelsSuggestions() {
    val classifiers = ReelsSuggestedUsersExitFingerprint.matchAllOrNull().orEmpty()
    if (classifiers.size > 1) throw PatchException("Multiple Reels suggested-user classifiers")
    val classifier = classifiers.singleOrNull()?.instructionMatches?.first()?.methodReference

    ReelsHiddenItemsFingerprint.matchSingle().let { match ->
        val method = match.method
        val loopIndex = match.instructionMatches[1].index
        val scratch = match.instructionMatches[2].instruction.registersUsed.single()
        val castIndex = match.instructionMatches.last().index
        val cast = match.instructionMatches.last().instruction
        val itemRegister = cast.registersUsed.single()
        val itemType = cast.getReference<TypeReference>()!!.type
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
            val enumClass = ReelsItemTypeFingerprint.matchSingle().originalClassDef
            if (enumClass.superclass != "Ljava/lang/Enum;") {
                throw PatchException("Reels item type is not an enum")
            }
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
