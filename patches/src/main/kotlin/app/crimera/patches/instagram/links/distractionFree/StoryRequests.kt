/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.distractionFree

import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.UTILS_DESCRIPTOR
import app.crimera.patches.instagram.utils.returnOfflineRequestIf
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.MethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val DISABLE_STORY_READS = "$PREF_CALL_DESCRIPTOR->disableStories()Z"
private const val TRAY = "feed/reels_tray/"
private const val CALLABLE = "Ljava/util/concurrent/Callable;"

internal fun disableStoryReads() {
    val latest = Fingerprint(
        name = "run",
        filters = listOf(
            string("feed/get_latest_reel_media/"),
            methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = emptyList(), returnType = "L"),
            opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
            opcode(Opcode.CONST_4, location = MatchAfterImmediately()),
            opcode(Opcode.NEW_INSTANCE, location = MatchAfterImmediately()),
        ),
    ).match()
    val stream = Fingerprint(
        filters = listOf(
            string("feed/reels_media_stream/"), string("reel_id_to_media_ids_dict"),
            methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = emptyList(), returnType = "L"),
            opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
            opcode(Opcode.RETURN_OBJECT, location = MatchAfterImmediately()),
        ),
    ).match()
    val requestBuilder = latest.instructionMatches[1].methodReference
    val streamBuilder = stream.instructionMatches[2].methodReference

    // Target requests that load the story tray or its media. Some tray callers
    // obtain the same path from Instagram's string table instead of an inline literal.
    val requests = listOf(
        latest to latest.instructionMatches[1].index,
        storyBuilderSite(requestBuilder, "ReelApiUtil.createReelsTrayPageRequestTask", TRAY),
        storyBuilderSite(requestBuilder, "ReelApiUtil.createReelsTrayRequestTask - reason: ", TRAY),
        storyBuilderSite(requestBuilder, "${TRAY}_v1_FOLLOWING", TRAY),
        storyBuilderSite(requestBuilder, "STORY_CHAINING_SESSION_INDEX", "delivered_reel_ids"),
        storyBuilderSite(requestBuilder, "is_mutuals_tray", "source", "Lcom/instagram/direct/inbox/stories/friendapps/StoriesMutualsTrayNetworkFetcher;"),
    )
    val streams = listOf(
        stream to stream.instructionMatches[2].index,
        storyBuilderSite(streamBuilder, "ReelApiUtil.createReelsTrayStreamingRequestTask - reason: ", TRAY),
        storyBuilderSite(streamBuilder, TRAY, "${TRAY}_v1_FOLLOWING"),
    )

    val regular = Fingerprint(
        definingClass = requestBuilder.definingClass, name = requestBuilder.name,
        parameters = emptyList(), returnType = requestBuilder.returnType,
    ).match()
    val regularCopy = regular.addStoryBuilder(MutableMethodImplementation(3))
    regularCopy.addInstructions(
        0,
        """
        invoke-virtual/range {p0 .. p0}, $requestBuilder
        move-result-object v0
        return-object v0
        """.trimIndent(),
    )
    regularCopy.returnOfflineRequestIf(0, DISABLE_STORY_READS)

    val streaming = Fingerprint(
        definingClass = streamBuilder.definingClass, name = streamBuilder.name,
        parameters = emptyList(), returnType = streamBuilder.returnType,
        filters = listOf(
            opcode(Opcode.NEW_INSTANCE),
            methodCall(
                opcode = Opcode.INVOKE_DIRECT, name = "<init>", parameters = listOf(CALLABLE, "I", "I", "Z"),
                location = MatchAfterImmediately(),
            ),
        ),
    ).match()
    val streamCopy = streaming.addStoryBuilder(requireNotNull(streaming.originalMethod.implementation))
    val allocation = streaming.instructionMatches.first().index
    val task = streamCopy.getInstruction<OneRegisterInstruction>(allocation).registerA
    val constructor = streamCopy.getInstruction<FiveRegisterInstruction>(allocation + 1)
    if (constructor.registerC != task || constructor.registerD == task) {
        throw PatchException("Unexpected streaming story task constructor")
    }
    // The native streaming request retains its scheduler and listeners. Its existing
    // failure path converts an exception from this callable into a stream read error.
    streamCopy.addInstructionsAtControlFlowLabel(
        allocation,
        """
        $DISABLE_STORY_READS
        move-result v$task
        if-eqz v$task, :original
        sget-object v${constructor.registerD}, $UTILS_DESCRIPTOR/OfflineRequest;->REQUEST:$CALLABLE
        :original
        nop
        """.trimIndent(),
    )
    for ((sites, replacement) in listOf(requests to regularCopy, streams to streamCopy)) {
        for ((match, index) in sites) {
            if (match.method.getInstruction(index).opcode != Opcode.INVOKE_VIRTUAL) {
                throw PatchException("Unexpected story request factory call in ${match.method}")
            }
            val builder = match.method.getInstruction<FiveRegisterInstruction>(index).registerC
            match.method.replaceInstruction(index, "invoke-virtual {v$builder}, $replacement")
        }
    }
}

private fun storyBuilderSite(builder: MethodReference, first: String, second: String, owner: String? = null): Pair<Match, Int> {
    val match = Fingerprint(
        definingClass = owner,
        filters = listOf(string(first), string(second), methodCall(reference = builder)),
    ).match()
    return match to match.instructionMatches.last().index
}

private fun Match.addStoryBuilder(body: MethodImplementation): MutableMethod {
    val original = originalMethod
    val method = MutableMethod(
        ImmutableMethod(
            original.definingClass, "pikoBuildStoryRead", emptyList(), original.returnType,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, emptySet(), emptySet(), body,
        ),
    )
    classDef.methods.add(method)
    return method
}
