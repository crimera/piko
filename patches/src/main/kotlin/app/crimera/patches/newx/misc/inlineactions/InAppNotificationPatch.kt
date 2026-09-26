package app.crimera.patches.newx.misc.inlineactions

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.bytecode.Block
import app.crimera.bytecode.Target
import app.crimera.bytecode.fieldReference
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.utils.requireExactlyOne
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val BRIDGE_DESCRIPTOR =
    "Lapp/morphe/extension/newx/misc/NewXInAppNotification;"
private const val BRIDGE_FACADE_FIELD =
    "$BRIDGE_DESCRIPTOR->facade:Ljava/lang/Object;"
private const val API_SCOPE = "Lcom/x/inappnotification/api/"
private const val IMPL_SCOPE = "Lcom/x/inappnotification/impl/"
private const val MODEL_SCOPE = "Lcom/x/models/"
private const val COROUTINE_SCOPE = "Lkotlinx/coroutines/"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val INT_DESCRIPTOR = "I"
private const val VOID_DESCRIPTOR = "V"
private const val SEND_HELPER = "send"
private const val LOCAL_REGISTER_COUNT = 12
private const val DEFAULT_ARGUMENT_MASK = 0x3fe
private const val UNAVAILABLE_LABEL = "piko_newx_in_app_notification_unavailable"

private data class FacadeCandidate(
    val classDef: ClassDef,
    val constructors: List<Method>,
    val senders: List<Method>,
) {
    override fun toString(): String = classDef.type
}

private data class ResolvedNotificationRuntime(
    val facadeDescriptor: String,
    val facadeConstructor: MethodReference,
    val sender: MethodReference,
    val apiDescriptor: String,
    val apiConstructor: MethodReference,
    val literalDescriptor: String,
    val literalConstructor: MethodReference,
)

/**
 * Captures the native facade and fills the Object/String-only extension bridge with the
 * release-specific notification model calls.  The extension deliberately keeps the Toast
 * fallback, so a notification facade that is not ready at runtime cannot lose a status message.
 */
internal val newXInAppNotificationPatch =
    bytecodePatch(default = false) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXExtensionPatch)

        execute {
            val runtime = resolveNotificationRuntime()
            patchFacadeConstructor(runtime)
            patchBridge(runtime)
        }
    }

context(context: BytecodePatchContext)
private fun resolveNotificationRuntime(): ResolvedNotificationRuntime {
    val classDefs = buildList<ClassDef> {
        context.classDefForEach { add(it) }
    }

    val apiClass = resolveApiClass(classDefs)
    val apiConstructor = resolveApiConstructor(apiClass)
    val parameters = apiConstructor.parameterTypes.map(CharSequence::toString)
    val baseDescriptor = parameters[0]
    val expectedLiteralDescriptor = parameters[4]
    if (!baseDescriptor.startsWith(MODEL_SCOPE) || !expectedLiteralDescriptor.startsWith(MODEL_SCOPE)) {
        throw PatchException(
            "NewX in-app notification constructor does not use model parameters: $apiConstructor",
        )
    }

    val literalClass = resolveLiteralClass(classDefs, baseDescriptor, expectedLiteralDescriptor)
    val literalConstructor = requireExactlyOne(
        "NewX in-app notification Literal(String) constructor in ${literalClass.type}",
        literalClass.methods.filter { method ->
            method.name == "<init>" &&
                method.returnType == VOID_DESCRIPTOR &&
                method.parameterTypes.map(CharSequence::toString) == listOf(STRING_DESCRIPTOR)
        },
    )

    val facade = resolveFacade(classDefs, apiClass.type)
    val facadeConstructor = requireExactlyOne(
        "NewX in-app notification facade constructor in ${facade.classDef.type}",
        facade.constructors,
    )
    val sender = requireExactlyOne(
        "NewX in-app notification generic sender in ${facade.classDef.type}",
        facade.senders,
    )

    return ResolvedNotificationRuntime(
        facadeDescriptor = facade.classDef.type,
        facadeConstructor = facadeConstructor,
        sender = sender,
        apiDescriptor = apiClass.type,
        apiConstructor = apiConstructor,
        literalDescriptor = literalClass.type,
        literalConstructor = literalConstructor,
    )
}

