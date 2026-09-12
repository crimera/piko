package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.newXSingleChoice
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.TIMELINE_TAB_FILTER_DESCRIPTOR
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.VariableRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val HOME_TABBED_SCOPE = "Lcom/x/home/tabbed/"
private const val OBJECT_ARRAY_DESCRIPTOR = "[Ljava/lang/Object;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val TOPIC_FILTER_FLAG = "co_timeline_topic_filter_enabled"

private object HomeTabbedComponentFingerprint : Fingerprint(
    definingClass = HOME_TABBED_SCOPE,
    returnType = "V",
    filters = listOf(string(TOPIC_FILTER_FLAG)),
    custom = { method, _ ->
        method.name == "<init>" && method.hasHomeTabRouteCreation()
    },
)

private data class HomeTabRouteCreation(
    val arrayIndex: Int,
    val arrayResultRegister: Int,
)

private data class HomeTabListFactory(
    val returnType: String,
    val arrayRegister: Int,
    val resultRegister: Int,
)

@Suppress("unused")
val customizeNewXTimelineTabsPatch =
    bytecodePatch(
        name = "NewX: Customize timeline tabs",
        description = "Lets you hide the For You or Following tab from the NewX home timeline.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXExtensionPatch)

        newXSingleChoice(
            id = "newx.timeline.tab_visibility",
            category = Categories.TIMELINE,
            strings = settingStrings("piko_newx_timeline_tabs"),
            order = 150,
            defaultValue = "show_both",
            rebootApp = true,
            options =
                listOf(
                    choice("show_both", "piko_newx_timeline_tabs_show_both"),
                    choice("hide_for_you", "piko_newx_timeline_tabs_hide_for_you"),
                    choice("hide_following", "piko_newx_timeline_tabs_hide_following"),
                ),
        )

        execute {
            val matches = HomeTabbedComponentFingerprint.scopedMatchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX HomeTabbedComponent constructor, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            val match = matches.single()
            val creation = match.method.resolveHomeTabRouteCreation()
            val register = creation.arrayResultRegister
            val invoke =
                if (register in 0..15) {
                    "invoke-static {v$register}, "
                } else {
                    "invoke-static/range {v$register .. v$register}, "
                }

            match.method.addInstructions(
                creation.arrayIndex + 2,
                """
                    ${invoke}$TIMELINE_TAB_FILTER_DESCRIPTOR->filter(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v$register
                    check-cast v$register, $OBJECT_ARRAY_DESCRIPTOR
                """.trimIndent(),
            )
        }
    }

private fun Method.hasHomeTabRouteCreation(): Boolean {
    val methodInstructions = implementation?.instructions?.toList() ?: return false
    return methodInstructions.indices.any { index ->
        methodInstructions.isHomeTabRouteArray(index) &&
            methodInstructions.hasHomeTabListFactory(index, p0Register)
    }
}
private fun MutableMethod.resolveHomeTabRouteCreation(): HomeTabRouteCreation {
    val candidates =
        instructions.indices.filter { index ->
            instructions.isHomeTabRouteArray(index) &&
                instructions.hasHomeTabListFactory(index, p0Register)
        }

    if (candidates.size != 1) {
        throw PatchException(
            "Expected one NewX home tab route array in $this, found ${candidates.size}: " +
                candidates.joinToString(),
        )
    }

    val arrayIndex = candidates.single()
    val resultInstruction = instructions.getOrNull(arrayIndex + 1)
    if (resultInstruction?.opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException("NewX home tab route array has no move-result-object in $this")
    }
    val result = resultInstruction as? OneRegisterInstruction
        ?: throw PatchException("NewX home tab route result has no destination register in $this")

    return HomeTabRouteCreation(arrayIndex, result.registerA)
}

