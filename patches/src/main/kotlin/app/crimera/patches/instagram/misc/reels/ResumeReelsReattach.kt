/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.reels

import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31c
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val PLAYBACK = "$PATCHES_DESCRIPTOR/ReelsPlayback;"

private val Method.calls get() = implementation!!.instructions.mapNotNull { it.getReference<MethodReference>() }

private fun scrollInReason(resume: MethodReference): StringReference {
    val caller = Fingerprint(
        definingClass = resume.definingClass, returnType = "V",
        filters = listOf(string("android_purge_26_q3_ClipsVideoPlayerController_maybeResumeScrolledInPlayer")),
    ).method
    val code = caller.implementation!!.instructions
    val call = code.indices.single { code[it].getReference<MethodReference>() == resume }
    val (_, _, _, reason) = code[call].registersUsed // receiver, item, holder, reason
    for (index in call - 1 downTo 0) {
        val instruction = code[index]
        if (code[index + 1].location.labels.isNotEmpty() || !instruction.opcode.canContinue()) break
        if (!instruction.opcode.setsRegister()) continue
        val destination = instruction.registersUsed.first()
        if (destination == reason) return instruction.getReference<StringReference>() ?: break
        if (instruction.opcode.setsWideRegister() && destination + 1 == reason) break
    }
    throw PatchException("Unable to identify the scroll-in reason passed to the Reels player")
}

