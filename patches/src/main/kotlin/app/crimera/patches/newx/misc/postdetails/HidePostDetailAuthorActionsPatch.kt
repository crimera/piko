package app.crimera.patches.newx.misc.postdetails

import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.bytecode.RegisterLimit
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.resolveIntegerLiteralOnCurrentPath
import app.crimera.patches.common.valueReachesRegister
import app.crimera.patches.common.scopedMatchAll
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.crimera.patches.settings.ToggleSettingDefinition
import app.crimera.patches.settings.settingStrings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resource.resourceId
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val POST_ITEM_SCOPE = "Lcom/x/urt/items/post/"
private const val FRIENDSHIP_SCOPE = "Lcom/x/subsystem/friendship/"
private const val BUTTON_SCOPE = "Lcom/x/ui/common/ports/buttons/"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val FUNCTION_ZERO_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val MESSAGE_BUTTON_RESOURCE_NAME = "xchat_bottom_sheet_message_button"
private const val FOLLOW_RESOURCE_NAME = "follow"

private val STATIC_INVOKE_OPCODES = setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)
private val INVOKE_OPCODES = STATIC_INVOKE_OPCODES + setOf(
    Opcode.INVOKE_VIRTUAL,
    Opcode.INVOKE_VIRTUAL_RANGE,
    Opcode.INVOKE_INTERFACE,
    Opcode.INVOKE_INTERFACE_RANGE,
    Opcode.INVOKE_DIRECT,
    Opcode.INVOKE_DIRECT_RANGE,
    Opcode.INVOKE_SUPER,
    Opcode.INVOKE_SUPER_RANGE,
)
private object NewXPostDetailAuthorActionRendererFingerprint : Fingerprint(
    definingClass = POST_ITEM_SCOPE,
    returnType = "V",
    filters = listOf(
        literal({ resourceId(ResourceType.STRING, MESSAGE_BUTTON_RESOURCE_NAME) }),
    ),
    custom = { method, _ -> method.isPostDetailAuthorActionRendererContract() },
)

internal fun Method.isPostDetailAuthorActionRendererContract(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return returnType == "V" &&
        AccessFlags.STATIC.isSet(accessFlags) &&
        parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
        parameters.any { it.startsWith(FRIENDSHIP_SCOPE) }
}

private data class MessageButtonLocation(val buttonCallIndex: Int)

/** Resolves the Message label through its resource lookup to the button composable that consumes it. */
private fun resolveMessageButtonLocation(method: Method): MessageButtonLocation {
    val instructions = method.implementation?.instructions?.toList()
        ?: throw PatchException("NewX post-detail author action renderer has no body: $method")
    val messageResourceId = resourceId(ResourceType.STRING, MESSAGE_BUTTON_RESOURCE_NAME).toInt()
    val resourceLiteralIndex = requireExactlyOne(
        label = "NewX post-detail Message resource in $method",
        candidates = instructions.withIndex().filter { (_, instruction) ->
            (instruction as? NarrowLiteralInstruction)?.narrowLiteral == messageResourceId
        }.map { (index, _) -> index },
    )
    val stringLookup = resolveStringResourceLookup(
        method = method,
        instructions = instructions,
        resourceLiteralIndex = resourceLiteralIndex,
        resourceId = messageResourceId,
    )
    val buttonCallIndex = resolveStringButtonCall(
        method = method,
        instructions = instructions,
        stringLookup = stringLookup,
    )
    return MessageButtonLocation(buttonCallIndex)
}

private data class StringResourceLookup(
    val resultIndex: Int,
    val resultRegister: Int,
)

