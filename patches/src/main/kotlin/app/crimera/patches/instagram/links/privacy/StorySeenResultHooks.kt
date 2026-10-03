/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.shared.parameterRegisterStart
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val RESULT_METHOD = "pikoStorySeenSucceeded"

internal fun addStorySeenResultHooks(
    requestBuilder: MutableMethod,
    extensionMethod: MutableMethod,
    resolveClass: (String) -> MutableClass,
) {
    val requestType = requestBuilder.returnType
    val factoryReference = uniqueMethodReference(requestBuilder, "story request factory") {
        it.parameterTypes.isEmpty() && it.returnType == requestType
    }
    val factory = resolveClass(factoryReference.definingClass).methods.single {
        it.matches(factoryReference)
    }
    val requestClasses = factory.instructions.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.RETURN_OBJECT) return@mapIndexedNotNull null
        val register = instruction.registersUsed.single()
        val allocation = factory.instructions.take(index).lastOrNull {
            it.opcode.setsRegister() && it.registersUsed.firstOrNull() == register
        }
        if (allocation?.opcode != Opcode.NEW_INSTANCE) {
            throw PatchException("Could not trace the returned story request allocation")
        }
        resolveClass(allocation.getReference<TypeReference>()!!.type).also {
            if (it.superclass != requestType) throw PatchException("Unexpected native story request superclass")
        }
    }.distinctBy { it.type }
    if (requestClasses.size != 2) throw PatchException("Expected the two native story request implementations")

    fun runMethod(owner: MutableClass) = owner.methods.singleOrNull {
        it.name == "run" && it.parameterTypes.isEmpty() && it.returnType == "V"
    } ?: throw PatchException("Missing native story request run method")

    val regular = requestClasses.singleOrNull { owner ->
        runMethod(owner).instructions.any {
            it.getReference<MethodReference>()?.toString() == "Ljava/lang/Runnable;->run()V"
        }
    } ?: throw PatchException("Could not identify the regular story request")
    val regularRun = runMethod(regular)
    val successType = regularRun.instructions.mapNotNull {
        if (it.opcode != Opcode.INSTANCE_OF) return@mapNotNull null
        it.getReference<TypeReference>()?.type?.takeIf { type ->
            resolveClass(type).methods.any { method -> method.hasString("Success(value=") }
        }
    }.distinct().singleOrNull() ?: throw PatchException("Could not identify the native successful request result")
    val resultGetter = regularRun.instructions.mapIndexedNotNull { index, instruction ->
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        val cast = regularRun.instructions.getOrNull(index + 2)
        reference.takeIf {
            instruction.opcode == Opcode.INVOKE_VIRTUAL && it.parameterTypes.isEmpty() &&
                it.returnType == "Ljava/lang/Object;" &&
                regularRun.instructions.getOrNull(index + 1)?.opcode == Opcode.MOVE_RESULT_OBJECT &&
                cast?.opcode == Opcode.CHECK_CAST &&
                cast.getReference<TypeReference>()?.type == resolveClass(successType).superclass
        }
    }.singleOrNull() ?: throw PatchException("Could not identify the completed request result getter")
    val taskField = regular.fields.singleOrNull { it.type == resultGetter.definingClass }
        ?: throw PatchException("Could not identify the regular request result task")
    addResultMethod(resolveClass(requestType), "const/4 v0, 0x0\nreturn v0")
    addResultMethod(regular, """
        iget-object v0, p0, $taskField
        invoke-virtual {v0}, $resultGetter
        move-result-object v0
        instance-of v0, v0, $successType
        return v0
    """.trimIndent())

    val streaming = requestClasses.single { it.type != regular.type }
    val streamingRun = runMethod(streaming)
    val streamRunReference = uniqueMethodReference(streamingRun, "streaming story request task") {
        it.name == "run" && it.parameterTypes.isEmpty() && it.returnType == "V"
    }
    val stream = resolveClass(streamRunReference.definingClass)
    val streamField = streaming.fields.singleOrNull { it.type == stream.type }
        ?: throw PatchException("Could not identify the streaming request task field")
    val responseMethod = stream.methods.singleOrNull { it.hasString("StreamingHttpRequestTask.onNewData ") }
        ?: throw PatchException("Could not identify streaming response handling")
    val predicateIndex = responseMethod.instructions.indices.singleOrNull { index ->
        val instruction = responseMethod.instructions[index]
        val call = instruction.getReference<MethodReference>()
        instruction.opcode == Opcode.INVOKE_INTERFACE && call?.parameterTypes?.isEmpty() == true &&
            call.returnType == "Z" && call.definingClass != "Ljava/util/Iterator;"
    } ?: throw PatchException("Could not identify streaming response success check")
    val result = responseMethod.instructions.getOrNull(predicateIndex + 1)
    val receiver = parameterRegisterStart(responseMethod)
    val resultRegister = result?.registersUsed?.singleOrNull()
    if (result?.opcode != Opcode.MOVE_RESULT || resultRegister == null ||
        resultRegister !in 0..15 || receiver !in 0..15 || resultRegister == receiver
    ) throw PatchException("Unsupported streaming response result registers")
    val failureReference = uniqueMethodReference(responseMethod, "streaming response failure handler") {
        it.definingClass == stream.type && it.returnType == "V" && it.parameterTypes.size == 1
    }
    val failureMethod = stream.methods.single { it.matches(failureReference) }
    val cancelMethod = stream.methods.singleOrNull { it.name == "onCancel" && it.parameterTypes.isEmpty() }
        ?: throw PatchException("Could not identify streaming cancellation")
    val succeeded = addResultField(stream, "pikoStorySeenResponseOk")
    val failed = addResultField(stream, "pikoStorySeenResponseFailed")
    // Streaming responses are processed on a different thread. Keep their outcome on the task,
    // then read it only after run() has finished waiting for the response.
    responseMethod.addInstruction(predicateIndex + 2, "iput-boolean v$resultRegister, v$receiver, $succeeded")
    prependResultFlag(runMethod(stream), listOf(succeeded, failed), false)
    prependResultFlag(failureMethod, listOf(failed), true)
    prependResultFlag(cancelMethod, listOf(failed), true)
    addResultMethod(streaming, """
        iget-object v0, p0, $streamField
        iget-boolean v1, v0, $failed
        if-nez v1, :piko_story_request_failed
        iget-boolean v0, v0, $succeeded
        return v0
        :piko_story_request_failed
        const/4 v0, 0x0
        return v0
    """.trimIndent())
    extensionMethod.addInstructions(0, """
        instance-of p1, p0, $requestType
        if-eqz p1, :piko_story_result_unavailable
        check-cast p0, $requestType
        invoke-virtual {p0}, $requestType->$RESULT_METHOD()Z
        move-result p1
        return p1
        :piko_story_result_unavailable
        const/4 p1, 0x0
        return p1
    """.trimIndent())
}

