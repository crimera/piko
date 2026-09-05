package app.crimera.patches.newx.misc.serverlogging

import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.action
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.EXTENSION_PACKAGE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val ERROR_PACKAGE = "Lcom/x/repositories/errors/"
private const val COMPOSER_WORK_SCOPE = "Lcom/x/composer/work/"
private const val EXCEPTION_DESCRIPTOR = "Ljava/lang/Exception;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val WORK_INPUT_DESCRIPTOR = "Landroidx/work/j;"
private const val FUNCTION_1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val FUNCTION_2_DESCRIPTOR = "Lkotlin/jvm/functions/Function2;"
private const val CONTINUATION_DESCRIPTOR =
    "Lkotlin/coroutines/jvm/internal/ContinuationImpl;"
private const val RESULT_FAILURE_DESCRIPTOR = "Lcom/x/result/b;"
private const val POST_FAILURE_FIELD_NAME = "POST_FAILURE"
private const val POST_SUCCESS_FIELD_NAME = "POST_SUCCESS"
private const val POST_OPERATION_FIELD_NAME = "Post"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val INTEGER_DESCRIPTOR = "Ljava/lang/Integer;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val THROWABLE_DESCRIPTOR = "Ljava/lang/Throwable;"
private const val SERVER_ERROR_HANDLER_DESCRIPTOR =
    "$EXTENSION_PACKAGE/settings/ServerLogExportAction;"
private const val LOGGER_DESCRIPTOR = "$EXTENSION_PACKAGE/settings/NewXLogger;"

private object SubmitWorkHandlerFingerprint : Fingerprint(
    definingClass = COMPOSER_WORK_SCOPE,
    returnType = OBJECT_DESCRIPTOR,
    filters = listOf(string("SubmitWorkHandler")),
    custom = { method, _ ->
        val parameterTypes = method.parameterTypes.map { type -> type.toString() }
        val instructions = method.implementation?.instructions?.toList().orEmpty()
        parameterTypes.size == 8 &&
            parameterTypes[0] == WORK_INPUT_DESCRIPTOR &&
            parameterTypes[2] == FUNCTION_2_DESCRIPTOR &&
            parameterTypes[3] == FUNCTION_2_DESCRIPTOR &&
            parameterTypes[4] == FUNCTION_1_DESCRIPTOR &&
            parameterTypes[5] == "Z" &&
            parameterTypes[6] == "I" &&
            parameterTypes[7] == CONTINUATION_DESCRIPTOR &&
            instructions.any { instruction ->
                instruction.getReference<FieldReference>()?.name == POST_FAILURE_FIELD_NAME
            } &&
            instructions.any { instruction ->
                val field = instruction.getReference<FieldReference>() ?: return@any false
                instruction.opcode == Opcode.IGET_OBJECT &&
                    field.definingClass.toString() == RESULT_FAILURE_DESCRIPTOR &&
                    field.type.toString() == THROWABLE_DESCRIPTOR
            }
    },
)

private data class RegisterLocation(
    val index: Int,
    val register: Int,
)

private data class ServerErrorConstructor(
    val className: String,
    val parameterTypes: List<String>,
    val marker: String,
)

private val SERVER_ERROR_CONSTRUCTORS = listOf(
    ServerErrorConstructor(
        className = "XErrors",
        parameterTypes = listOf(LIST_DESCRIPTOR, STRING_DESCRIPTOR),
        marker = "XErrors(errors=",
    ),
    ServerErrorConstructor(
        className = "HttpException",
        parameterTypes = listOf("I", INTEGER_DESCRIPTOR, STRING_DESCRIPTOR, STRING_DESCRIPTOR),
        marker = "HttpException(code=",
    ),
)

@Suppress("unused")
val newXServerLoggingPatch =
    bytecodePatch(
        name = "NewX: Server error logging",
        description = "Captures parsed NewX server errors in memory and exports them on demand.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXExtensionPatch)

        newXSettings {
            category(Categories.ADVANCED) {
                group(Groups.DEBUG_TOOLS) {
                    toggle(
                        id = "newx.advanced.debug_tools.server_logging",
                        strings = settingStrings("piko_newx_server_logging"),
                        order = 150,
                        defaultValue = true,
                    )
                    action(
                        id = "newx.advanced.debug_tools.save_server_logs",
                        strings = settingStrings("piko_newx_save_server_logs"),
                        order = 200,
                        handlerClassDescriptor = SERVER_ERROR_HANDLER_DESCRIPTOR,
                    )
                }
            }
        }

        execute {
            patchServerErrorConstructors()
            patchSubmitFailureHandler()
        }
    }

