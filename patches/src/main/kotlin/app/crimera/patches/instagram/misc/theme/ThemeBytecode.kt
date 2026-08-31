/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.crimera.patches.instagram.misc.extension.hooks.instagramInitHook
import app.crimera.patches.instagram.misc.settings.IgFragmentActivityOnCreate
import app.crimera.patches.shared.parameterRegisterStart
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val INSTAGRAM_APP_SHELL_SUFFIX = "/InstagramAppShell;"
internal const val IG_FRAGMENT_ACTIVITY_DESCRIPTOR =
    "Lcom/instagram/base/activity/IgFragmentActivity;"
internal const val BUNDLE_DESCRIPTOR = "Landroid/os/Bundle;"
internal const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
internal const val STRING_DESCRIPTOR = "Ljava/lang/String;"
internal const val LIST_DESCRIPTOR = "Ljava/util/List;"
internal const val COLLECTION_DESCRIPTOR = "Ljava/util/Collection;"
internal const val THEME_OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
internal const val RADIO_GROUP_LISTENER_DESCRIPTOR =
    "Landroid/widget/RadioGroup\$OnCheckedChangeListener;"
internal data class InvokeCall(
    val index: Int,
    val reference: MethodReference,
    val registers: List<Int>,
)

internal fun legacyNativeThemeListenerInvocation(
    listenerRegister: Int,
    packedIdsRegister: Int,
): String =
    "invoke-static {v$listenerRegister, v$packedIdsRegister}, " +
        "Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->" +
        "wrapLegacyNativeThemeListener(" +
        "Landroid/widget/RadioGroup\$OnCheckedChangeListener;I)" +
        "Landroid/widget/RadioGroup\$OnCheckedChangeListener;"

internal fun systemUiModeCacheResolutionInvocation(
    resultRegister: Int,
): String =
    "invoke-static {v$resultRegister}, " +
        "Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->" +
        "resolveSystemUiModeCache(I)I"

context(patchContext: BytecodePatchContext)
internal fun installThemeLifecycleHooks() {
    instagramInitHook.fingerprint.method.apply {
        if (
            !definingClass.endsWith(INSTAGRAM_APP_SHELL_SUFFIX) ||
            name != "onCreate" ||
            parameterTypes.isNotEmpty() ||
            returnType != "V"
        ) {
            throw PatchException(
                "Unexpected InstagramAppShell lifecycle method: " +
                    "$definingClass->$name(${parameterTypes.joinToString()})$returnType",
            )
        }

        val firstInvokeSuperIndex = indexOfFirstInstruction(Opcode.INVOKE_SUPER)
        if (firstInvokeSuperIndex < 0) {
            throw PatchException("InstagramAppShell.onCreate has no invoke-super instruction")
        }
        val contextRegister =
            getInstruction(firstInvokeSuperIndex).registersUsed.firstOrNull()
                ?: throw PatchException(
                    "InstagramAppShell.onCreate invoke-super has no context register",
                )

        addInstruction(
            firstInvokeSuperIndex + 1,
            """
            invoke-static {v$contextRegister}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->initialize(Landroid/content/Context;)V
            """.trimIndent(),
        )
    }

    IgFragmentActivityOnCreate.method.apply {
        if (
            definingClass != IG_FRAGMENT_ACTIVITY_DESCRIPTOR ||
            name != "onCreate" ||
            parameterTypes.size != 1 ||
            parameterTypes[0].toString() != BUNDLE_DESCRIPTOR ||
            returnType != "V"
        ) {
            throw PatchException(
                "Unexpected IgFragmentActivity lifecycle method: " +
                    "$definingClass->$name(${parameterTypes.joinToString()})$returnType",
            )
        }

        addInstruction(
            0,
            """
            invoke-static {p0}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->applyToActivity(Landroid/app/Activity;)V
            """.trimIndent(),
        )
    }
}

context(patchContext: BytecodePatchContext)
internal fun installSystemDefaultUiModeHook() {
    val method =
        CurrentSystemUiModeFingerprint
            .matchAll(1..1)
            .single()
            .method
    if (
        !AccessFlags.STATIC.isSet(method.accessFlags) ||
        method.parameterTypes.isNotEmpty() ||
        method.returnType != "I"
    ) {
        throw PatchException(
            "Unexpected current system ui mode method: " +
                "${method.definingClass}->${method.name}" +
                "(${method.parameterTypes.joinToString()})${method.returnType}",
        )
    }

    val returnIndexes =
        method.instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode == Opcode.RETURN) index else null
        }
    if (returnIndexes.size != 1) {
        throw PatchException(
            "Expected one current system ui mode return, found ${returnIndexes.size}",
        )
    }

    val returnIndex = returnIndexes.single()
    val resultRegister =
        (method.getInstruction(returnIndex) as? OneRegisterInstruction)
            ?.registerA
            ?: throw PatchException(
                "Current system ui mode return does not use one register",
            )
    if (resultRegister !in 0..0xf) {
        throw PatchException(
            "Current system ui mode result register is not 4-bit: v$resultRegister",
        )
    }

    method.addInstructions(
        returnIndex,
        """
        ${systemUiModeCacheResolutionInvocation(resultRegister)}
        move-result v$resultRegister
        """.trimIndent(),
    )
}