private fun resolveApiClass(classDefs: List<ClassDef>): ClassDef {
    val candidates = classDefs.filter { classDef ->
        classDef.type.startsWith(API_SCOPE) &&
            classDef.methods.any { method ->
                method.hasStringAnchors(
                    "InAppNotification(message=",
                    ", messageBoldRanges=",
                    ", priority=",
                )
            }
    }
    val apiClass = requireExactlyOne("NewX in-app notification API model class", candidates)
    requireExactlyOne(
        "NewX in-app notification API model toString method in ${apiClass.type}",
        apiClass.methods.filter { method ->
            method.hasStringAnchors(
                "InAppNotification(message=",
                ", messageBoldRanges=",
                ", priority=",
            )
        },
    )
    return apiClass
}

private fun resolveApiConstructor(apiClass: ClassDef): Method {
    val constructors = apiClass.methods.filter { method ->
        val parameters = method.parameterTypes.map(CharSequence::toString)
        method.name == "<init>" &&
            method.returnType == VOID_DESCRIPTOR &&
            parameters.size == 10 &&
            parameters[0].startsWith(MODEL_SCOPE) &&
            parameters[1].startsWith(API_SCOPE) &&
            parameters[2].startsWith(API_SCOPE) &&
            parameters[3] == LIST_DESCRIPTOR &&
            parameters[4].startsWith(MODEL_SCOPE) &&
            parameters[5].startsWith(API_SCOPE) &&
            parameters[6].startsWith(API_SCOPE) &&
            parameters[7].startsWith(API_SCOPE) &&
            parameters[8] == STRING_DESCRIPTOR &&
            parameters[9] == INT_DESCRIPTOR
    }
    return requireExactlyOne(
        "NewX in-app notification API 10-argument constructor in ${apiClass.type}",
        constructors,
    )
}

private fun resolveLiteralClass(
    classDefs: List<ClassDef>,
    baseDescriptor: String,
    expectedDescriptor: String,
): ClassDef {
    val candidates = classDefs.filter { classDef ->
        classDef.type.startsWith(MODEL_SCOPE) &&
            classDef.superclass?.toString() == baseDescriptor &&
            classDef.methods.any { method ->
                method.hasStringAnchors("Literal(text=", ")")
            }
    }
    val literalClass = requireExactlyOne("NewX Literal model class", candidates)
    if (literalClass.type != expectedDescriptor) {
        throw PatchException(
            "NewX API model Literal parameter changed from semantic match " +
                "${literalClass.type} to $expectedDescriptor",
        )
    }
    requireExactlyOne(
        "NewX Literal toString method in ${literalClass.type}",
        literalClass.methods.filter { method ->
            method.hasStringAnchors("Literal(text=", ")")
        },
    )
    return literalClass
}

private fun resolveFacade(
    classDefs: List<ClassDef>,
    apiDescriptor: String,
): FacadeCandidate {
    val candidates = classDefs.mapNotNull { classDef ->
        if (!classDef.type.startsWith(IMPL_SCOPE)) return@mapNotNull null

        val constructors = classDef.methods.filter { method ->
            val parameters = method.parameterTypes.map(CharSequence::toString)
            method.name == "<init>" &&
                method.returnType == VOID_DESCRIPTOR &&
                parameters.size == 2 &&
                parameters[0].startsWith(API_SCOPE) &&
                parameters[1].startsWith(COROUTINE_SCOPE) &&
                classDef.fields.count { field ->
                    !AccessFlags.STATIC.isSet(field.accessFlags) && field.type == parameters[0]
                } == 1 &&
                classDef.fields.count { field ->
                    !AccessFlags.STATIC.isSet(field.accessFlags) && field.type == parameters[1]
                } == 1
        }
        val senders = classDef.methods.filter { method ->
            val parameters = method.parameterTypes.map(CharSequence::toString)
            AccessFlags.PUBLIC.isSet(method.accessFlags) &&
                AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.returnType == VOID_DESCRIPTOR &&
                parameters == listOf(classDef.type, apiDescriptor)
        }
        if (constructors.isEmpty() || senders.isEmpty()) return@mapNotNull null
        FacadeCandidate(classDef, constructors, senders)
    }

    return requireExactlyOne("NewX in-app notification facade", candidates)
}

