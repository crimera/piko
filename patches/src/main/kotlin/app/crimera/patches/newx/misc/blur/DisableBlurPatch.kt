/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.blur

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.ToggleSettingDefinition
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val HAZE_SCOPE = "Ldev/chrisbanes/haze/"
private const val HAZE_UPDATE_EFFECT_MARKER = "HazeEffectNode-updateEffect"
private const val BOOLEAN_DESCRIPTOR = "Z"

/**
 * Haze keeps its node owner and setter name obfuscated, but the updateEffect diagnostic marker is
 * emitted by the node class. Resolve that class first, then inspect its methods for the setter's
 * state-read/conditional-write/invalidation shape.
 */
private object NewXHazeNodeFingerprint : Fingerprint(
    definingClass = HAZE_SCOPE,
    filters = listOf(string(HAZE_UPDATE_EFFECT_MARKER)),
)

private fun Instruction.booleanFieldAccess(
    opcode: Opcode,
    owner: String,
    receiverRegister: Int,
    valueRegister: Int? = null,
): FieldReference? {
    if (this.opcode != opcode) return null
    val registers = this as? TwoRegisterInstruction ?: return null
    val field = getReference<FieldReference>() ?: return null
    if (
        field.definingClass != owner ||
        field.type != BOOLEAN_DESCRIPTOR ||
        registers.registerB != receiverRegister ||
        (valueRegister != null && registers.registerA != valueRegister)
    ) {
        return null
    }
    return field
}

private fun Method.isHazeBlurEnabledSetter(owner: String): Boolean {
    if (
        AccessFlags.STATIC.isSet(accessFlags) ||
        !AccessFlags.PUBLIC.isSet(accessFlags) ||
        !AccessFlags.FINAL.isSet(accessFlags) ||
        returnType != "V" ||
        parameterTypes != listOf(BOOLEAN_DESCRIPTOR)
    ) {
        return false
    }

    val instructions = implementation?.instructions?.toList() ?: return false
    val receiverRegister = p0Register
    val inputRegister = receiverRegister + 1
    val stateReads = instructions.mapNotNull { instruction ->
        instruction
            .booleanFieldAccess(
                opcode = Opcode.IGET_BOOLEAN,
                owner = owner,
                receiverRegister = receiverRegister,
            )
            ?.let { field -> field to (instruction as TwoRegisterInstruction).registerA }
    }
    if (stateReads.size != 1) return false

    val (stateField, stateRegister) = stateReads.single()
    val stateFieldDescriptor = stateField.toString()
    val inputWriteCount = instructions.count { instruction ->
        instruction
            .booleanFieldAccess(
                opcode = Opcode.IPUT_BOOLEAN,
                owner = owner,
                receiverRegister = receiverRegister,
                valueRegister = inputRegister,
            )
            ?.toString() == stateFieldDescriptor
    }
    if (inputWriteCount != 1) return false

    val comparesStateWithInput = instructions.any { instruction ->
        if (instruction.opcode != Opcode.IF_EQ && instruction.opcode != Opcode.IF_NE) {
            return@any false
        }
        val registers = instruction as? TwoRegisterInstruction ?: return@any false
        (registers.registerA == inputRegister && registers.registerB == stateRegister) ||
            (registers.registerA == stateRegister && registers.registerB == inputRegister)
    }
    if (!comparesStateWithInput) return false

    // Haze marks the node dirty after changing blurEnabled. This distinguishes the setter from
    // unrelated boolean mutators in the same node class without naming its fields.
    val invalidationWriteCount = instructions.count { instruction ->
        instruction
            .booleanFieldAccess(
                opcode = Opcode.IPUT_BOOLEAN,
                owner = owner,
                receiverRegister = receiverRegister,
            )
            ?.toString()
            ?.let { field -> field != stateFieldDescriptor } == true
    }
    return invalidationWriteCount == 1 && instructions.count { it.opcode == Opcode.RETURN_VOID } == 1
}

private fun patchHazeBlurSetter(
    method: MutableMethod,
    disableBlur: ToggleSettingDefinition,
) {
    val originalFirstInstruction =
        method.instructions.firstOrNull()
            ?: throw PatchException("NewX Haze blur setter has no instructions: $method")
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

    method.addInstructionsWithLabels(
        read.nextIndex,
        """
            if-eqz v${read.register}, :piko_newx_disable_blur_continue
            const/4 v$inputRegister, 0x0
        """.trimIndent(),
        ExternalLabel(
            "piko_newx_disable_blur_continue",
            originalFirstInstruction,
        ),
    )
}

@Suppress("unused")
val newXDisableBlurPatch =
    bytecodePatch(
        name = "NewX: Disable blur effects",
        description =
            "Disables Haze blur in NewX Compose UI while preserving configured fallback tint and scrim effects.",
    ) {
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
            val nodeMatches = NewXHazeNodeFingerprint.scopedMatchAll()
            if (nodeMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX Haze node update marker, found ${nodeMatches.size}: " +
                        nodeMatches.joinToString { it.originalMethod.toString() },
                )
            }

            val ownerDescriptor = nodeMatches.single().originalClassDef.type
            val owner = mutableClassDefBy(ownerDescriptor)
            val setters = owner.methods.filter { method ->
                method.isHazeBlurEnabledSetter(ownerDescriptor)
            }
            if (setters.size != 1) {
                throw PatchException(
                    "Expected one NewX Haze blur-enabled setter in $ownerDescriptor, found " +
                        "${setters.size}: ${setters.joinToString()}",
                )
            }
            patchHazeBlurSetter(setters.single(), disableBlur)
        }
    }