private fun resolveStringResourceLookup(
    method: Method,
    instructions: List<Instruction>,
    resourceLiteralIndex: Int,
    resourceId: Int,
): StringResourceLookup {
    val candidates = buildList {
        for (index in resourceLiteralIndex + 1 until instructions.size) {
            val instruction = instructions[index]
            if (!instruction.isStaticInvocation()) continue
            val reference = instruction.getReference<MethodReference>() ?: continue
            if (reference.returnType != "Ljava/lang/String;") continue
            val parameters = reference.parameterTypes.map(CharSequence::toString)
            if (parameters.count { it == COMPOSER_DESCRIPTOR } != 1) continue

            val resourceArguments = buildList {
                for (parameterIndex in parameters.indices) {
                    if (parameters[parameterIndex] != "I") continue
                    val register = instruction.argumentRegister(reference, parameterIndex) ?: continue
                    add(register)
                }
            }
            if (resourceArguments.none { argumentRegister ->
                    instructions.resolveIntegerLiteralOnCurrentPath(index, argumentRegister) == resourceId
                }
            ) {
                continue
            }

            val resultIndex = index + 1
            val result = instructions.getOrNull(resultIndex)
            if (result?.opcode != Opcode.MOVE_RESULT_OBJECT) continue
            val resultRegister = (result as? OneRegisterInstruction)?.registerA ?: continue
            add(StringResourceLookup(resultIndex, resultRegister))
        }
    }
    return requireExactlyOne("NewX post-detail Message string lookup in $method", candidates)
}

private fun resolveStringButtonCall(
    method: Method,
    instructions: List<Instruction>,
    stringLookup: StringResourceLookup,
): Int {
    val candidates = buildList {
        for (index in stringLookup.resultIndex + 1 until instructions.size) {
            val instruction = instructions[index]
            if (!instruction.isStaticInvocation()) continue
            val reference = instruction.getReference<MethodReference>() ?: continue
            if (reference.returnType != "V") continue
            val parameters = reference.parameterTypes.map(CharSequence::toString)
            if (
                parameters.none { it == "Ljava/lang/String;" } ||
                    parameters.count { it == COMPOSER_DESCRIPTOR } != 1 ||
                    !parameters.contains(MODIFIER_DESCRIPTOR)
            ) {
                continue
            }

            val consumesMessageLabel = parameters.indices
                .filter { parameterIndex -> parameters[parameterIndex] == "Ljava/lang/String;" }
                .any { parameterIndex ->
                    val argumentRegister = instruction.argumentRegister(reference, parameterIndex)
                        ?: return@any false
                    instructions.valueReachesRegister(
                        valueIndex = stringLookup.resultIndex,
                        valueRegister = stringLookup.resultRegister,
                        targetIndex = index,
                        targetRegister = argumentRegister,
                    )
                }
            if (!consumesMessageLabel) continue
            add(index)
        }
    }
    return requireExactlyOne("NewX post-detail Message button composable in $method", candidates)
}

context(context: BytecodePatchContext)
private fun resolveFollowButtonCall(method: Method): Int {
    val followResourceId = resourceId(ResourceType.STRING, FOLLOW_RESOURCE_NAME).toInt()
    val instructions = method.implementation?.instructions?.toList()
        ?: throw PatchException("NewX post-detail author action renderer has no body: $method")
    val candidates = buildList {
        for ((index, instruction) in instructions.withIndex()) {
            if (!instruction.isStaticInvocation()) continue
            val reference = instruction.getReference<MethodReference>() ?: continue
            if (!reference.isFollowActionComposable()) continue
            if (!reference.rendersFollowResource(followResourceId)) continue
            add(index)
        }
    }
    return requireExactlyOne("NewX post-detail Follow button composable in $method", candidates)
}

internal fun MethodReference.isFollowActionComposable(): Boolean {
    if (returnType != "V") return false
    val parameters = parameterTypes.map(CharSequence::toString)
    return parameters.count { it.startsWith(FRIENDSHIP_SCOPE) } == 1 &&
        parameters.count { it == MODIFIER_DESCRIPTOR } == 1 &&
        parameters.count { it.startsWith(BUTTON_SCOPE) } >= 1 &&
        parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
        parameters.any { it == FUNCTION_ZERO_DESCRIPTOR }
}

context(context: BytecodePatchContext)
private fun MethodReference.rendersFollowResource(resourceId: Int): Boolean {
    val renderer = context.methodByOrNull(this) ?: return false
    if (renderer.containsResourceLiteral(resourceId)) return true

    val labelHelpers = buildList {
        for (callee in renderer.outgoingMethodReferences()) {
            val helper = context.methodByOrNull(callee) ?: continue
            if (helper.containsResourceLiteral(resourceId)) add(helper)
        }
    }
    return requireAtMostOne("NewX Follow label helper for $this", labelHelpers) != null
}