context(context: BytecodePatchContext)
private fun patchServerErrorConstructors() {
    SERVER_ERROR_CONSTRUCTORS.forEach { target ->
        val classDescriptor = "$ERROR_PACKAGE${target.className};"
        val errorClass = context.mutableClassDefBy(classDescriptor)
        if (errorClass.superclass != EXCEPTION_DESCRIPTOR) {
            throw PatchException(
                "NewX ${target.className} is not an Exception: ${errorClass.superclass}",
            )
        }

        val constructors = errorClass.methods.filter { method ->
            method.name == "<init>" &&
                method.parameterTypes.map { type -> type.toString() } == target.parameterTypes
        }
        if (constructors.size != 1) {
            throw PatchException(
                "Expected one NewX ${target.className} constructor, found " +
                    "${constructors.size}: ${constructors.joinToString()}",
            )
        }

        val toStringMethods = errorClass.methods.filter { method ->
            method.name == "toString" &&
                method.parameterTypes.isEmpty() &&
                method.returnType.toString() == STRING_DESCRIPTOR
        }
        if (toStringMethods.size != 1) {
            throw PatchException(
                "Expected one NewX ${target.className} toString method, found " +
                    "${toStringMethods.size}: ${toStringMethods.joinToString()}",
            )
        }
        val toStringImplementation = toStringMethods.single().implementation
            ?: throw PatchException("NewX ${target.className} toString has no implementation")
        val markerFound = toStringImplementation.instructions.any { instruction ->
            instruction.opcode == Opcode.CONST_STRING &&
                instruction.getReference<StringReference>()?.string == target.marker
        }
        if (!markerFound) {
            throw PatchException(
                "NewX ${target.className} toString marker was not found: ${target.marker}",
            )
        }

        val constructor = constructors.single()
        val implementation = constructor.implementation
            ?: throw PatchException("NewX ${target.className} constructor has no implementation")
        val returnIndices = implementation.instructions.mapIndexedNotNull { index, instruction ->
            index.takeIf { instruction.opcode == Opcode.RETURN_VOID }
        }
        if (returnIndices.size != 1) {
            throw PatchException(
                "Expected one return in NewX ${target.className} constructor, found " +
                    "${returnIndices.size}",
            )
        }

        constructor.addInstructions(
            returnIndices.single(),
            "invoke-static/range {p0 .. p0}, " +
                "$LOGGER_DESCRIPTOR->captureServerError($THROWABLE_DESCRIPTOR)V",
        )
    }
}

context(context: BytecodePatchContext)
private fun patchSubmitFailureHandler() {
    val matches = SubmitWorkHandlerFingerprint.scopedMatchAll()
    if (matches.size != 1) {
        throw PatchException(
            "Expected one NewX submit work handler, found ${matches.size}: " +
                matches.joinToString { "${it.originalClassDef.type}->${it.originalMethod}" },
        )
    }
    patchSubmitFailureMethod(matches.single().method)
}

