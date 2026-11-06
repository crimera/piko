/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.reels

import app.crimera.patches.instagram.misc.overflowMenuButton.reels.ClipsItemStateToStringFingerprint
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.patches.instagram.utils.methodReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal class ReelStartPosition(val explicit: MethodReference, val paused: FieldReference, val stopped: FieldReference)

context(_: BytecodePatchContext)
private fun trackDeferredReattach(prepare: MutableMethod, state: String, itemId: MethodReference) {
    val reattach = Fingerprint(
        parameters = listOf(state, prepare.parameterTypes[1].toString()), returnType = "Z",
        filters = listOf(string("android_purge_26_q3_ClipsVideoPlayer_resumeAfterDeferredRelease")),
    ).originalMethod
    val code = prepare.implementation!!.instructions
    val call = code.indices.single { index ->
        val ref = code[index].getReference<MethodReference>()
        code[index].opcode == Opcode.INVOKE_INTERFACE && ref != null && ref.name == reattach.name &&
            ref.parameterTypes == reattach.parameterTypes && ref.returnType == reattach.returnType
    }
    val player = code[call].registersUsed.first()
    val listener = code[call - 1]
    val (listenerPlayer, viewer) = listener.registersUsed
    val result = code[call + 1].registersUsed.single()
    val skip = code[call + 2] as BuilderOffsetInstruction
    val id = code[call + 3]
    val item = id.registersUsed.single()
    require(listener.opcode == Opcode.INVOKE_INTERFACE && listenerPlayer == player &&
        listener.getReference<MethodReference>()?.parameterTypes == listOf(prepare.definingClass) &&
        code[call + 1].opcode == Opcode.MOVE_RESULT && skip.opcode == Opcode.IF_EQZ &&
        skip.registersUsed == listOf(result) && skip.target.location.index > call + 3 &&
        id.opcode == Opcode.INVOKE_VIRTUAL && id.getReference<MethodReference>() == itemId &&
        listOf(viewer, player, item).all { it in 0..15 && it != result } &&
        setOf(viewer, player, item).size == 3) { "Unexpected deferred Reels reattach path" }
    // A retained player can start here while its reel is still offscreen.
    prepare.addInstruction(call + 3,
        "invoke-static {v$viewer, v$player, v$item}, $PATCHES_DESCRIPTOR/ReelsPlayback;->onPlayerReattached(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V")
}

context(context: BytecodePatchContext)
internal fun patchReelsStartPosition(): ReelStartPosition {
    val config = "Lcom/instagram/clips/intf/ClipsViewerConfig;"
    val match = Fingerprint(
        returnType = "V",
        filters = listOf(
            string("android_purge_26_q3_ClipsVideoPlayerController_preparePlayer"),
            fieldAccess(definingClass = ClipsItemStateToStringFingerprint.originalClassDef.type,
                type = "Ljava/lang/Integer;", opcode = Opcode.IGET_OBJECT),
            opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
            methodCall(definingClass = "this", parameters = listOf("L"), returnType = "Z",
                opcode = Opcode.INVOKE_DIRECT, location = MatchAfterImmediately()),
            string("android_purge_26_q3_ClipsVideoPlayerController_getForcedStartPositionMs"),
            fieldAccess(type = config, opcode = Opcode.IGET_OBJECT),
            fieldAccess(definingClass = config, type = "Z", opcode = Opcode.IGET_BOOLEAN, location = MatchAfterImmediately()),
            fieldAccess(definingClass = config, type = "Ljava/lang/String;", opcode = Opcode.IGET_OBJECT),
            methodCall(name = "getId", parameters = emptyList(), returnType = "Ljava/lang/String;"),
        ),
    ).match()
    val hits = match.instructionMatches
    val hasItemStart = hits[3].methodReference
    val itemType = hasItemStart.parameterTypes.single().toString()
    val itemId = hits[8].methodReference
    require(itemId.definingClass == itemType)
    val stop = hits[1].fieldReference
    val state = stop.definingClass
    val code = match.method.implementation!!.instructions
    val pausePredicate = code.mapNotNull { it.getReference<MethodReference>() }.distinct().single {
        it.definingClass == state && it.parameterTypes.isEmpty() && it.returnType == "Z"
    }
    val paused = context.classDefBy(state).methods.single { it == pausePredicate }.implementation!!.instructions
        .mapNotNull { it.getReference<FieldReference>() }.single()
    // Native prepare reads the manual pause first and falls back to the last stop.
    val positions = code.filter { it.opcode == Opcode.IGET_OBJECT }.mapNotNull { it.getReference<FieldReference>() }
        .filter { it.definingClass == state && it.type == stop.type }
    require(paused != stop && positions == listOf(paused, stop)) { "Unexpected pause and stop positions in Reels player preparation" }

    val explicit = addReelMethod(
        match.method.definingClass, "pikoHasExplicitReelStart", listOf(itemType), "Z", registers = 4,
        flags = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value or AccessFlags.SYNTHETIC.value,
        instructions = """
            invoke-direct {p0, p1}, $hasItemStart
            move-result v0
            if-nez v0, :yes
            iget-object v0, p0, ${hits[5].fieldReference}
            iget-boolean v1, v0, ${hits[6].fieldReference}
            if-eqz v1, :no
            iget-object v0, v0, ${hits[7].fieldReference}
            if-eqz v0, :yes
            invoke-virtual {p1}, $itemId
            move-result-object v1
            invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
            move-result v0
            return v0
            :no
            const/4 v0, 0x0
            return v0
            :yes
            const/4 v0, 0x1
            return v0
        """,
    )
    val (controller, item) = hits[3].instruction.registersUsed
    val stopped = hits[2].instruction.registersUsed.single()
    require(stopped == hits[1].instruction.registersUsed.first())
    match.method.apply {
        val index = hits[2].index
        // Both successors call a predicate and overwrite the same scratch register.
        val target = getInstruction<BuilderOffsetInstruction>(index).target.location.index
        val scratch = getInstruction(index + 2).registersUsed.single()
        require(getInstruction(index + 2).opcode == Opcode.MOVE_RESULT &&
                getInstruction(target).opcode == Opcode.INVOKE_VIRTUAL &&
                getInstruction(target + 1).opcode == Opcode.MOVE_RESULT &&
                getInstruction(target + 1).registersUsed.single() == scratch &&
                scratch !in getInstruction(target).registersUsed && scratch !in listOf(controller, item, stopped))
        addInstructionsWithLabels(index, """
            if-eqz v$stopped, :native
            invoke-static {v$controller, v$item, v$stopped}, $PATCHES_DESCRIPTOR/ReelsPlayback;->savedStopState(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Integer;)I
            move-result v$scratch
            if-ltz v$scratch, :native
            if-eqz v$scratch, :discard
            invoke-virtual {v$controller, v$item}, $explicit
            move-result v$scratch
            if-eqz v$scratch, :native
            :discard
            const/4 v$stopped, 0x0
        """, ExternalLabel("native", getInstruction(index)))
    }
    markShortReelPrepare(match.method, explicit, stop, pausePredicate)
    trackDeferredReattach(match.method, state, itemId)
    return ReelStartPosition(explicit, paused, stop)
}