private fun List<Instruction>.isHomeTabRouteArray(index: Int): Boolean {
    val instruction = getOrNull(index) ?: return false
    if (instruction.opcode != Opcode.FILLED_NEW_ARRAY &&
        instruction.opcode != Opcode.FILLED_NEW_ARRAY_RANGE
    ) {
        return false
    }
    if ((instruction as? VariableRegisterInstruction)?.registerCount != 2) return false
    val arrayType = instruction.getReference<TypeReference>()?.type ?: return false
    // The route element descriptor changes between declared releases. The
    // following immutable-list factory is the semantic discriminator, so do
    // not make that obfuscated descriptor part of the fingerprint.
    return arrayType.startsWith("[L") && arrayType.endsWith(';')
}

private fun List<Instruction>.hasHomeTabListFactory(
    arrayIndex: Int,
    receiverRegister: Int,
): Boolean {
    return resolveHomeTabListFactory(arrayIndex, receiverRegister) != null
}

private fun List<Instruction>.resolveHomeTabListFactory(
    arrayIndex: Int,
    receiverRegister: Int,
): HomeTabListFactory? {
    val arrayResult = getOrNull(arrayIndex + 1) ?: return null
    if (arrayResult.opcode != Opcode.MOVE_RESULT_OBJECT) return null
    val arrayRegister = (arrayResult as? OneRegisterInstruction)?.registerA ?: return null

    val factory = getOrNull(arrayIndex + 2) ?: return null
    if (factory.opcode != Opcode.INVOKE_STATIC &&
        factory.opcode != Opcode.INVOKE_STATIC_RANGE
    ) {
        return null
    }
    val reference = factory.getReference<MethodReference>() ?: return null
    if (reference.parameterTypes.map(CharSequence::toString) != listOf(OBJECT_ARRAY_DESCRIPTOR)) {
        return null
    }
    if (factory.registersUsed != listOf(arrayRegister)) return null
    val returnType = reference.returnType.toString()
    if (!returnType.startsWith("L") || !returnType.endsWith(';') || returnType == OBJECT_DESCRIPTOR) {
        return null
    }
    val resultInstruction = getOrNull(arrayIndex + 3)
    if (resultInstruction?.opcode != Opcode.MOVE_RESULT_OBJECT) return null
    val resultRegister = (resultInstruction as? OneRegisterInstruction)?.registerA ?: return null

    if (!hasSingleHomeTabFieldConsumer(
        startIndex = arrayIndex + 4,
        resultRegister = resultRegister,
        returnType = returnType,
        receiverRegister = receiverRegister,
    )) return null
    return HomeTabListFactory(returnType, arrayRegister, resultRegister)
}

private fun List<Instruction>.hasSingleHomeTabFieldConsumer(
    startIndex: Int,
    resultRegister: Int,
    returnType: String,
    receiverRegister: Int,
): Boolean {
    val valueRegisters = linkedSetOf(resultRegister)
    var consumerCount = 0
    for (index in startIndex until size) {
        val instruction = this[index]

        if (instruction.opcode in OBJECT_MOVE_OPCODES) {
            val move = instruction as? TwoRegisterInstruction ?: return false
            if (move.registerB in valueRegisters) {
                valueRegisters += move.registerA
            } else {
                valueRegisters -= move.registerA
            }
            continue
        }

        if (instruction.opcode == Opcode.IPUT_OBJECT) {
            val field = instruction.getReference<FieldReference>() ?: return false
            val store = instruction as? TwoRegisterInstruction ?: return false
            if (store.registerA in valueRegisters) {
                if (
                    field.type != returnType ||
                        store.registerB !in receiverAliasesAt(index, receiverRegister)
                ) {
                    return false
                }
                consumerCount++
                valueRegisters -= store.registerA
            }
            continue
        }

        if (instruction.registersUsed.any { it in valueRegisters }) return false
    }

    return consumerCount == 1
}

private val OBJECT_MOVE_OPCODES =
    setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)

private fun List<Instruction>.receiverAliasesAt(index: Int, receiverRegister: Int): Set<Int> {
    val aliases = linkedSetOf(receiverRegister)
    for (instruction in take(index)) {
        if (instruction.opcode !in OBJECT_MOVE_OPCODES) continue
        val move = instruction as? TwoRegisterInstruction ?: continue
        if (move.registerB in aliases) {
            aliases += move.registerA
        } else {
            aliases -= move.registerA
        }
    }
    return aliases
}
