/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.reels

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.entity.mediadata.MediaIsVideoFingerprint
import app.crimera.patches.instagram.entity.mediadata.mediaVideoGetterFingerprint
import app.crimera.patches.instagram.misc.overflowMenuButton.reels.ClipsItemStateToStringFingerprint
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val BOXED_INT = "Ljava/lang/Integer;"
private const val BOXED_DOUBLE = "Ljava/lang/Double;"
private const val OBJECT = "Ljava/lang/Object;"

internal fun addReelMethod(
    owner: String, name: String, parameters: List<String>, returnType: String,
    registers: Int, flags: Int = AccessFlags.PRIVATE.value or AccessFlags.SYNTHETIC.value,
    instructions: String,
) = ImmutableMethod(
    owner, name, parameters.map { ImmutableMethodParameter(it, null, null) }, returnType,
    flags, emptySet(), emptySet(), MutableMethodImplementation(registers),
).toMutable().apply {
    addInstructionsWithLabels(0, instructions)
    mutableClassDefBy(owner).methods.add(this)
}

internal object ReelPlayerPrepare : Fingerprint(
    returnType = "Z", filters = listOf(string("ClipsVideoPlayer.prepareVideo autoPlayEnabled=")),
)

private object OrganicReelType : Fingerprint(
    name = "<clinit>",
    filters = listOf(
        string("ORGANIC"),
        fieldAccess(opcode = Opcode.SPUT_OBJECT),
        string("AD_PREVIEW", location = MatchAfterImmediately()),
        string("STORIES_IN_REELS"),
    ),
)

private object VideoDurationSeconds : Fingerprint(
    definingClass = MEDIA_CLASS_NAME,
    parameters = emptyList(),
    returnType = BOXED_DOUBLE,
    filters = listOf(string("video_duration")),
)

private fun scrollOutFingerprint(stateType: String) = Fingerprint(
    parameters = listOf("L"),
    returnType = "V",
    filters = listOf(
        string("out_of_playback_range"),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = listOf("Ljava/lang/String;"), returnType = "I",
            location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        methodCall(smali = "$BOXED_INT->valueOf(I)$BOXED_INT", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = emptyList(), returnType = "L",
            location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        string("AD_OPTIMIZATION_GOAL_VIDEO_VIEWS"),
        fieldAccess(definingClass = stateType, type = BOXED_INT, opcode = Opcode.IGET_OBJECT),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = listOf("L", BOXED_INT),
            returnType = "V", location = MatchAfterImmediately()),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = listOf("L", BOXED_INT), returnType = "V",
            location = MatchAfterImmediately()),
        string("AD_OPTIMIZATION_GOAL_MRC_VIDEO_VIEWS"),
        fieldAccess(definingClass = "this", opcode = Opcode.IGET_OBJECT),
        methodCall(opcode = Opcode.INVOKE_INTERFACE, parameters = listOf("L", BOXED_INT),
            returnType = "V", location = MatchAfterImmediately()),
        opcode(Opcode.GOTO, MatchAfterImmediately()),
    ),
)

private fun trackReelSelection(pauseOthers: MethodReference, itemType: String) {
    val pager = Fingerprint(
        definingClass = pauseOthers.definingClass, parameters = listOf("Z"), returnType = "V",
        filters = listOf(
            string("ViewerNotCompletelyVisible"),
            methodCall(parameters = emptyList(), returnType = itemType),
            opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
            methodCall(smali = pauseOthers.toString()),
        ),
    ).match()
    val (_, _, selected, pause) = pager.instructionMatches
    val item = selected.instruction.registersUsed.single()
    val viewer = pause.instruction.registersUsed.first()
    val instructions = pager.method.implementation!!.instructions
    require(item != viewer && item in 0..15 && viewer in 0..15 &&
        (selected.index + 1 until pause.index).none { item in instructions[it].registersUsed })
    pager.method.addInstruction(pause.index + 1,
        "invoke-static {v$viewer, v$item}, $PATCHES_DESCRIPTOR/ReelsPlayback;->onReelSelected($OBJECT$OBJECT)V")
}

