/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.blur

import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.settings.SettingReadRegisterConstraint
import app.crimera.patches.settings.ToggleSettingDefinition
import app.crimera.patches.settings.injectRead
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.common.requireExactlyOne
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val HAZE_SCOPE = "Ldev/chrisbanes/haze/"
private const val BOOLEAN_DESCRIPTOR = "Z"
private const val BOOLEAN_VALUE_OF_DESCRIPTOR = "Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;"
private const val COLLECTION_DESCRIPTOR = "Ljava/util/Collection;"

/**
 * 12.30 moved blur configuration off the Haze node property into the effect-scope recorder: the
 * scope lambda is invoked on a class that appends `(overrideKey, value)` entries to a list instead
 * of writing a node field. The boolean override is the only `(Z)V` method in the Haze scope that
 * boxes its parameter for an entry and appends the entry to the scope list, so this is the path
 * every `blurEnabled = ...` write takes.
 */
private fun Method.isHazeBlurEnabledRecorder(): Boolean {
    if (AccessFlags.STATIC.isSet(accessFlags) || !AccessFlags.PUBLIC.isSet(accessFlags)) return false
    if (returnType != "V" || parameterTypes.map(CharSequence::toString) != listOf(BOOLEAN_DESCRIPTOR)) {
        return false
    }

    val instructions = implementation?.instructions?.toList() ?: return false
    val inputRegister = p0Register + 1
    val boxCalls =
        instructions.filter { instruction ->
            instruction.opcode == Opcode.INVOKE_STATIC &&
                instruction.getReference<MethodReference>()?.toString() == BOOLEAN_VALUE_OF_DESCRIPTOR
        }
    if (boxCalls.size != 1) return false
    if ((boxCalls.single() as? Instruction35c)?.registerC != inputRegister) return false

    return instructions.any { instruction ->
        instruction.opcode == Opcode.INVOKE_INTERFACE &&
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.definingClass == COLLECTION_DESCRIPTOR && reference.name == "add"
            } == true
    }
}

context(context: BytecodePatchContext)
private fun resolveHazeBlurEnabledRecorder(): MutableMethod {
    val recorderClasses = mutableListOf<String>()
    context.classDefForEach { classDef ->
        if (!classDef.type.startsWith(HAZE_SCOPE)) return@classDefForEach
        if (classDef.methods.any { method -> method.implementation != null && method.isHazeBlurEnabledRecorder() }) {
            recorderClasses += classDef.type
        }
    }
    val recorderClass =
        requireExactlyOne("NewX Haze blur override recorder class", recorderClasses) { it }
    val recorderMethods =
        context.mutableClassDefBy(recorderClass).methods.filter { method ->
            method.isHazeBlurEnabledRecorder()
        }
    return requireExactlyOne("NewX Haze blur override recorder setter", recorderMethods) {
        it.toString()
    }
}

private fun patchHazeBlurSetter(
    method: MutableMethod,
    disableBlur: ToggleSettingDefinition,
) {
    val receiverRegister = method.p0Register
    val inputRegister = receiverRegister + 1
    if (inputRegister > 255) {
        throw PatchException(
            "NewX Haze blur setter input register exceeds bytecode limits: v$inputRegister",
        )
    }

    val read =
        disableBlur.injectRead(
            method = method,
            index = 0,
            excludedRegisters = listOf(receiverRegister, inputRegister),
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    if (read.register == receiverRegister || read.register == inputRegister) {
        throw PatchException(
            "NewX Haze blur setting register aliases a setter parameter: v${read.register}",
        )
    }

    // The old external label was anchored on the setter's original first instruction, which is
    // exactly the instruction the injected read left behind its own block, so
    // `Target.Original` addresses it. The plain insertion kept incoming labels on that
    // instruction, so the branch policy stays `false`.
    method.insertHook(
        index = read.nextIndex,
        relocateBranchTargets = false,
    ) {
        ifEqz(read.register, Target.Original)
        constInt(inputRegister, 0)
    }
}

@Suppress("unused")
val newXDisableBlurPatch =
    bytecodePatch(
        name = "NewX: Disable blur effects",
        description =
            "Disables Haze blur in NewX Compose UI while preserving configured fallback tint and scrim effects.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)

        val disableBlur =
            newXToggle(
                id = "newx.appearance.disable_blur",
                category = Categories.APPEARANCE,
                strings = settingStrings("piko_newx_disable_blur"),
                order = 100,
                defaultValue = false,
                rebootApp = true,
            )

        execute {
            val setter = resolveHazeBlurEnabledRecorder()
            patchHazeBlurSetter(setter, disableBlur)
        }
    }