internal fun hookReelReattach(start: ReelStartPosition) {
    val explicitStart = start.explicit
    val resume = Fingerprint(
        returnType = "V",
        definingClass = explicitStart.definingClass,
        filters = listOf(string("android_purge_26_q3_ClipsVideoPlayerController_maybeResumePlayer")),
    ).method
    val snapshot = ReelItemSnapshot.originalMethod
    val item = snapshot.parameterTypes.single().toString()
    require(snapshot.returnType == start.stopped.definingClass)
    val seek = Fingerprint(
        parameters = listOf("I", "Z", "Z"), returnType = "V",
        filters = listOf(string("android_purge_26_q3_ClipsVideoPlayer_seekTo")),
    ).method
    val player = seek.definingClass
    val pause = Fingerprint(
        definingClass = player, returnType = "I", parameters = listOf("Ljava/lang/String;"),
        filters = listOf(string("android_purge_26_q3_ClipsVideoPlayer_pause")),
    ).method
    val prepare = ReelPlayerPrepare.method
    require(prepare.definingClass == player)
    val wrapperResume = Fingerprint(
        definingClass = player, returnType = "Z", parameters = listOf("Ljava/lang/String;", "Z"),
        filters = listOf(string("android_purge_26_q3_ClipsVideoPlayer_resume")),
    ).method
    val nativeSeek = seek.calls.single { it.parameterTypes == seek.parameterTypes && it.returnType == "V" }
    val core = seek.implementation!!.instructions.mapNotNull { it.getReference<FieldReference>() }
        .single { it.definingClass == player && it.type == nativeSeek.definingClass }
    val position = pause.calls.single { it.definingClass == core.type && it.parameterTypes.isEmpty() && it.returnType == "I" }
    val coreMethods = classDefBy(core.type).methods
    val positionGetter = coreMethods.single { it == position }
    val backendPosition = positionGetter.calls.single {
        it.definingClass != core.type && it.parameterTypes.isEmpty() && it.returnType == "I"
    }
    val backend = positionGetter.implementation!!.instructions.mapNotNull { it.getReference<FieldReference>() }
        .single { it.definingClass == core.type && it.type == backendPosition.definingClass }
    // Native seek calls the same position getter as pause, and also reads the player's duration.
    val duration = coreMethods.single { it == nativeSeek }.calls.distinct().single {
        it.definingClass == core.type && it.returnType == "I" && it.parameterTypes.isEmpty() && it != position
    }

    val instructions = resume.implementation!!.instructions
    val setStart = instructions.indexOfFirst {
        it.getReference<StringReference>()?.string == "android_purge_26_q2_ClipsStandaloneAdapterDataSource_setCustomStartTimeMs"
    }
    val customStart = instructions.getOrNull(setStart + 2)?.takeIf { setStart >= 0 && it.opcode == Opcode.IPUT }
        ?.getReference<FieldReference>()?.takeIf { it.definingClass == item && it.type == "I" }
        ?: throw PatchException("Missing Reels custom start time in the resume path")
    val applyIndex = instructions.indices.single { index ->
        instructions[index].getReference<MethodReference>() == snapshot &&
            instructions[index + 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
            instructions[index + 2].opcode == Opcode.IGET_BOOLEAN &&
            instructions[index + 2].getReference<FieldReference>()?.definingClass == item
    }
    val resumeCall = instructions.indices.single { index ->
        instructions[index].getReference<MethodReference>()?.let {
            it.name == wrapperResume.name && it.parameterTypes == wrapperResume.parameterTypes && it.returnType == "Z"
        } == true
    }
    val (playerRegister, reasonRegister) = instructions[resumeCall].registersUsed
    val playerInterface = instructions[resumeCall].getReference<MethodReference>()!!.definingClass
    val playerLookup = (0 until applyIndex).last { index ->
        instructions[index].getReference<MethodReference>()?.returnType == playerInterface &&
            instructions[index + 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
            instructions[index + 1].registersUsed.single() == playerRegister
    }
    val itemRegister = instructions[applyIndex].registersUsed.last()
    val controllerGetter = instructions[applyIndex - 3]
    val controllerRegister = controllerGetter.registersUsed.single()
    val provider = controllerGetter.getReference<MethodReference>()
    val adapter = instructions[applyIndex - 1].getReference<FieldReference>()
    if (provider?.definingClass != resume.definingClass || provider.parameterTypes.isNotEmpty() ||
        adapter?.definingClass != provider.returnType || adapter.type != snapshot.definingClass ||
        instructions[applyIndex + 2].registersUsed.last() != itemRegister ||
        resume.parameterTypes[0] != item || resume.parameterTypes[2] != "Ljava/lang/String;" ||
        instructions[playerLookup].getReference<MethodReference>()!!.parameterTypes != listOf(resume.parameterTypes[1]) ||
        instructions[playerLookup + 2].opcode != Opcode.IF_NEZ ||
        instructions[playerLookup + 2].registersUsed != listOf(playerRegister) ||
        explicitStart.parameterTypes != listOf(item) || explicitStart.returnType != "Z" ||
        resumeCall <= applyIndex ||
        listOf(itemRegister, playerRegister, reasonRegister, controllerRegister).toSet().size != 4 ||
        listOf(itemRegister, playerRegister, reasonRegister, controllerRegister).any { it > 15 }
    ) throw PatchException("Unexpected Reels player, item or snapshot in the resume path")

    val helper = addReelMethod(
        resume.definingClass, "pikoRestoreReattachedReel", listOf("Ljava/lang/Object;", item, "Ljava/lang/String;"),
        "V", registers = 9, flags = AccessFlags.PRIVATE.value,
        instructions = """
            invoke-virtual {v0, p3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :done
            invoke-static {p0, p1, p2}, $PLAYBACK->takeReattachMode(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)I
            move-result v4
            if-eqz v4, :done
            instance-of v0, p1, $player
            if-eqz v0, :done
            invoke-virtual {p0}, $provider
            move-result-object v3
            iget-object v3, v3, $adapter
            invoke-virtual {v3, p2}, $snapshot
            move-result-object v3
            iget-object v0, v3, ${start.paused}
            if-nez v0, :done
            iget v0, p2, $customStart
            if-gtz v0, :done
            invoke-virtual {p0, p2}, $explicitStart
            move-result v0
            if-nez v0, :done
            iget-object v0, v3, ${start.stopped}
            if-eqz v0, :done
            check-cast p1, $player
            iget-object v1, p1, $core
            if-eqz v1, :done
            iget-object v2, v1, $backend
            if-eqz v2, :done
            invoke-virtual {v2}, $backendPosition
            move-result v2
            invoke-virtual {v1}, $duration
            move-result v1
            invoke-static {v0, v2, v1, v4}, $PLAYBACK->reattachPosition(Ljava/lang/Integer;III)I
            move-result v0
            if-lez v0, :done
            const/4 v1, 0x0
            # Request an exact seek so a short stop cannot round back to the first keyframe.
            const/4 v2, 0x1
            invoke-virtual {p1, v0, v1, v2}, $seek
            :done
            return-void
        """,
    )
    helper.addInstruction(0, BuilderInstruction31c(Opcode.CONST_STRING_JUMBO, 0, scrollInReason(resume)))
    val discard = "invoke-static/range {p0 .. p0}, $PLAYBACK->discardReattach(Ljava/lang/Object;)V"
    // The earlier snapshot is also read on paths that reject playback. Only correct an actual resume.
    resume.addInstructions(resumeCall, "invoke-direct {v$controllerRegister, v$playerRegister, v$itemRegister, v$reasonRegister}, $helper")
    // Native explicit seeks reset the custom start field before the resume call.
    resume.addInstructions(setStart + 3, "invoke-static/range {v$playerRegister .. v$playerRegister}, $PLAYBACK->discardReattach(Ljava/lang/Object;)V")
    // prepareVideo can return before binding anything when its fragment is paused.
    val bindItem = prepare.implementation!!.instructions.withIndex().single { (_, instruction) ->
        instruction.opcode == Opcode.IPUT_OBJECT && instruction.getReference<FieldReference>()?.let {
            it.definingClass == player && it.type == item
        } == true
    }.index
    prepare.addInstructions(bindItem + 1, discard)
    seek.addInstructions(0, discard)
}
