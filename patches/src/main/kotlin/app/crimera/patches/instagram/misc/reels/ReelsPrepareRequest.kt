/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.reels

import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField

context(context: BytecodePatchContext)
private fun addResumeStartField(type: String): ImmutableField {
    val field = ImmutableField(type, "pikoResumeStartMs", "I",
        AccessFlags.PUBLIC.value or AccessFlags.SYNTHETIC.value, null, emptySet(), emptySet())
    context.mutableClassDefBy(type).fields.add(field.toMutable())
    return field
}

context(context: BytecodePatchContext)
private fun patchShortReelPrepare() = Fingerprint(
    returnType = "V", parameters = listOf("L"),
    filters = listOf(
        string("IgVideoPlayerImpl.prepareVideo autoPlay:"),
        fieldAccess(type = "Z", opcode = Opcode.IGET_BOOLEAN),
        opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
        fieldAccess(type = "I", definingClass = "this", opcode = Opcode.IGET, location = MatchAfterImmediately()),
        opcode(Opcode.IF_GE, location = MatchAfterImmediately()),
        opcode(Opcode.CONST_4, location = MatchAfterImmediately()),
    ),
).match().let { match ->
    val hits = match.instructionMatches
    val method = match.method
    val requestType = method.parameterTypes.single().toString()
    val marker = addResumeStartField(requestType)
    val (position, floor) = hits[4].instruction.registersUsed
    val clear = hits[5].index
    require(method.getInstruction<BuilderOffsetInstruction>(hits[2].index).target.location.index == clear + 1 &&
        method.getInstruction<BuilderOffsetInstruction>(hits[4].index).target.location.index == clear + 1 &&
        hits[3].instruction.registersUsed.first() == floor && hits[5].instruction.registersUsed.single() == position &&
        (hits[5].instruction as NarrowLiteralInstruction).narrowLiteral == 0 && position != floor)
    // This comparison can run again when Instagram defers the same prepare request.
    method.addInstructionsWithLabels(clear, """
        move-object/from16 v$floor, p1
        iget v$floor, v$floor, $marker
        if-ne v$floor, v$position, :clear
        if-lez v$position, :clear
        $PREF_CALL_DESCRIPTOR->resumeReelsAfterScrolling()Z
        move-result v$floor
        if-nez v$floor, :keep
    """, ExternalLabel("clear", method.getInstruction(clear)), ExternalLabel("keep", method.getInstruction(clear + 1)))
    marker
}

context(context: BytecodePatchContext)
internal fun markShortReelPrepare(
    controller: MutableMethod, explicit: MethodReference, stop: FieldReference, pausePredicate: MethodReference,
) {
    val requestMark = patchShortReelPrepare()
    val prepare = ReelPlayerPrepare.method
    val configType = prepare.parameterTypes[3].toString()
    val stateType = stop.definingClass
    require(prepare.parameterTypes[1].toString() == explicit.parameterTypes.single().toString() &&
        prepare.parameterTypes[2].toString() == stateType) { "Unexpected Reels prepare signature: $prepare, $explicit, $stop" }
    val configMark = addResumeStartField(configType)
    val mark = addReelMethod(
        controller.definingClass, "pikoMarkReelPrepare",
        listOf(explicit.parameterTypes.single().toString(), stateType, configType), "V", registers = 6,
        instructions = """
            invoke-virtual {p2}, $pausePredicate
            move-result v0
            if-nez v0, :done
            iget-object v1, p2, $stop
            if-eqz v1, :done
            invoke-static {p0, p1, v1}, $PATCHES_DESCRIPTOR/ReelsPlayback;->savedStopState(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Integer;)I
            move-result v0
            if-lez v0, :done
            invoke-virtual {p0, p1}, $explicit
            move-result v0
            if-nez v0, :done
            invoke-virtual {v1}, Ljava/lang/Integer;->intValue()I
            move-result v0
            iput v0, p3, $configMark
            :done
            return-void
        """,
    )
    val calls = controller.implementation!!.instructions
    val prepareCall = calls.single { it.getReference<MethodReference>()?.let { ref ->
        ref.name == prepare.name && ref.parameterTypes == prepare.parameterTypes
    } == true }
    val args = prepareCall.registersUsed
    val factory = calls.indices.single { calls[it].opcode == Opcode.INVOKE_STATIC &&
        calls[it].getReference<MethodReference>()?.returnType == configType &&
        calls[it + 1].registersUsed.singleOrNull() == args[4] && calls[it + 1].opcode == Opcode.MOVE_RESULT_OBJECT }
    val setup = calls.subList(factory + 2, calls.indexOf(prepareCall))
    fun source(argument: Int) = setup.single { it.opcode == Opcode.MOVE_OBJECT_FROM16 &&
        it.registersUsed.first() == args[argument] }.registersUsed.last()
    val temp = setup.first().registersUsed.first()
    val owner = calls[factory - 2].registersUsed.last()
    require(calls[factory - 2].opcode == Opcode.IGET_OBJECT &&
        calls[factory - 2].getReference<FieldReference>()?.definingClass == controller.definingClass &&
        setup.first().opcode == Opcode.MOVE_FROM16 && temp != setup.first().registersUsed.last() &&
        listOf(owner, source(2), source(3), temp).distinct().size == 4)
    controller.addInstructions(factory + 2, """
        move-object/from16 v$temp, v${args[4]}
        invoke-direct {v$owner, v${source(2)}, v${source(3)}, v$temp}, $mark
    """)

    // Reels copies the player configuration. Transfer the mark from the original argument to this request.
    val start = prepare.implementation!!.instructions
    val invoke = start.indices.single { start[it].opcode == Opcode.INVOKE_VIRTUAL &&
        start[it].getReference<MethodReference>()?.parameterTypes == listOf(requestMark.definingClass) &&
        start[it - 1].getReference<MethodReference>()?.definingClass == requestMark.definingClass &&
        start[it - 1].getReference<MethodReference>()?.name == "<init>" }
    val request = start[invoke].registersUsed.last()
    val ctorArgs = start[invoke - 1].registersUsed
    require(start[invoke - 2].registersUsed == listOf(ctorArgs[0], request) && ctorArgs[1] == ctorArgs[0] + 1)
    val copy = addReelMethod(
        requestMark.definingClass, "pikoCopyReelStart", listOf(configType), "V", registers = 3,
        flags = AccessFlags.PUBLIC.value or AccessFlags.SYNTHETIC.value,
        instructions = """
            iget v0, p1, $configMark
            iput v0, p0, $requestMark
            return-void
        """,
    )
    prepare.addInstructions(invoke, """
        move-object/from16 v${ctorArgs[1]}, p4
        invoke-virtual/range {v${ctorArgs[0]} .. v${ctorArgs[1]}}, $copy
    """)
}