private fun rememberScrolledOutPosition(start: ReelStartPosition, reader: ReelStateReader) {
    val stoppedField = start.stopped
    val pause = scrollOutFingerprint(ClipsItemStateToStringFingerprint.originalClassDef.type)
    val matches = pause.instructionMatches
    val player = matches[5].instruction.registersUsed.single()
    val stopped = matches[4].instruction.registersUsed.single()
    val item = matches[6].instruction.registersUsed.single()
    val (store, controller) = matches[12].instruction.registersUsed
    val write = matches[13]
    val args = write.instruction.registersUsed
    val adArgs = matches[9].instruction.registersUsed
    // Ads keep their own stopped position. Both paths then clear the separate user-pause field.
    if (args.size != 3 || adArgs.size != 3 || matches[1].instruction.registersUsed.first() != player ||
        matches[1].methodReference.definingClass != matches[5].methodReference.definingClass ||
        matches[2].instruction.registersUsed != matches[3].instruction.registersUsed ||
        matches[9].methodReference != write.methodReference ||
        args[0] != store || args[1] != item || adArgs != listOf(store, item, stopped) || args[2] == stopped ||
        matches[10].instruction.registersUsed != args
    ) throw PatchException("Unexpected Reels scroll-out position writes")

    val playerItem = matches[5].methodReference
    val itemClass = classDefBy(playerItem.returnType)
    val stateStore = matches[12].fieldReference
    val pausedField = matches[8].fieldReference
    val pauseSetter = matches[10].methodReference
    require(pausedField == start.paused && reader.snapshot.parameterTypes == listOf(itemClass.type))
    val organic = OrganicReelType.instructionMatches[1].fieldReference
    val type = itemClass.instanceFields.single { it.type == organic.type }
    val media = itemClass.instanceFields.single { it.type == MEDIA_CLASS_NAME }
    val isVideo = mediaVideoGetterFingerprint(MediaIsVideoFingerprint.instructionMatches.first().methodReference).originalMethod
    val getDuration = VideoDurationSeconds.originalMethod
    val eligible = addReelMethod(
        pause.method.definingClass, "pikoIsResumeVideo", listOf(itemClass.type), "Z", registers = 4,
        instructions = """
            $PREF_CALL_DESCRIPTOR->resumeReelsAfterScrolling()Z
            move-result v0
            if-eqz v0, :no
            iget-object v0, p1, $type
            sget-object v1, $organic
            if-ne v0, v1, :no
            iget-object v0, p1, $media
            if-eqz v0, :no
            invoke-virtual {v0}, $isVideo
            move-result v0
            return v0
            :no
            const/4 v0, 0x0
            return v0
        """,
    )
    val stop = addReelMethod(
        pause.method.definingClass, "pikoScrolledOutPosition",
        listOf(playerItem.definingClass, itemClass.type, BOXED_INT), BOXED_INT, registers = 11,
        instructions = """
            invoke-direct {p0, p2}, $eligible
            move-result v0
            if-eqz v0, :none
            iget-object v0, p2, $media
            invoke-virtual {v0}, $getDuration
            move-result-object v6
            invoke-virtual {p0}, ${reader.provider}
            move-result-object v5
            invoke-interface {v5, p2}, ${reader.snapshot}
            move-result-object v5
            iget-object v4, v5, $pausedField
            iget-object v5, v5, $stoppedField
            move-object v0, p0
            move-object v1, p1
            move-object v2, p2
            move-object v3, p3
            invoke-static/range {v0 .. v6}, $PATCHES_DESCRIPTOR/ReelsPlayback;->onScrollOut($OBJECT$OBJECT$OBJECT$BOXED_INT$BOXED_INT$BOXED_INT$BOXED_DOUBLE)$BOXED_INT
            move-result-object v0
            return-object v0
            :none
            const/4 v0, 0x0
            return-object v0
        """,
    )
    // Borrow the setter's null argument only for this call. The shared user-pause clear still needs null.
    val nullRegister = args[2]
    require(listOf(controller, player, item, stopped, nullRegister).all { it in 0..15 })
    pause.method.addInstruction(write.index + 1, "const/4 v$nullRegister, 0x0")
    pause.method.addInstructionsAtControlFlowLabel(write.index, """
        invoke-direct {v$controller, v$player, v$item, v$stopped}, $stop
        move-result-object v$nullRegister
    """)
    trackReelSelection(pause.method, itemClass.type)
    syncPausedReelSeek(eligible, stateStore, reader, playerItem, pausedField, pauseSetter)
}

@Suppress("unused")
val resumeReelsAfterScrollingPatch = patch(
    name = "Resume Reels after scrolling",
    description = "Resumes videos from where you left off when you scroll back to them in Reels",
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    dependsOn(settingsPatch)
    execute {
        val start = patchReelsStartPosition()
        val reader = hookReelReattach(start)
        rememberScrolledOutPosition(start, reader)
        enableSettings("resumeReelsAfterScrolling")
    }
}