private fun BytecodePatchContext.methodByOrNull(reference: MethodReference): Method? {
    val owner = classDefByOrNull(reference.definingClass) ?: return null
    val candidates = owner.methods.filter { method -> method.matches(reference) }
    return requireAtMostOne("NewX method reference $reference", candidates)
}

private fun Method.matches(reference: MethodReference): Boolean =
    definingClass == reference.definingClass &&
        name == reference.name &&
        returnType == reference.returnType &&
        parameterTypes.map(CharSequence::toString) == reference.parameterTypes.map(CharSequence::toString)

private fun Method.containsResourceLiteral(resourceId: Int): Boolean =
    implementation?.instructions?.any { instruction ->
        (instruction as? NarrowLiteralInstruction)?.narrowLiteral == resourceId
    } == true

private fun Method.outgoingMethodReferences(): List<MethodReference> = buildList {
    val methodImplementation = implementation ?: return@buildList
    for (instruction in methodImplementation.instructions) {
        if (instruction.opcode !in INVOKE_OPCODES) continue
        val reference = instruction.getReference<MethodReference>() ?: continue
        if (none { candidate -> candidate.matches(reference) }) add(reference)
    }
}

private fun MethodReference.matches(reference: MethodReference): Boolean =
    definingClass == reference.definingClass &&
        name == reference.name &&
        returnType == reference.returnType &&
        parameterTypes.map(CharSequence::toString) == reference.parameterTypes.map(CharSequence::toString)

private fun String.registerWidth(): Int = if (this == "J" || this == "D") 2 else 1

private fun Instruction.argumentRegister(
    reference: MethodReference,
    parameterIndex: Int,
): Int? {
    if (!isStaticInvocation()) return null
    val registerOffset = reference.parameterTypes
        .take(parameterIndex)
        .sumOf { parameter -> parameter.toString().registerWidth() }
    return registersUsed.getOrNull(registerOffset)
}

private fun Instruction.isStaticInvocation(): Boolean = opcode in STATIC_INVOKE_OPCODES

internal fun applyPostDetailAuthorActionSkip(
    setting: ToggleSettingDefinition,
    method: MutableMethod,
    callIndex: Int,
) {
    method.insertHook(
        index = callIndex,
        // Incoming branches to a Compose child must execute the setting gate; cleanup after the
        // child remains the branch destination so the caller's Composer group stays balanced.
        relocateBranchTargets = true,
    ) {
        val settingRegister = scratchRegister(RegisterLimit.BYTE)
        constString(settingRegister, setting.id)
        invokeStatic(
            methodReference(
                "$SETTINGS_REGISTRY_DESCRIPTOR->getBooleanOrDefault(Ljava/lang/String;)Z",
            ),
            settingRegister,
        )
        moveResult(settingRegister, "Z")
        ifEqz(settingRegister, Target.Original)
        goto(Target.AfterOriginal(1))
    }
}

@Suppress("unused")
val newXHidePostDetailAuthorActionsPatch =
    bytecodePatch(
        name = "NewX: Hide post author actions",
        description = "Hides the Follow and Message buttons for post authors on post details.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)

        val hidePostDetailAuthorActions =
            newXToggle(
                // Keep this persisted key stable for users upgrading from the original label.
                id = "newx.post_actions_media.hide_post_detail_profile_actions",
                category = Categories.POST_ACTIONS_MEDIA,
                strings = settingStrings("piko_newx_hide_post_detail_author_actions"),
                order = 110,
                defaultValue = false,
            )

        execute {
            val renderer = requireExactlyOne(
                label = "NewX post-detail author action renderer",
                candidates = NewXPostDetailAuthorActionRendererFingerprint.scopedMatchAll(),
            ).method
            val messageButton = resolveMessageButtonLocation(renderer)
            val followButtonCallIndex = resolveFollowButtonCall(renderer)
            val calls = listOf(messageButton.buttonCallIndex, followButtonCallIndex).sortedDescending()

            calls.forEach { callIndex ->
                applyPostDetailAuthorActionSkip(hidePostDetailAuthorActions, renderer, callIndex)
            }
        }
    }
