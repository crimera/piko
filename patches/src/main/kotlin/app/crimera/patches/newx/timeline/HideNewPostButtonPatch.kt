package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.common.hasComposeShape
import app.crimera.patches.common.parameterDescriptors
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.classDefFlatMap
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.crimera.bytecode.RegisterLimit
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val SETTINGS_READ_DESCRIPTOR =
    "$SETTINGS_REGISTRY_DESCRIPTOR->getBooleanOrDefault(Ljava/lang/String;)Z"

/** Resolves the post FAB host by its Compose/menu contract, not its obfuscated owner or label. */
internal fun Method.primaryPostFabRendererCallIndices(): List<Int> {
    if (returnType != "V") return emptyList()
    val parameters = parameterDescriptors()
    if (parameters.firstOrNull() != "I" ||
        !parameters.hasComposeShape(
            required = listOf(MODIFIER_DESCRIPTOR, COMPOSER_DESCRIPTOR, LIST_DESCRIPTOR, FUNCTION1_DESCRIPTOR),
        ) || parameters.count { it == FUNCTION0_DESCRIPTOR } < 2
    ) return emptyList()

    val instructions = implementation?.instructions?.toList() ?: return emptyList()
    return instructions.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
            return@mapIndexedNotNull null
        }
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        val callParameters = reference.parameterTypes.map(CharSequence::toString)
        index.takeIf {
            reference.returnType == "V" &&
                callParameters.containsAll(listOf("Ljava/lang/String;", MODIFIER_DESCRIPTOR, COMPOSER_DESCRIPTOR)) &&
                callParameters.count { type -> type == "J" } >= 2 &&
                callParameters.count { type -> type == FUNCTION0_DESCRIPTOR } >= 2
        }
    }
}

internal fun Method.isNewPostFabHostCandidate(): Boolean =
    primaryPostFabRendererCallIndices().size == 1

@Suppress("unused")
val hideNewPostButtonPatch =
    bytecodePatch(
        name = "NewX: Hide compose button",
        description = "Removes the compose button from NewX timelines.",
    ) {
        unsupportedOnMount()
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
            val fabHostCandidates =
                classDefFlatMap { classDef ->
                    classDef.methods.filter { method -> method.isNewPostFabHostCandidate() }
                        .map { method -> method to classDef }
                }
            val fabHost = requireExactlyOne("NewX post FAB host", fabHostCandidates)
            val (originalMethod, owner) = fabHost
            val method = requireExactlyOne(
                "NewX mutable post FAB host method",
                mutableClassDefBy(owner.type).methods.filter { candidate ->
                    candidate.name == originalMethod.name &&
                        candidate.parameterTypes == originalMethod.parameterTypes &&
                        candidate.returnType == originalMethod.returnType
                },
            )
            val rendererCallIndex = requireExactlyOne(
                "NewX primary post FAB renderer call in $method",
                method.primaryPostFabRendererCallIndices(),
            )
            val instructions = method.instructions.toList()
            val offsets = instructions.runningFold(0) { offset, instruction -> offset + instruction.codeUnits }
            val hookAddress = offsets[rendererCallIndex]
            if (method.implementation?.tryBlocks?.any { block ->
                    block.startCodeAddress <= hookAddress &&
                        block.startCodeAddress + block.codeUnitCount > hookAddress
                } == true
            ) {
                throw PatchException("NewX primary post FAB call is inside a try region: $method")
            }
            if (method.implementation?.tryBlocks?.any { block ->
                    block.exceptionHandlers.any { handler -> handler.handlerCodeAddress == hookAddress }
                } == true
            ) {
                throw PatchException("NewX primary post FAB call is an exception-handler entry: $method")
            }
            if (instructions.mapIndexedNotNull { index, instruction ->
                    if (instruction.opcode != Opcode.PACKED_SWITCH && instruction.opcode != Opcode.SPARSE_SWITCH) {
                        return@mapIndexedNotNull null
                    }
                    val branch = instruction as com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
                    val payloadAddress = offsets[index] + branch.codeOffset
                    val payloadIndex = offsets.indexOf(payloadAddress)
                    val payload = instructions.getOrNull(payloadIndex) as? com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
                        ?: throw PatchException("NewX primary post FAB switch payload is missing: $method")
                    payload.switchElements.any { element -> offsets[index] + element.offset == hookAddress }
                }.any { it }
            ) {
                throw PatchException("NewX primary post FAB call is a switch target and cannot be guarded safely: $method")
            }
            // Ordinary branch labels are relocated to the guard so no branch can bypass the setting.
            // The renderer call itself remains the sole original instruction skipped by the guard.
            method.insertHook(rendererCallIndex, relocateBranchTargets = true) {
                val enabledRegister = scratchRegister(RegisterLimit.BYTE)
                constString(enabledRegister, hideNewPostButton.id)
                invokeStatic(methodReference(SETTINGS_READ_DESCRIPTOR), enabledRegister)
                moveResult(enabledRegister, "Z")
                ifNez(enabledRegister, Target.AfterOriginal(1))
            }
        }
    }