context(context: BytecodePatchContext)
private fun patchFacadeConstructor(runtime: ResolvedNotificationRuntime) {
    val facadeClass = context.mutableClassDefBy(runtime.facadeDescriptor)
    val constructor = requireExactlyOne(
        "NewX in-app notification mutable facade constructor",
        facadeClass.methods.filter { method -> method.matches(runtime.facadeConstructor) },
    )
    val exitIndex = requireExactlyOne(
        "NewX in-app notification facade constructor return",
        constructor.instructions.mapIndexedNotNull { index, instruction ->
            index.takeIf { instruction.opcode == Opcode.RETURN_VOID }
        },
    )
    // `p0` is the receiver; `insertHook` emits `invoke-static/range` itself when the
    // receiver does not fit the four-bit operand of `invoke-static`.
    constructor.insertHook(
        index = exitIndex,
        // The old insertion left every label on the constructor's return, so a branch that reaches
        // the exit directly keeps bypassing the capture, exactly as before.
        relocateBranchTargets = false,
    ) {
        invokeStatic(
            methodReference("$BRIDGE_DESCRIPTOR->capture($OBJECT_DESCRIPTOR)V"),
            constructor.p0Register,
        )
    }
}

context(context: BytecodePatchContext)
private fun patchBridge(runtime: ResolvedNotificationRuntime) {
    val bridgeClass = context.mutableClassDefBy(BRIDGE_DESCRIPTOR)
    val placeholder = requireExactlyOne(
        "NewX in-app notification bridge method",
        bridgeClass.methods.filter { method ->
            method.name == SEND_HELPER &&
                method.parameterTypes.map(CharSequence::toString) == listOf(STRING_DESCRIPTOR) &&
                method.returnType == "Z"
        },
    )
    if (!AccessFlags.STATIC.isSet(placeholder.accessFlags)) {
        throw PatchException("NewX in-app notification bridge is unexpectedly non-static: $placeholder")
    }

    val implementation = placeholder.implementation
        ?: throw PatchException("NewX in-app notification bridge has no implementation: $placeholder")
    val requiredRegisterCount = placeholder.numberOfParameterRegisters + LOCAL_REGISTER_COUNT
    val helper =
        if (implementation.registerCount >= requiredRegisterCount) {
            placeholder
        } else {
            placeholder.cloneMutable(
                additionalRegisters = requiredRegisterCount - implementation.registerCount,
            ).also { expanded ->
                bridgeClass.methods.remove(placeholder)
                bridgeClass.methods.add(expanded)
            }
        }

    helper.insertHook(0, relocateBranchTargets = false) {
        notificationInstructions(runtime, helper.p0Register)
    }
}

/**
 * Emits the bridge body into the twelve locals the frame growth above reserved: the facade captured
 * by [patchFacadeConstructor], one API model around the `p0` message, and the facade's generic
 * sender. Both paths return a boolean, so the stub's own trailing return is never reached.
 *
 * The receiver and the literal constructor argument are the only non-zero operands; the remaining
 * model and collection parameters stay null and the priority uses the default argument mask.
 */
private fun Block.notificationInstructions(
    runtime: ResolvedNotificationRuntime,
    messageRegister: Int,
) {
    sget(0, fieldReference(BRIDGE_FACADE_FIELD))
    ifEqz(0, Target.Local(UNAVAILABLE_LABEL))
    checkCast(0, runtime.facadeDescriptor)
    newInstance(1, runtime.apiDescriptor)
    newInstance(2, runtime.literalDescriptor)
    invokeDirect(runtime.literalConstructor, 2, messageRegister)
    (3..10).forEach { register -> constInt(register, 0) }
    constInt(11, DEFAULT_ARGUMENT_MASK)
    // Eleven operand words: the receiver v1 plus the ten constructor parameters v2..v11, which the
    // typed invoke lowers to the `invoke-direct/range {v1 .. v11}` this block has always emitted.
    invokeDirect(runtime.apiConstructor, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
    invokeStatic(runtime.sender, 0, 1)
    constInt(0, 1)
    returnValue(0)

    label(UNAVAILABLE_LABEL)
    constInt(0, 0)
    returnValue(0)
}

private fun Method.hasStringAnchors(vararg anchors: String): Boolean {
    if (name != "toString" || returnType != STRING_DESCRIPTOR || parameterTypes.isNotEmpty()) return false
    val instructions = implementation?.instructions ?: return false
    return anchors.all { anchor ->
        instructions.any { instruction ->
            instruction.getReference<StringReference>()?.string == anchor
        }
    }
}

private fun Method.matches(reference: MethodReference): Boolean =
    definingClass.toString() == reference.definingClass.toString() &&
        name == reference.name &&
        returnType.toString() == reference.returnType.toString() &&
        parameterTypes.map(CharSequence::toString) == reference.parameterTypes.map(CharSequence::toString)