context(patchContext: BytecodePatchContext)
internal fun installNativeThemeModeSync() {
    installComposeNativeThemeModeSync()
    installLegacyNativeThemeModeSync()
}

internal fun MutableMethod.invokeCalls(): List<InvokeCall> =
    instructions.mapIndexedNotNull { index, instruction ->
        if (
            instruction.opcode != Opcode.INVOKE_STATIC &&
            instruction.opcode != Opcode.INVOKE_STATIC_RANGE
        ) {
            return@mapIndexedNotNull null
        }
        val reference =
            (instruction as? ReferenceInstruction)?.reference as? MethodReference
                ?: return@mapIndexedNotNull null

        InvokeCall(
            index = index,
            reference = reference,
            registers = instruction.registersUsed,
        )
    }

internal fun String.isObjectDescriptor(): Boolean =
    length >= 3 && startsWith("L") && endsWith(";")

internal data class LegacyBinding(
    val method: MutableMethod,
    val radioConstructor: InvokeCall,
    val bindIndex: Int,
    val collectionRegister: Int,
)

internal fun findLegacyBinding(method: MutableMethod): LegacyBinding? {
    val instructions = method.instructions
    val radioConstructors =
        instructions.mapIndexedNotNull { index, instruction ->
            if (
                instruction.opcode != Opcode.INVOKE_DIRECT &&
                instruction.opcode != Opcode.INVOKE_DIRECT_RANGE
            ) {
                return@mapIndexedNotNull null
            }
            val reference =
                (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@mapIndexedNotNull null
            if (
                reference.name != "<init>" ||
                reference.returnType != "V" ||
                reference.parameterTypes.map(CharSequence::toString) !=
                listOf(
                    RADIO_GROUP_LISTENER_DESCRIPTOR,
                    STRING_DESCRIPTOR,
                    LIST_DESCRIPTOR,
                )
            ) {
                return@mapIndexedNotNull null
            }

            InvokeCall(
                index = index,
                reference = reference,
                registers = instruction.registersUsed,
            )
        }
    val bindingCandidates =
        radioConstructors.mapNotNull { constructor ->
            if (constructor.registers.size != 4 || constructor.index + 2 > instructions.lastIndex) {
                return@mapNotNull null
            }

            val rowRegister = constructor.registers[0]
            val addInstruction = instructions[constructor.index + 1]
            val addReference =
                (addInstruction as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@mapNotNull null
            val addRegisters = addInstruction.registersUsed
            if (
                addInstruction.opcode != Opcode.INVOKE_VIRTUAL &&
                addInstruction.opcode != Opcode.INVOKE_INTERFACE
            ) {
                return@mapNotNull null
            }
            if (
                addReference.name != "add" ||
                addReference.returnType != "Z" ||
                addReference.parameterTypes.map(CharSequence::toString) !=
                listOf(THEME_OBJECT_DESCRIPTOR) ||
                addRegisters.size != 2 ||
                addRegisters[1] != rowRegister
            ) {
                return@mapNotNull null
            }

            val bindIndex = constructor.index + 2
            val bindInstruction = instructions[bindIndex]
            val bindReference =
                (bindInstruction as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@mapNotNull null
            val bindRegisters = bindInstruction.registersUsed
            if (
                bindInstruction.opcode != Opcode.INVOKE_VIRTUAL &&
                bindInstruction.opcode != Opcode.INVOKE_INTERFACE
            ) {
                return@mapNotNull null
            }
            if (
                bindReference.returnType != "V" ||
                bindReference.parameterTypes.map(CharSequence::toString) !=
                listOf(COLLECTION_DESCRIPTOR) ||
                bindReference.definingClass == method.definingClass ||
                bindRegisters.size != 2 ||
                bindRegisters[0] != parameterRegisterStart(method) ||
                bindRegisters[1] != addRegisters[0]
            ) {
                return@mapNotNull null
            }

            LegacyBinding(
                method = method,
                radioConstructor = constructor,
                bindIndex = bindIndex,
                collectionRegister = addRegisters[0],
            )
        }
    if (bindingCandidates.size > 1) {
        throw PatchException(
            "Expected at most one RadioGroup append followed by adapter binding in " +
                "${method.definingClass}->${method.name}, found ${bindingCandidates.size}",
        )
    }

    return bindingCandidates.singleOrNull()
}
