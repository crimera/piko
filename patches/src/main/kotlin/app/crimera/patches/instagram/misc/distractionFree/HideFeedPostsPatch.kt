/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

private object MainFeedModelsFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "L", "L", "Z", "Z", "Z"),
    filters = listOf(
        string("MainfeedAdapter.buildModels"),
        methodCall(smali = "Ljava/util/List;->size()I"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.CONST_16, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_FROM16, location = MatchAfterImmediately()),
        opcode(Opcode.IF_GE, location = MatchAfterImmediately()),
    ),
)

private fun mainFeedFooterFingerprint(adapterClass: String) = Fingerprint(
    definingClass = adapterClass,
    returnType = "V",
    filters = listOf(
        // Preserve the incremental-update snapshot before omitting the footer models.
        methodCall(
            name = "<init>",
            parameters = listOf("L", "L", "Ljava/util/List;", "Ljava/util/Map;", "I", "I", "I", "I", "I", "I", "Z", "Z", "Z"),
        ),
        methodCall(parameters = emptyList(), returnType = "Z", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        string("SHIMMER"),
        string("WELCOME_CARD"),
        methodCall(parameters = listOf("L", "L", "L"), returnType = "V"),
        opcode(Opcode.GOTO_16, location = MatchAfterImmediately()),
        methodCall(
            definingClass = "Lcom/facebook/systrace/Systrace;",
            parameters = listOf("J"),
            returnType = "Z",
            location = MatchAfterImmediately(),
        ),
        string("syncUpdateState"),
    ),
)

private object MainFeedLoadMoreFingerprint : Fingerprint(
    parameters = listOf("Ljava/util/Map;", "Z"),
    returnType = "V",
    filters = listOf(
        string("cached_feed_item_ids"),
        string("pagination_source"),
        string("feed_recs"),
    ),
)

private fun MutableMethod.stopHiddenFeedLoading() {
    val first = getInstruction(0)
    val registers = first.registersUsed
    val localCount = implementation!!.registerCount - parameterTypes.size - 1
    if (first.opcode != Opcode.IGET_OBJECT || registers.size != 2 || registers[0] >= localCount) {
        throw PatchException("Could not resolve a home feed loading scratch register")
    }
    val scratch = registers[0]
    addInstructionsWithLabels(
        0,
        """
        $PREF_CALL_DESCRIPTOR->hideFeedPosts()Z
        move-result v$scratch
        if-eqz v$scratch, :piko_loading_original
        return-void
        """.trimIndent(),
        ExternalLabel("piko_loading_original", first),
    )
}

@Suppress("unused")
val hideFeedPostsPatch = bytecodePatch(
    name = "Hide feed posts",
    description = "Hides posts from the home feed",
) {
    dependsOn(settingsPatch)
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        val match = MainFeedModelsFingerprint.matchSingle()
        val countRegisters = match.instructionMatches[2].instruction.registersUsed
        val moveRegisters = match.instructionMatches[4].instruction.registersUsed
        val branchRegisters = match.instructionMatches[5].instruction.registersUsed
        if (countRegisters.size != 1 || moveRegisters.size != 2 || branchRegisters.size != 2 ||
            moveRegisters[1] != countRegisters.single() || branchRegisters[1] != moveRegisters[0] ||
            moveRegisters[0] == countRegisters.single()
        ) {
            throw PatchException("Could not resolve the home feed item count")
        }
        val countRegister = countRegisters.single()
        val scratchRegister = moveRegisters[0]
        val insertIndex = match.instructionMatches[2].index + 1

        // The story header is already built. Skip only the item loop, preserving the feed
        // data and the normal model finalization. The loop reloads the scratch register.
        match.method.addInstructionsWithLabels(
            insertIndex,
            """
            $PREF_CALL_DESCRIPTOR->hideFeedPosts()Z
            move-result v$scratchRegister
            if-eqz v$scratchRegister, :piko_original
            const/16 v$countRegister, 0x0
            """.trimIndent(),
            ExternalLabel("piko_original", match.method.getInstruction(insertIndex)),
        )

        val footer = mainFeedFooterFingerprint(match.method.definingClass).matchSingle()
        val footerStart = footer.instructionMatches[1]
        val footerScratch = footer.instructionMatches[2].instruction.registersUsed
        val footerReceiver = footerStart.instruction.registersUsed
        if (footerScratch.size != 1 || footerReceiver.size != 1 ||
            footerScratch.single() == footerReceiver.single()
        ) {
            throw PatchException("Could not resolve the home feed footer scratch register")
        }
        // Existing branches also enter here. Keep snapshot creation and state publication,
        // but omit load-more, shimmer and empty-feed models when posts are hidden.
        footer.method.addInstructionsAtControlFlowLabel(
            footerStart.index,
            """
            $PREF_CALL_DESCRIPTOR->hideFeedPosts()Z
            move-result v${footerScratch.single()}
            if-nez v${footerScratch.single()}, :piko_feed_complete
            """.trimIndent(),
            ExternalLabel("piko_feed_complete", footer.method.getInstruction(footer.instructionMatches[7].index)),
        )

        MainFeedLoadMoreFingerprint.matchSingle().method.stopHiddenFeedLoading()
        enableSettings("hideFeedPosts")
    }
}
