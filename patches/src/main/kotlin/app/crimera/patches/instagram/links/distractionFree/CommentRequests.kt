/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.distractionFree

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.instanceOf
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val PARENT_FETCHER = "Lcom/instagram/comments/mvvm/data/network/MediaParentCommentNetworkFetcherKt;"
private const val COMMENTS_PATH = "media/%s/comments/"

internal fun disableCommentReads(preferenceCall: String) {
    val parent = Fingerprint(
        definingClass = PARENT_FETCHER,
        filters = listOf(
            string(COMMENTS_PATH),
            methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = listOf("I", "L"), returnType = "Ljava/lang/Object;"),
            methodCall(definingClass = PARENT_FETCHER, opcode = Opcode.INVOKE_STATIC, parameters = listOf("L"), returnType = "Z"),
            opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
            opcode(Opcode.NEW_INSTANCE, location = MatchAfterImmediately()),
            methodCall(smali = "Ljava/lang/Object;-><init>()V", location = MatchAfterImmediately()),
            fieldAccess(type = "Z", opcode = Opcode.IPUT_BOOLEAN, location = MatchAfterImmediately()),
        ),
    ).match()
    val parentMethod = parent.method
    val check = parent.instructionMatches[2].index
    val errorField = parentMethod.getInstruction(check - 2)
    if (errorField.opcode != Opcode.IGET_OBJECT) throw PatchException("Missing comment network failure payload")
    val networkFailure = errorField.getReference<FieldReference>()!!.definingClass
    val retryable = parent.instructionMatches[3].instruction.registersUsed.single()
    // A plain IOException without an HTTP response is a retryable parent-comment failure.
    parentMethod.guardRead(
        parent.instructionMatches[1].index, parentMethod.getInstruction(parent.instructionMatches[4].index),
        preferenceCall, prepareFailure = "const/16 v$retryable, 0x1",
    )

    val child = Fingerprint(
        definingClass = "Lcom/instagram/comments/mvvm/data/network/MediaChildCommentNetworkFetcherKt;",
        filters = listOf(
            string("media/%s/comments/%s/inline_fb_child_comments/"),
            methodCall(opcode = Opcode.INVOKE_VIRTUAL_RANGE, parameters = listOf("L", "I", "I", "Z", "Z"), returnType = "Ljava/lang/Object;"),
            string("media/%s/comments/%s/inline_child_comments/"),
            instanceOf(networkFailure),
            opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
            fieldAccess(opcode = Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
            opcode(Opcode.RETURN_OBJECT, location = MatchAfterImmediately()),
        ),
    ).match()
    child.method.guardRead(
        child.instructionMatches[1].index, child.method.getInstruction(child.instructionMatches[5].index), preferenceCall,
    )

    val legacy = Fingerprint(
        definingClass = "Lcom/instagram/comments/request/CommentsFetcher;",
        filters = listOf(
            string("media/%s/stream_comments/"),
            methodCall(opcode = Opcode.INVOKE_STATIC, parameters = listOf("L", "I", "I", "Z", "L"), returnType = "V"),
            fieldAccess(smali = "Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;", opcode = Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
            string(COMMENTS_PATH),
            methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = listOf("L"), returnType = "Ljava/lang/Object;"),
            instanceOf(networkFailure),
            opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
            fieldAccess(opcode = Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
            methodCall(opcode = Opcode.INVOKE_STATIC, parameters = listOf("L", "Z"), returnType = "V", location = MatchAfterImmediately()),
            opcode(Opcode.CONST_4, location = MatchAfterImmediately()),
            fieldAccess(opcode = Opcode.IPUT_OBJECT, location = MatchAfterImmediately()),
            methodCall(smali = "Ljava/util/Map;->remove(Ljava/lang/Object;)Ljava/lang/Object;"),
        ),
    ).match()
    val legacyMethod = legacy.method
    val failure = legacyMethod.getInstruction(legacy.instructionMatches[7].index)
    val paged = legacy.instructionMatches[8].instruction.registersUsed.last()
    legacyMethod.guardRead(legacy.instructionMatches[4].index, failure, preferenceCall)
    val streamSend = legacy.instructionMatches[1].index
    val registerListener = legacyMethod.getInstruction(streamSend - 1)
    if (registerListener.opcode != Opcode.INVOKE_VIRTUAL || registerListener.registersUsed.size != 2) {
        throw PatchException("Missing streamed comment listener registration")
    }
    // The stream listener uses the same failure notifications and pending-request cleanup with paged=false.
    legacyMethod.guardRead(
        streamSend, failure, preferenceCall, scratch = registerListener.registersUsed.last(),
        prepareFailure = "const/16 v$paged, 0x0",
    )
}

private fun MutableMethod.guardRead(
    index: Int,
    failure: BuilderInstruction,
    preferenceCall: String,
    scratch: Int = getInstruction(index + 1).registersUsed.single(),
    prepareFailure: String = "",
) {
    val send = getInstruction(index)
    if (scratch in send.registersUsed) throw PatchException("Comment dispatch uses the temporary register")
    addInstructionsWithLabels(
        index,
        """
        $preferenceCall
        move-result v$scratch
        if-eqz v$scratch, :send
        $prepareFailure
        goto/32 :failure
        """.trimIndent(),
        ExternalLabel("send", send), ExternalLabel("failure", failure),
    )
}
