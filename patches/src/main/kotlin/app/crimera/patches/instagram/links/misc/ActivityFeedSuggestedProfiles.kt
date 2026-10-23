/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.common.parameterRegisterStart
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import app.morphe.patcher.util.p0Register
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val SUGGESTIONS = "$PATCHES_DESCRIPTOR/SuggestedProfiles;"

// Activity feed category parameter, saved by Instagram before the parameter registers are reused.
private const val CATEGORY_PARAMETER_INDEX = 8

private fun MutableMethod.filterUsersAfter(index: Int) {
    val register = getInstruction<OneRegisterInstruction>(index).registerA
    addInstructions(
        index + 1,
        """
        invoke-static/range {v$register .. v$register}, $SUGGESTIONS->filterActivityFeedUsers(Ljava/util/List;)Ljava/util/List;
        move-result-object v$register
        """.trimIndent(),
    )
}

context(_: BytecodePatchContext)
internal fun hideActivityFeedSuggestions() {
    // Resolve every match before editing, since they all point into the same method.
    val followBackRead = ActivityFeedFollowBackUsersFingerprint.instructionMatches.first().index
    val delegatedRead = ActivityFeedDelegatedUsersFingerprint.instructionMatches.first().index
    val recommendations = ActivityFeedRecommendationsFingerprint.instructionMatches
    val method = ActivityFeedRecommendationsFingerprint.method

    val skipSection = method.getInstruction(
        method.getInstruction<BuilderOffsetInstruction>(recommendations[4].index).target.location.index,
    )
    val headerIndex = recommendations[5].index
    val headerScratch = method.getInstruction<TwoRegisterInstruction>(recommendations[6].index).registerA
    val iteratorIndex = recommendations[7].index
    val loopScratch = method.getInstruction<OneRegisterInstruction>(recommendations[10].index).registerA

    // Pinned request construction reuses the parameter registers; use the native saved category.
    val categoryParameter = method.p0Register + CATEGORY_PARAMETER_INDEX
    val categoryRegister = method.getInstruction<TwoRegisterInstruction>(
        method.indexOfFirstInstructionReversedOrThrow(headerIndex) {
            opcode == Opcode.MOVE_OBJECT_FROM16 && (this as TwoRegisterInstruction).registerB == categoryParameter
        },
    ).registerA

    // Keep the pinned request row above, and skip only recommendations and their footer.
    method.addInstructionsWithLabels(
        iteratorIndex,
        """
        invoke-static/range {v$categoryRegister .. v$categoryRegister}, $SUGGESTIONS->hideActivityFeedSuggestions(Ljava/lang/String;)Z
        move-result v$loopScratch
        if-nez v$loopScratch, :piko_skip_suggestion_rows
        """.trimIndent(),
        ExternalLabel("piko_skip_suggestion_rows", skipSection),
    )

    // Existing header branches must enter the gate instead of jumping straight to the append.
    method.addInstructionsAtControlFlowLabel(
        headerIndex,
        """
        invoke-static/range {v$categoryRegister .. v$categoryRegister}, $SUGGESTIONS->hideActivityFeedSuggestions(Ljava/lang/String;)Z
        move-result v$headerScratch
        if-nez v$headerScratch, :piko_skip_suggestion_header
        """.trimIndent(),
        ExternalLabel("piko_skip_suggestion_header", method.getInstruction(headerIndex + 1)),
    )

    method.filterUsersAfter(delegatedRead)
    method.filterUsersAfter(followBackRead)

    ActivityFeedDelegatedRecommendationsFingerprint.let {
        it.method.filterUsersAfter(it.instructionMatches.last().index)
    }
}
