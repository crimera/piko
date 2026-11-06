/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.saveMessages

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.getReference
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * Calls [hookMethod], which takes the item as an Object, with every DirectItem parsed from
 * REST/JSON. Injected at the return of the parser.
 */
context(patchContext: BytecodePatchContext)
internal fun hookParsedDirectItems(hookMethod: String) {
    DirectItemJsonParserFingerprint.let {
        val returnObject = it.instructionMatches.last()
        val itemRegister = returnObject.getInstruction<OneRegisterInstruction>().registerA

        it.method.addInstructionsAtControlFlowLabel(
            returnObject.index,
            "invoke-static {v$itemRegister}, $hookMethod",
        )
    }
}

/**
 * Calls [hookMethod], taking the item, the user session and the delta, with every DirectItem
 * from the MQTT/MSys real-time path, which REST never touches. Hooked at entry because normal
 * branches jump directly to return instructions and skip code inserted before them.
 *
 * @return The name of the delta's thread id field, for messages that do not carry their thread.
 */
context(patchContext: BytecodePatchContext)
internal fun hookRealtimeDirectItems(hookMethod: String): String {
    val postprocess = DirectItemPostprocessFingerprint.method
    val deltaType = postprocess.parameterTypes[1].toString()
    val deltaStringFields =
        patchContext.classDefBy(deltaType)
            .fields.filter { field ->
                field.type == "Ljava/lang/String;" &&
                    !AccessFlags.STATIC.isSet(field.accessFlags)
            }
    if (deltaStringFields.size != 1) {
        throw PatchException(
            "Expected one delta thread-id field in $deltaType, " +
                "found ${deltaStringFields.size}",
        )
    }

    postprocess.addInstructions(0, "invoke-static/range {p0 .. p2}, $hookMethod")
    return deltaStringFields.single().name
}

/**
 * Calls [hookMethod], which takes a List, with the participants Instagram parses for a thread.
 * Optional, so nothing is hooked when the dispatch is not found.
 */
context(patchContext: BytecodePatchContext)
internal fun hookThreadUsers(hookMethod: String, threadHookMethod: String? = null) {
    ThreadUsersDispatchFingerprint.matchOrNull()?.let { match ->
        match.method.apply {
            // The list write is out of line, after other JSON fields. Follow the
            // array guard to its completion block instead of selecting the next list write.
            // Searched again rather than taken from the match: other hooks in this method move indices.
            val usersString = indexOfConstString("users")
            val guardIndex = (usersString + 1 until minOf(usersString + 11, instructions.size)).firstOrNull {
                instructions[it].opcode == Opcode.IF_NE
            } ?: throw PatchException("Missing the users array guard of the thread parser")
            val arrayGuard = getInstruction<BuilderOffsetInstruction>(guardIndex)
            val putIndex = indexOfFirstInstructionOrThrow(
                arrayGuard.target.location.index,
                fieldAccess(
                    definingClass = parameterTypes.first().toString(),
                    type = "Ljava/util/List;",
                    opcode = Opcode.IPUT_OBJECT,
                ),
            )
            val parsedUsersRegister = getInstruction<TwoRegisterInstruction>(putIndex).registerA
            val registers = getFreeRegisterProvider(putIndex + 1, if (threadHookMethod != null) 2 else 1)
            val usersRegister = registers.getFreeRegister()
            val threadHook =
                if (threadHookMethod == null) {
                    ""
                } else {
                    // Also hands over the thread being parsed (its first parameter) with its users.
                    val threadRegister = registers.getFreeRegister()
                    val threadParameter = if (AccessFlags.STATIC.isSet(accessFlags)) "p0" else "p1"
                    "\nmove-object/from16 v$threadRegister, $threadParameter\n" +
                        "invoke-static {v$threadRegister, v$usersRegister}, $threadHookMethod"
                }
            addInstructions(
                putIndex + 1,
                "move-object/from16 v$usersRegister, v$parsedUsersRegister\n" +
                    "invoke-static {v$usersRegister}, $hookMethod" + threadHook,
            )
        }
    }
}

/**
 * Calls [idHook] (Object thread, String id) and [imageHook] (Object thread, Object image) right
 * after the thread parser stores the thread's id and its photo. The parser is called once per JSON
 * field, so each hook sees the thread object it belongs to. Optional: nothing is hooked when the
 * parser or a field write is not found.
 */
context(patchContext: BytecodePatchContext)
internal fun hookThreadFields(idHook: String, imageHook: String): Boolean {
    val method = ThreadUsersDispatchFingerprint.matchOrNull()?.method ?: return false
    method.apply {
        fun putAfter(key: String, type: String?): Int? {
            val start = indexOfConstStringOrNull(key) ?: return null
            return (start + 1 until minOf(start + 16, instructions.size)).firstOrNull {
                val instruction = instructions[it]
                if (instruction.opcode == Opcode.RETURN_VOID) return null
                instruction.opcode == Opcode.IPUT_OBJECT &&
                    (type == null || instruction.getReference<FieldReference>()?.type == type)
            }
        }
        // Found before inserting anything, then inserted from the bottom up so indices stay valid.
        val idPut = putAfter("thread_id", "Ljava/lang/String;") ?: return false
        val imagePut = putAfter("thread_image", null) ?: return false
        val hooks = listOf(idPut to idHook, imagePut to imageHook).sortedByDescending { it.first }
        for ((index, _) in hooks) {
            val put = getInstruction<TwoRegisterInstruction>(index)
            if (put.registerA > 0xF || put.registerB > 0xF) return false
        }
        for ((index, hook) in hooks) {
            val put = getInstruction<TwoRegisterInstruction>(index)
            addInstructions(index + 1, "invoke-static {v${put.registerB}, v${put.registerA}}, $hook")
        }
    }
    return true
}

private fun com.android.tools.smali.dexlib2.iface.Method.indexOfConstStringOrNull(value: String): Int? {
    val all = implementation?.instructions?.toList() ?: return null
    return all.indexOfFirst {
        (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
            it.getReference<StringReference>()?.string == value
    }.takeIf { it >= 0 }
}

private fun com.android.tools.smali.dexlib2.iface.Method.indexOfConstString(value: String): Int =
    indexOfConstStringOrNull(value) ?: throw PatchException("Missing the string \"$value\" in $name")
