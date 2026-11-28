/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.utils

import app.crimera.patches.instagram.utils.Constants.UTILS_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode

private const val CALLABLE = "Ljava/util/concurrent/Callable;"

private object LocalRequestWrapper : Fingerprint(
    parameters = listOf("L"),
    returnType = "L",
    filters = listOf(
        string("path undefined"),
        opcode(Opcode.NEW_INSTANCE, location = MatchAfterImmediately()),
        methodCall(name = "<init>", parameters = listOf("L", "L", "Ljava/lang/String;", "Ljava/lang/String;"), location = MatchAfterImmediately()),
        opcode(Opcode.RETURN_OBJECT, location = MatchAfterImmediately()),
    ),
)

/** Lets a content-specific request factory deliver its ordinary offline result to its own callbacks. */
internal fun MutableMethod.returnOfflineRequestIf(index: Int, predicateCall: String) {
    val wrapper = LocalRequestWrapper.originalMethod
    if (returnType != wrapper.returnType && returnType != wrapper.definingClass) {
        throw PatchException("Expected a local content request factory: $this")
    }
    val factory = Fingerprint(
        parameters = listOf("L", CALLABLE),
        returnType = wrapper.returnType,
        filters = listOf(
            methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = listOf(CALLABLE, "I")),
            opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
            methodCall(reference = wrapper, location = MatchAfterImmediately()),
        ),
    ).originalMethod
    val taskFactory = Fingerprint(
        definingClass = wrapper.parameterTypes.single().toString(),
        name = "<clinit>",
        filters = listOf(fieldAccess(type = factory.parameterTypes.first().toString(), opcode = Opcode.SPUT_OBJECT)),
    ).instructionMatches.single().fieldReference
    val scratch = findFreeRegister(index)
    // Both temporaries on the disabled branch die at return. Only the predicate must
    // preserve live registers on the original branch.
    addInstructionsAtControlFlowLabel(
        index,
        """
        $predicateCall
        move-result v$scratch
        if-eqz v$scratch, :piko_original
        sget-object v0, $taskFactory
        sget-object v1, $UTILS_DESCRIPTOR/OfflineRequest;->REQUEST:$CALLABLE
        invoke-static {v0, v1}, $factory
        move-result-object v0
        return-object v0
        :piko_original
        nop
        """.trimIndent(),
    )
}
