package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.returnVoidIfEnabled
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val FUNCTION_ZERO_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val FUNCTION_THREE_DESCRIPTOR = "Lkotlin/jvm/functions/Function3;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val COMPOSE_ANIMATION_SCOPE = "Landroidx/compose/animation/"

private fun newPostButtonVisibilityFilter() =
    methodCall(
        opcode = Opcode.INVOKE_INTERFACE,
        name = "isVisible",
        parameters = listOf(),
        returnType = "Z",
    )

private object NewXNewPostButtonCandidateFingerprint : Fingerprint(
    parameters =
        listOf(
            "I",
            COMPOSER_DESCRIPTOR,
            MODIFIER_DESCRIPTOR,
            FUNCTION_ZERO_DESCRIPTOR,
        ),
    returnType = "V",
    filters = listOf(newPostButtonVisibilityFilter()),
    custom = { method, _ -> method.isNewPostButtonRendererCandidate() },
)

// 12.29 relocated the renderer and lowered its Compose ABI to (Modifier, Function0, Composer, I, I):
// content parameters first, then Composer and the two changed/default bitmasks. Resolve both shapes
// and share the common mutation below.
private object NewXNewPostButtonComposeFlagCandidateFingerprint : Fingerprint(
    parameters =
        listOf(
            MODIFIER_DESCRIPTOR,
            FUNCTION_ZERO_DESCRIPTOR,
            COMPOSER_DESCRIPTOR,
            "I",
            "I",
        ),
    returnType = "V",
    filters = listOf(newPostButtonVisibilityFilter()),
    custom = { method, _ -> method.isNewPostButtonRendererCandidate() },
)

private val OBJECT_MOVE_OPCODES =
    setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)

private fun MethodReference.isAnimatedVisibilityCall(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return definingClass.startsWith(COMPOSE_ANIMATION_SCOPE) &&
        returnType == "V" &&
        parameters.size == 9 &&
        parameters[0] == "Z" &&
        parameters[1] == MODIFIER_DESCRIPTOR &&
        parameters[4] == STRING_DESCRIPTOR &&
        parameters[5] == FUNCTION_THREE_DESCRIPTOR &&
        parameters[6] == COMPOSER_DESCRIPTOR &&
        parameters[7] == "I" &&
        parameters[8] == "I"
}

private fun Instruction.destinationRegister(): Int? {
    if (!opcode.setsRegister()) return null
    return when (this) {
        is ThreeRegisterInstruction -> registerA
        is TwoRegisterInstruction -> registerA
        is OneRegisterInstruction -> registerA
        else -> null
    }
}

private fun List<Instruction>.originatesFromObjectParameter(
    useIndex: Int,
    argumentRegister: Int,
    parameterRegister: Int,
): Boolean {
    if (argumentRegister == parameterRegister) return true

    var trackedRegister = argumentRegister
    var searchEnd = useIndex
    val visitedRegisters = mutableSetOf<Int>()

    while (visitedRegisters.add(trackedRegister)) {
        val writeIndex =
            (searchEnd - 1 downTo 0).firstOrNull { index ->
                this[index].destinationRegister() == trackedRegister
            } ?: return false
        val write = this[writeIndex]
        if (write.opcode !in OBJECT_MOVE_OPCODES) return false
        val move = write as? TwoRegisterInstruction ?: return false
        trackedRegister = move.registerB
        // A conditional default assignment can overwrite the parameter slot before the alias
        // (e.g. `sget-object p0, Modifier.Companion`), so reaching the parameter register proves
        // provenance without requiring the slot's own pre-write.
        if (trackedRegister == parameterRegister) return true
        searchEnd = writeIndex
    }
    return false
}

/**
 * Identifies the shared new-post renderer without relying on its obfuscated owner/method or on
 * Kotlin parameter-name strings. The modifier provenance rejects the recommended-list renderer,
 * which has the same Compose ABI and visibility call but supplies an invoke-produced modifier.
 */
internal fun Method.isNewPostButtonRendererCandidate(): Boolean {
    if (!AccessFlags.STATIC.isSet(accessFlags) || returnType != "V") return false

    val parameters = parameterTypes.map(CharSequence::toString)
    val modifierParameterIndex = parameters.indexOf(MODIFIER_DESCRIPTOR)
    if (modifierParameterIndex < 0 || parameters.count { it == MODIFIER_DESCRIPTOR } != 1) return false

    val implementation = implementation ?: return false
    val instructions = implementation.instructions.toList()
    val visibilityCallCount =
        instructions.count { instruction ->
            if (instruction.opcode != Opcode.INVOKE_INTERFACE) return@count false
            val reference = instruction.getReference<MethodReference>() ?: return@count false
            reference.name == "isVisible" &&
                reference.parameterTypes.isEmpty() &&
                reference.returnType == "Z"
        }
    if (visibilityCallCount != 1) return false

    val animatedVisibilityCalls =
        instructions.mapIndexedNotNull { index, instruction ->
            if (
                instruction.opcode != Opcode.INVOKE_STATIC &&
                instruction.opcode != Opcode.INVOKE_STATIC_RANGE
            ) {
                return@mapIndexedNotNull null
            }
            val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
            index.takeIf { reference.isAnimatedVisibilityCall() }
        }
    if (animatedVisibilityCalls.size != 1) return false
    val animatedVisibilityIndex = animatedVisibilityCalls.single()
    val animatedVisibility = instructions[animatedVisibilityIndex]
    val argumentRegisters = animatedVisibility.registersUsed
    if (argumentRegisters.size != 9) return false

    val firstParameterRegister =
        implementation.registerCount - parameters.sumOf(String::registerWidth)
    val modifierParameterRegister =
        firstParameterRegister + parameters.take(modifierParameterIndex).sumOf(String::registerWidth)
    return instructions.originatesFromObjectParameter(
        useIndex = animatedVisibilityIndex,
        argumentRegister = argumentRegisters[1],
        parameterRegister = modifierParameterRegister,
    )
}

private fun String.registerWidth(): Int = if (this == "J" || this == "D") 2 else 1

@Suppress("unused")
val hideNewPostButtonPatch =
    bytecodePatch(
        name = "NewX: Hide compose button",
        description = "Removes the compose button from NewX timelines.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val hideNewPostButton =
            newXToggle(
                id = "newx.timeline.hide_new_post_button",
                category = Categories.TIMELINE,
                strings = settingStrings("piko_newx_hide_new_post_button"),
                order = 300,
                defaultValue = false,
            )

        execute {
            val candidates =
                buildList {
                    addAll(NewXNewPostButtonCandidateFingerprint.scopedMatchAllOrNull().orEmpty())
                    addAll(NewXNewPostButtonComposeFlagCandidateFingerprint.scopedMatchAllOrNull().orEmpty())
                }
            val renderer =
                requireExactlyOne(
                    label = "NewX new-post button renderer",
                    candidates = candidates,
                )
            hideNewPostButton.returnVoidIfEnabled(renderer.method, 0)
        }
    }