private fun addResultMethod(owner: MutableClass, code: String) {
    if (owner.methods.any { it.name == RESULT_METHOD }) throw PatchException("Duplicate story request result method")
    val method = MutableMethod(ImmutableMethod(
        owner.type, RESULT_METHOD, emptyList(), "Z", AccessFlags.PUBLIC.value or AccessFlags.SYNTHETIC.value,
        emptySet(), emptySet(), ImmutableMethodImplementation(3, emptyList(), emptyList(), emptyList()),
    ))
    method.addInstructions(0, code)
    owner.methods.add(method)
}

private fun addResultField(owner: MutableClass, name: String): FieldReference {
    if (owner.fields.any { it.name == name }) throw PatchException("Duplicate story request result field")
    val field = ImmutableField(owner.type, name, "Z",
        AccessFlags.PUBLIC.value or AccessFlags.VOLATILE.value or AccessFlags.SYNTHETIC.value,
        null, emptySet(), emptySet()).toMutable()
    owner.fields.add(field)
    return field
}

private fun prependResultFlag(method: MutableMethod, fields: List<FieldReference>, value: Boolean) {
    val first = method.instructions.first()
    val receiver = parameterRegisterStart(method)
    val scratch = first.registersUsed.firstOrNull()
    if (!first.opcode.setsRegister() || scratch == null || scratch !in 0 until receiver ||
        scratch > 15 || receiver !in 0..15
    ) throw PatchException("Unsupported streaming result flag registers")
    method.addInstructions(0, "const/16 v$scratch, ${if (value) "0x1" else "0x0"}\n" +
        fields.joinToString("\n") { "iput-boolean v$scratch, v$receiver, $it" })
}
