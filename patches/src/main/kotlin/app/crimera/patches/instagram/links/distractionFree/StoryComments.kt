/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.distractionFree

import app.crimera.patches.instagram.utils.booleanStateField
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

context(_: BytecodePatchContext)
internal fun hideStoryComments(preferenceCall: String) {
    for (marker in listOf("StoryCommentButtonViewComponent", "StoryCommentButtonMimicAdsViewComponent")) {
        val component = Fingerprint(
            returnType = "Ljava/lang/String;",
            parameters = emptyList(),
            filters = listOf(string(marker)),
        ).matchSingle().originalMethod.definingClass
        val binding = Fingerprint(
            definingClass = component,
            returnType = "V",
            parameters = listOf("Ljava/lang/Object;"),
            filters = listOf(fieldAccess(type = "Z", opcode = Opcode.IGET_BOOLEAN)),
        ).matchSingle()
        val read = binding.instructionMatches.single()
        val registers = read.instruction.registersUsed
        if (registers.size != 2 || binding.method.implementation!!.instructions.count {
                it.opcode == Opcode.IGET_BOOLEAN
            } != 1
        ) {
            throw PatchException("Expected one story comment button visibility field: $marker")
        }
        val visible = registers[0]
        // Keep native hidden states; otherwise use the native hide path when disabled.
        binding.method.addInstructionsWithLabels(
            read.index + 1,
            """
            if-eqz v$visible, :original
            $preferenceCall
            move-result v$visible
            xor-int/lit8 v$visible, v$visible, 0x1
            """.trimIndent(),
            ExternalLabel("original", binding.method.getInstruction(read.index + 1)),
        )
    }

    val display = Fingerprint(
        returnType = "Ljava/lang/String;",
        parameters = emptyList(),
        filters = listOf(string("StoryCommentsDisplayViewComponent")),
    ).matchSingle().originalMethod.definingClass
    val binding = Fingerprint(
        definingClass = display,
        returnType = "V",
        parameters = listOf("Ljava/lang/Object;"),
        filters = listOf(
            opcode(Opcode.INSTANCE_OF),
            opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
            methodCall(opcode = Opcode.INVOKE_DIRECT, parameters = emptyList(), returnType = "V", location = MatchAfterImmediately()),
            opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately()),
        ),
    ).matchSingle()
    val hidden = binding.instructionMatches[1].instruction.registersUsed.singleOrNull()
        ?: throw PatchException("Expected one story comment display state register")
    if (binding.instructionMatches[0].instruction.registersUsed.firstOrNull() != hidden) {
        throw PatchException("Unexpected story comment display visibility branch")
    }
    // The native hidden-state handler clears every comment provider, including recycled views.
    val branch = binding.instructionMatches[1]
    binding.method.addInstructionsWithLabels(
        branch.index,
        """
        if-nez v$hidden, :original
        $preferenceCall
        move-result v$hidden
        """.trimIndent(),
        ExternalLabel("original", binding.method.getInstruction(branch.index)),
    )

    hideStoryCommentPreviews(preferenceCall)
}

context(_: BytecodePatchContext)
private fun hideStoryCommentPreviews(preferenceCall: String) {
    val commentsGetter = Fingerprint(
        definingClass = "Lcom/instagram/feed/media/Media;",
        returnType = "Ljava/util/List;",
        parameters = emptyList(),
        filters = listOf(string("story_comments")),
    ).matchSingle().originalMethod
    // These two suppliers feed story previews, including sponsored comments. Captions
    // are built separately, so keep the shared caption/preview renderer running.
    val suppliers = Fingerprint(
        returnType = "Ljava/util/List;",
        parameters = listOf(commentsGetter.definingClass),
        filters = listOf(methodCall(reference = commentsGetter)),
    ).matchAll(2..2)
    suppliers.forEach { match ->
        val method = match.method
        val scratch = method.findFreeRegister(0)
        method.addInstructionsWithLabels(
            0,
            """
            $preferenceCall
            move-result v$scratch
            if-eqz v$scratch, :original
            invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
            move-result-object v$scratch
            return-object v$scratch
            """.trimIndent(),
            ExternalLabel("original", method.getInstruction(0)),
        )
    }

    val marker = "ViewState(showCommentButton="
    val buttonField = booleanStateField(marker, marker)
    val hintField = booleanStateField(marker, ", showSelfStoryHint=")
    val renderer = Fingerprint(
        returnType = "V",
        filters = listOf(fieldAccess(reference = buttonField, opcode = Opcode.IGET_BOOLEAN)),
    ).matchSingle().method
    for ((field, expectedReads) in listOf(buttonField to 2, hintField to 3)) {
        val reads = renderer.implementation!!.instructions.withIndex().filter {
            it.value.opcode == Opcode.IGET_BOOLEAN && it.value.getReference<FieldReference>() == field
        }
        if (reads.size != expectedReads) {
            throw PatchException("Unexpected story comment visibility reads: $field")
        }
        reads.reversed().forEach { (index, instruction) ->
            val registers = instruction.registersUsed
            if (registers.size != 2) throw PatchException("Expected story comment visibility registers")
            val visible = registers[0]
            renderer.addInstructionsWithLabels(
                index + 1,
                """
                if-eqz v$visible, :original
                $preferenceCall
                move-result v$visible
                xor-int/lit8 v$visible, v$visible, 0x1
                """.trimIndent(),
                ExternalLabel("original", renderer.getInstruction(index + 1)),
            )
        }
    }
}