private fun patchSubmitFailureMethod(method: MutableMethod) {
    val implementation = method.implementation
        ?: throw PatchException("NewX submit work handler has no implementation: $method")
    val instructions = implementation.instructions
    val throwableReads = instructions.mapIndexedNotNull { index, instruction ->
        val field = instruction.getReference<FieldReference>() ?: return@mapIndexedNotNull null
        if (instruction.opcode != Opcode.IGET_OBJECT ||
            field.definingClass.toString() != RESULT_FAILURE_DESCRIPTOR ||
            field.type.toString() != THROWABLE_DESCRIPTOR
        ) {
            return@mapIndexedNotNull null
        }
        val oneRegisterInstruction = instruction as? OneRegisterInstruction
            ?: throw PatchException("NewX submit failure throwable read has no destination: $method")
        RegisterLocation(index, oneRegisterInstruction.registerA)
    }
    if (throwableReads.size != 1) {
        throw PatchException(
            "Expected one NewX submit failure throwable read, found " +
                "${throwableReads.size}: ${throwableReads.joinToString()}",
        )
    }

    val failureEventIndices = findEventIndices(instructions, POST_FAILURE_FIELD_NAME)
    val throwableRead = throwableReads.single()
    val failureEventCandidates = failureEventIndices.filter { index ->
        index > throwableRead.index
    }
    if (failureEventCandidates.size != 1) {
        throw PatchException(
            "Expected one final NewX POST_FAILURE event after throwable read, found " +
                "${failureEventCandidates.size}: all=$failureEventIndices, " +
                "throwableRead=${throwableRead.index}",
        )
    }
    val successEventIndices = findEventIndices(instructions, POST_SUCCESS_FIELD_NAME)
    if (successEventIndices.size != 1) {
        throw PatchException(
            "Expected one NewX POST_SUCCESS event, found " +
                "${successEventIndices.size}: ${successEventIndices.joinToString()}",
        )
    }

    val failureEventIndex = failureEventCandidates.single()
    val successEventIndex = successEventIndices.single()
    if (failureEventIndex >= successEventIndex) {
        throw PatchException(
            "NewX submit event order is invalid: " +
                "POST_FAILURE@$failureEventIndex, POST_SUCCESS@$successEventIndex",
        )
    }

    val operationCandidates = instructions.mapIndexedNotNull { index, instruction ->
        findPostOperationRegister(instructions, index, instruction)
    }
    val failureOperationCandidates = operationCandidates.filter { candidate ->
        candidate.index > failureEventIndex
    }
    if (failureOperationCandidates.isEmpty()) {
        throw PatchException(
            "Expected a NewX failure operation register after POST_FAILURE: " +
                "all=$operationCandidates, POST_FAILURE@$failureEventIndex",
        )
    }
    val firstFailureOperationIndex = failureOperationCandidates.minOf { candidate ->
        candidate.index
    }
    val firstFailureOperations = failureOperationCandidates.filter { candidate ->
        candidate.index == firstFailureOperationIndex
    }
    if (firstFailureOperations.size != 1) {
        throw PatchException(
            "Expected one earliest NewX failure operation register, found " +
                "${firstFailureOperations.size}: all=$operationCandidates",
        )
    }
    if (firstFailureOperationIndex >= successEventIndex) {
        throw PatchException(
            "NewX failure operation is after POST_SUCCESS: " +
                "operation@$firstFailureOperationIndex, POST_SUCCESS@$successEventIndex",
        )
    }

    val operationRegister = firstFailureOperations.single().register
    if (throwableRead.register !in 0..15 || operationRegister !in 0..15) {
        throw PatchException(
            "NewX submit failure registers do not fit invoke: " +
                "throwable=v${throwableRead.register}, operation=v$operationRegister",
        )
    }
    if (throwableRead.register == operationRegister) {
        throw PatchException(
            "NewX submit failure throwable and operation share v${throwableRead.register}",
        )
    }

    method.addInstructions(
        throwableRead.index + 1,
        "invoke-static {v${throwableRead.register}, v$operationRegister}, " +
            "$LOGGER_DESCRIPTOR->captureSubmitFailure(${THROWABLE_DESCRIPTOR}Ljava/lang/Object;)V",
    )
}

private fun findEventIndices(
    instructions: List<Instruction>,
    eventName: String,
): List<Int> = instructions.mapIndexedNotNull { index, instruction ->
    val field = instruction.getReference<FieldReference>() ?: return@mapIndexedNotNull null
    index.takeIf { field.name == eventName }
}

private fun findPostOperationRegister(
    instructions: List<Instruction>,
    index: Int,
    instruction: Instruction,
): RegisterLocation? {
    if (instruction.opcode != Opcode.SGET_OBJECT) return null
    val field = instruction.getReference<FieldReference>() ?: return null
    if (field.name != POST_OPERATION_FIELD_NAME) return null

    val sget = instruction as? OneRegisterInstruction
        ?: throw PatchException("NewX submit operation read has no destination at $index")
    val comparison = instructions.getOrNull(index + 1)
    if (comparison?.opcode != Opcode.IF_NE) return null
    val ifInstruction = comparison as? TwoRegisterInstruction
        ?: throw PatchException("NewX submit operation comparison has no registers at $index")
    val operationRegister = when (sget.registerA) {
        ifInstruction.registerA -> ifInstruction.registerB
        ifInstruction.registerB -> ifInstruction.registerA
        else -> return null
    }
    return RegisterLocation(index, operationRegister)
}
