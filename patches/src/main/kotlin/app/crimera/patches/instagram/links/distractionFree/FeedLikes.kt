/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.distractionFree

import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.feedUfiStateField
import app.crimera.patches.instagram.utils.hideFeedUfiStates
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.string
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

internal fun hideFeedLikes() {
    hideFeedUfiStates(HIDE_LIKES, ", shouldShowLikeCountInUfi=")

    val viewHolder = Fingerprint(
        name = "<init>",
        parameters = listOf("Landroid/view/View;"),
        filters = listOf(
            resourceLiteral(ResourceType.ID, "row_feed_button_like"),
            methodCall(smali = "Landroid/view/View;->requireViewById(I)Landroid/view/View;", location = MatchAfterImmediately()),
            opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        ),
    ).match()
    val view = viewHolder.instructionMatches.last()
    val button = view.instruction.registersUsed.single()
    viewHolder.method.addInstructions(
        view.index + 1,
        "invoke-static/range {v$button .. v$button}, $PREF_DESCRIPTOR->hideLikeView(Landroid/view/View;)V",
    )

    hideLithoLikeButton()
    hideComposeLikeButton()
    hideFeedLikeSummary()

    // Carousel slides can also have their own like button over the media.
    Fingerprint(
        parameters = listOf("L", "L"),
        returnType = "Z",
        filters = listOf(
            string("open_carousel_prompt"),
            methodCall(smali = "Ljava/lang/String;->endsWith(Ljava/lang/String;)Z"),
        ),
    ).method.returnHiddenLikeState()
}

private fun hideFeedLikeSummary() {
    val summary = Fingerprint(
        parameters = listOf("L"),
        returnType = "L",
        filters = listOf(
            string("row_feed_textview_likes"),
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                parameters = listOf("Landroid/content/Context;", "L", "L", "Ljava/lang/String;"),
                returnType = "Ljava/lang/CharSequence;",
            ),
            opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
            opcode(Opcode.GOTO_16, location = MatchAfterImmediately()),
            fieldAccess(type = "Z", opcode = Opcode.IGET_BOOLEAN, location = MatchAfterImmediately()),
            opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
            fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
            opcode(Opcode.CONST_16, location = MatchAfterImmediately()),
        ),
    ).match()
    // This row also renders follower, tagged-user and ad text. Only omit the row
    // after Instagram has picked the likes-only branch.
    val likesOnly = summary.instructionMatches[6].index
    val scratch = summary.method.findFreeRegister(likesOnly)
    summary.method.addInstructionsWithLabels(
        likesOnly,
        """
        $HIDE_LIKES
        move-result v$scratch
        if-eqz v$scratch, :original
        const/16 v$scratch, 0x0
        return-object v$scratch
        """.trimIndent(),
        ExternalLabel("original", summary.method.getInstruction(likesOnly)),
    )

    // Video posts can append likes to the view count. Select the native
    // views-only path before the formatter caches the combined text.
    val formatter = summary.instructionMatches[1].methodReference
    val mixed = Fingerprint(
        definingClass = formatter.definingClass,
        name = formatter.name,
        parameters = formatter.parameterTypes.map { it.toString() },
        returnType = formatter.returnType,
        filters = listOf(
            methodCall(smali = "Ljava/util/List;->isEmpty()Z"),
            opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
            opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        ),
    ).match()
    val branch = mixed.instructionMatches.last()
    val empty = branch.instruction.registersUsed.single()
    if (mixed.instructionMatches[1].instruction.registersUsed.single() != empty) {
        throw PatchException("Unexpected liked-by list check")
    }
    mixed.method.addInstructionsWithLabels(
        branch.index,
        """
        if-nez v$empty, :original
        $HIDE_LIKES
        move-result v$empty
        """.trimIndent(),
        ExternalLabel("original", mixed.method.getInstruction(branch.index)),
    )
}

private fun hideLithoLikeButton() {
    val likeComponent = Fingerprint(
        parameters = listOf("L"),
        returnType = "L",
        filters = listOf(
            fieldAccess(reference = feedUfiStateField(", isLiked="), opcode = Opcode.IGET_BOOLEAN),
            string("android.widget.Button"),
            resourceLiteral(ResourceType.ID, "row_feed_button_like"),
        ),
    ).originalMethod.definingClass
    val row = Fingerprint(
        parameters = listOf("L"),
        returnType = "L",
        filters = listOf(
            string("onUfiVisible"),
            methodCall(definingClass = likeComponent, name = "<init>"),
            methodCall(parameters = listOf("L", "L", "L", "L", "Lcom/instagram/common/session/UserSession;", "Z", "Z"), returnType = "L"),
            opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
            opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
            methodCall(smali = "Ljava/util/AbstractCollection;->add(Ljava/lang/Object;)Z", location = MatchAfterImmediately()),
        ),
    ).match()
    // Skip adding the entire wrapped child, including its click handler and spacing.
    val add = row.instructionMatches.last()
    val scratch = row.method.findFreeRegister(add.index, add.instruction.registersUsed)
    row.method.addInstructionsWithLabels(
        add.index,
        """
        $HIDE_LIKES
        move-result v$scratch
        if-nez v$scratch, :skipLike
        """.trimIndent(),
        ExternalLabel("skipLike", row.method.getInstruction(add.index + 1)),
    )
}

private fun hideComposeLikeButton() {
    val row = Fingerprint(
        returnType = "V",
        filters = listOf(
            string("instagram.features.feed.adapter.row.ufi.compose.IconRow (ComposeMediaUFIComponent.kt:92)"),
            opcode(Opcode.FILLED_NEW_ARRAY),
            opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
            methodCall(parameters = listOf("[Ljava/lang/Object;"), returnType = "Ljava/util/List;", location = MatchAfterImmediately()),
        ),
    ).match()
    // IconRow drops null entries. Its four slots are like, comment, share and repost.
    val icons = row.instructionMatches[1]
    val slots = icons.instruction.registersUsed
    if (slots.size != 4 || slots.distinct().size != 4) throw PatchException("Unexpected feed icon slots")
    val scratch = row.method.findFreeRegister(icons.index, slots)
    row.method.addInstructionsWithLabels(
        icons.index,
        """
        $HIDE_LIKES
        move-result v$scratch
        if-eqz v$scratch, :original
        const/4 v${slots.first()}, 0x0
        """.trimIndent(),
        ExternalLabel("original", row.method.getInstruction(icons.index)),
    )
}
