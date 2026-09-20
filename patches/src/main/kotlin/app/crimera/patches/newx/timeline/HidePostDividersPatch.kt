package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.ToggleSettingDefinition
import app.crimera.patches.newx.settings.injectReadWithDefault
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val ANDROID_SCOPE = "Lcom/x/android/"
private const val URT_UI_SCOPE = "Lcom/x/urt/ui/"
private const val COMPOSE_FOUNDATION_SCOPE = "Landroidx/compose/foundation/"
private const val COMPOSE_RUNTIME_INTERNAL_SCOPE = "Landroidx/compose/runtime/internal/"
private const val LAZY_PACKAGE = "Landroidx/compose/foundation/lazy/"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val INSETS_DESCRIPTOR = "I"
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val FUNCTION3_DESCRIPTOR = "Lkotlin/jvm/functions/Function3;"
private const val TIMELINE_HEADER_KEY_ANCHOR = "timeline_header_key"
/** The synthetic Compose lambda wraps the post content in the optional divider container. */
private object NewXPostDividerRendererFingerprint : Fingerprint(
    definingClass = ANDROID_SCOPE,
    name = "invoke",
    parameters = listOf(OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR),
    returnType = OBJECT_DESCRIPTOR,
    custom = { method, classDef ->
        val packageRelativeName = classDef.type.removePrefix(ANDROID_SCOPE)
        classDef.type.startsWith(ANDROID_SCOPE) &&
            !packageRelativeName.contains('/') &&
            classDef.methods.any(Method::isPostDividerWrapperConstructor) &&
            method.instructions.count(Instruction::isPostDividerCall) == 2
    },
)

/** The reply facepile draws another connector behind the stacked reply avatars. */
private object NewXReplyFacepileDividerFingerprint : Fingerprint(
    parameters = listOf("L", "L", MODIFIER_DESCRIPTOR, COMPOSER_DESCRIPTOR, INSETS_DESCRIPTOR),
    returnType = "V",
    custom = { method, _ -> method.hasReplyFacepileDividerFlow() },
)

/** The URT timeline content builder that adds timeline items and inter-module divider separators. */
private object NewXTimelineModuleBuilderFingerprint : Fingerprint(
    definingClass = URT_UI_SCOPE,
    name = "invoke",
    parameters = listOf(OBJECT_DESCRIPTOR),
    returnType = OBJECT_DESCRIPTOR,
    custom = { method, classDef ->
        val packageRelativeName = classDef.type.removePrefix(URT_UI_SCOPE)
        classDef.type.startsWith(URT_UI_SCOPE) &&
            !packageRelativeName.contains('/') &&
            method.instructions.any { instruction ->
                instruction.getReference<StringReference>()?.string == TIMELINE_HEADER_KEY_ANCHOR
            } &&
            method.instructions.toList().hasTimelineModuleDividerItem()
    },
)

private data class PostDividerCall(
    val index: Int,
    val booleanRegister: Int,
)

private data class ReplyFacepileDrawCall(
    val index: Int,
    val inputModifierRegister: Int,
    val resultRegister: Int,
)

private fun Method.isPostDividerWrapperConstructor(): Boolean =
    name == "<init>" &&
        parameterTypes.map(CharSequence::toString).let { parameters ->
            parameters.size == 5 &&
                parameters[0] == MODIFIER_DESCRIPTOR &&
                parameters[1] == "Z" &&
                parameters[2].isObjectDescriptor() &&
                parameters[3].isObjectDescriptor() &&
                parameters[4] == INSETS_DESCRIPTOR
        }

private fun Instruction.isPostDividerCall(): Boolean {
    if (opcode != Opcode.INVOKE_STATIC && opcode != Opcode.INVOKE_STATIC_RANGE) return false
    val reference = getReference<MethodReference>() ?: return false
    val parameters = reference.parameterTypes.map(CharSequence::toString)
    return reference.returnType == "V" &&
        parameters.size == 6 &&
        parameters[0] == MODIFIER_DESCRIPTOR &&
        parameters[1] == "Z" &&
        parameters[2].isObjectDescriptor() &&
        parameters[3] == COMPOSER_DESCRIPTOR &&
        parameters[4] == INSETS_DESCRIPTOR &&
        parameters[5] == INSETS_DESCRIPTOR
}

private fun Instruction.isDrawModifierCall(): Boolean {
    if (opcode != Opcode.INVOKE_STATIC && opcode != Opcode.INVOKE_STATIC_RANGE) return false
    val reference = getReference<MethodReference>() ?: return false
    return reference.returnType == MODIFIER_DESCRIPTOR &&
        reference.parameterTypes.map(CharSequence::toString) ==
            listOf(MODIFIER_DESCRIPTOR, FUNCTION1_DESCRIPTOR)
}

private fun Instruction.callsCollectionMethod(
    definingClass: String,
    name: String,
    parameters: List<String>,
    returnType: String,
): Boolean {
    val reference = getReference<MethodReference>() ?: return false
    return reference.definingClass == definingClass &&
        reference.name == name &&
        reference.parameterTypes.map(CharSequence::toString) == parameters &&
        reference.returnType == returnType
}

private fun Method.hasReplyFacepileDividerFlow(): Boolean {
    if (!AccessFlags.STATIC.isSet(accessFlags)) return false
    val instructions = implementation?.instructions?.toList() ?: return false
    if (instructions.count(Instruction::isDrawModifierCall) != 1) return false

    val listFields =
        instructions.mapNotNull { instruction ->
            if (instruction.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
            instruction.getReference<FieldReference>()?.takeIf { field ->
                field.type == "Ljava/util/ArrayList;"
            }
        }.distinctBy(FieldReference::toString)
    if (listFields.size != 1) return false

    return instructions.any { instruction ->
        instruction.callsCollectionMethod(
            definingClass = "Ljava/util/ArrayList;",
            name = "size",
            parameters = emptyList(),
            returnType = "I",
        )
    } && instructions.any { instruction ->
        instruction.callsCollectionMethod(
            definingClass = "Ljava/lang/Iterable;",
            name = "iterator",
            parameters = emptyList(),
            returnType = "Ljava/util/Iterator;",
        )
    } && instructions.any { instruction ->
        instruction.callsCollectionMethod(
            definingClass = "Ljava/util/Iterator;",
            name = "hasNext",
            parameters = emptyList(),
            returnType = "Z",
        )
    } && instructions.any { instruction ->
        instruction.callsCollectionMethod(
            definingClass = "Ljava/util/Iterator;",
            name = "next",
            parameters = emptyList(),
            returnType = OBJECT_DESCRIPTOR,
        )
    }
}

private fun String.isObjectDescriptor(): Boolean = startsWith('L') && endsWith(';')

private fun Instruction.isLazyListItemCall(): Boolean {
    if (opcode != Opcode.INVOKE_STATIC && opcode != Opcode.INVOKE_STATIC_RANGE) return false
    val reference = getReference<MethodReference>() ?: return false
    val parameters = reference.parameterTypes.map(CharSequence::toString)
    return reference.returnType == "V" &&
        parameters.size == 4 &&
        parameters[0].startsWith(LAZY_PACKAGE) &&
        parameters[1] == OBJECT_DESCRIPTOR &&
        parameters[2] == FUNCTION3_DESCRIPTOR &&
        parameters[3] == INSETS_DESCRIPTOR
}

private fun Instruction.isZeroConstant(): Boolean =
    (opcode == Opcode.CONST_4 || opcode == Opcode.CONST_16 || opcode == Opcode.CONST) &&
        this is NarrowLiteralInstruction &&
        narrowLiteral == 0

private fun MethodReference.isComposeFoundationLambdaAdapterConstructor(): Boolean =
    name == "<init>" &&
        definingClass.startsWith(COMPOSE_FOUNDATION_SCOPE) &&
        returnType == "V" &&
        parameterTypes.map(CharSequence::toString) == listOf(OBJECT_DESCRIPTOR, INSETS_DESCRIPTOR)

private fun MethodReference.isComposeLambdaWrapperConstructor(): Boolean =
    name == "<init>" &&
        definingClass.startsWith(COMPOSE_RUNTIME_INTERNAL_SCOPE) &&
        returnType == "V" &&
        parameterTypes.map(CharSequence::toString) ==
            listOf(OBJECT_DESCRIPTOR, "Z", INSETS_DESCRIPTOR)

private fun List<Instruction>.isTimelineModuleDividerItem(
    index: Int,
    instruction: Instruction,
): Boolean = index in timelineModuleDividerItemIndices()

/**
 * Zero-key divider items, preferring inline-built content lambdas. Older releases build
 * the divider lambdas inline (foundation adapter + runtime wrapper ctors); newer ones
 * hoist the content into a shared static holder (urt/ui/a->e on alpha.04). Both shapes
 * coexist on older targets, so the inline shape wins when present: that preserves the
 * established hook, while the hoisted shape keeps newer releases working.
 */
private fun List<Instruction>.timelineModuleDividerItemIndices(): List<Int> {
    val inline =
        indices.filter { index ->
            isZeroKeyDividerItem(index, this[index]) &&
                hasInlineDividerLambdas(index)
        }
    if (inline.isNotEmpty()) return inline
    return indices.filter { index ->
        isZeroKeyDividerItem(index, this[index]) && hasHoistedDividerLambda(index)
    }
}

private fun List<Instruction>.isZeroKeyDividerItem(
    index: Int,
    instruction: Instruction,
): Boolean {
    if (!instruction.isLazyListItemCall()) return false
    val keyRegister = instruction.registersUsed.getOrNull(1) ?: return false
    val keyConstant =
        (maxOf(0, index - 4) until index)
            .asSequence()
            .map { candidateIndex -> get(candidateIndex) }
            .filter { candidate ->
                candidate is OneRegisterInstruction && candidate.registerA == keyRegister
            }
            .lastOrNull()
    return keyConstant?.isZeroConstant() == true
}

private fun List<Instruction>.hasInlineDividerLambdas(index: Int): Boolean {
    val precedingInstructions = subList(maxOf(0, index - 8), index)
    return precedingInstructions.any { candidate ->
        candidate.getReference<MethodReference>()?.isComposeFoundationLambdaAdapterConstructor() == true
    } && precedingInstructions.any { candidate ->
        candidate.getReference<MethodReference>()?.isComposeLambdaWrapperConstructor() == true
    }
}

private fun List<Instruction>.hasHoistedDividerLambda(index: Int): Boolean {
    val contentRegister = get(index).registersUsed.getOrNull(2) ?: return false
    return subList(maxOf(0, index - 8), index).any { candidate ->
        candidate.opcode == Opcode.SGET_OBJECT &&
            (candidate as? OneRegisterInstruction)?.registerA == contentRegister &&
            candidate.getReference<FieldReference>()?.type?.startsWith(COMPOSE_RUNTIME_INTERNAL_SCOPE) == true
    }
}

private fun List<Instruction>.hasTimelineModuleDividerItem(): Boolean =
    timelineModuleDividerItemIndices().size == 1

private fun resolvePostDividerCalls(method: Method): List<PostDividerCall> {
    val helperReferences =
        method.instructions
            .mapNotNull { instruction ->
                if (!instruction.isPostDividerCall()) return@mapNotNull null
                instruction.getReference<MethodReference>()
            }.distinctBy(MethodReference::toString)
    requireExactlyOne(
        label = "NewX post divider helper",
        candidates = helperReferences,
    )

    return method.instructions.mapIndexedNotNull { index, instruction ->
        if (!instruction.isPostDividerCall()) return@mapIndexedNotNull null
        val registers = instruction.registersUsed
        if (registers.size != 6) {
            throw PatchException(
                "NewX post divider call has ${registers.size} registers; expected " +
                    "6: $instruction",
            )
        }
        PostDividerCall(index = index, booleanRegister = registers[1])
    }
}

private fun resolveReplyFacepileDrawCall(method: Method): ReplyFacepileDrawCall {
    val instructions = method.instructions.toList()
    val drawCallIndex =
        requireExactlyOne(
            label = "NewX reply facepile draw modifier call",
            candidates =
                instructions.mapIndexedNotNull { index, instruction ->
                    index.takeIf { instruction.isDrawModifierCall() }
                },
        )

    val drawCall = instructions[drawCallIndex]
    val inputModifierRegister =
        drawCall.registersUsed.firstOrNull()
            ?: throw PatchException(
                "NewX reply facepile draw modifier call has no input modifier register: " +
                    drawCall,
            )
    val drawResult = instructions.getOrNull(drawCallIndex + 1) as? OneRegisterInstruction
    if (drawResult?.opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException(
            "NewX reply facepile draw modifier call has no move-result-object: " +
                drawCall,
        )
    }
    if (instructions.getOrNull(drawCallIndex + 2) == null) {
        throw PatchException("NewX reply facepile draw modifier call has no continuation")
    }
    val resultRegister = drawResult.registerA
    if (inputModifierRegister !in 0..0xffff || resultRegister !in 0..0xffff) {
        throw PatchException(
            "NewX reply facepile draw modifier call requires 16-bit registers: " +
                "input v$inputModifierRegister, result v$resultRegister",
        )
    }
    return ReplyFacepileDrawCall(
        index = drawCallIndex,
        inputModifierRegister = inputModifierRegister,
        resultRegister = resultRegister,
    )
}

context(context: BytecodePatchContext)
private fun patchReplyFacepileDivider(
    setting: ToggleSettingDefinition,
) {
    val renderer =
        requireExactlyOne(
            label = "NewX reply facepile divider renderer",
            candidates = NewXReplyFacepileDividerFingerprint.scopedMatchAllOrNull().orEmpty(),
        )
    val originalMethod = renderer.method
    val originalRegisterCount =
        originalMethod.implementation?.registerCount
            ?: throw PatchException("NewX reply facepile divider renderer has no implementation")
    val owner = context.mutableClassDefBy(renderer.originalClassDef.type)
    val method =
        originalMethod.cloneMutable(
            additionalRegisters = originalMethod.numberOfParameterRegisters + 2,
        )
    owner.methods.remove(originalMethod)
    owner.methods.add(method)

    val drawCall = resolveReplyFacepileDrawCall(method)
    val continuation =
        method.instructions.getOrNull(drawCall.index + 2)
            ?: throw PatchException("NewX reply facepile draw call has no continuation")
    val drawInstruction = method.instructions[drawCall.index]
    val settingRegister = originalRegisterCount
    val read =
        setting.injectReadWithDefault(
            method = method,
            index = drawCall.index,
            defaultValue = false,
            registerRange = settingRegister..settingRegister + 1,
        )
    val drawLabel = "piko_newx_hide_post_dividers_reply_facepile_draw"
    val continuationLabel = "piko_newx_hide_post_dividers_reply_facepile_continue"
    method.addInstructionsWithLabels(
        read.nextIndex,
        """
            if-eqz v${read.register}, :$drawLabel
            move-object/from16 v${drawCall.resultRegister}, v${drawCall.inputModifierRegister}
            goto :$continuationLabel
        """.trimIndent(),
        ExternalLabel(drawLabel, drawInstruction),
        ExternalLabel(continuationLabel, continuation),
    )
}

private fun resolveTimelineModuleDividerCallIndex(method: Method): Int {
    val instructions = method.instructions.toList()
    return requireExactlyOne(
        label = "NewX timeline module divider item",
        candidates =
            instructions.mapIndexedNotNull { index, instruction ->
                index.takeIf { instructions.isTimelineModuleDividerItem(index, instruction) }
            },
    )
}

context(context: BytecodePatchContext)
private fun patchTimelineModuleDividers(
    setting: ToggleSettingDefinition,
) {
    val builder =
        requireExactlyOne(
            label = "NewX timeline module builder",
            candidates = NewXTimelineModuleBuilderFingerprint.scopedMatchAllOrNull().orEmpty(),
        )
    val originalMethod = builder.method
    val originalRegisterCount =
        originalMethod.implementation?.registerCount
            ?: throw PatchException("NewX timeline module builder has no implementation")
    val owner = context.mutableClassDefBy(builder.originalClassDef.type)
    val method =
        originalMethod.cloneMutable(
            additionalRegisters = originalMethod.numberOfParameterRegisters + 2,
        )
    owner.methods.remove(originalMethod)
    owner.methods.add(method)

    val callIndex = resolveTimelineModuleDividerCallIndex(method)
    val continuation =
        method.instructions.getOrNull(callIndex + 1)
            ?: throw PatchException("NewX timeline module divider call is at the end of the method")

    val settingRegister = originalRegisterCount
    val read =
        setting.injectReadWithDefault(
            method = method,
            index = callIndex,
            defaultValue = false,
            registerRange = settingRegister..settingRegister + 1,
        )
    val label = "piko_newx_hide_post_dividers_module_divider"
    method.addInstructionsWithLabels(
        read.nextIndex,
        """
            if-nez v${read.register}, :$label
        """.trimIndent(),
        ExternalLabel(label, continuation),
    )
}

@Suppress("unused")
val newXHidePostDividersPatch =
    bytecodePatch(
        default = false,
        name = "NewX: Hide post dividers",
        description = "Removes post and reply dividers shown in NewX timelines.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val hidePostDividers =
            newXToggle(
                id = "newx.timeline.hide_post_dividers",
                category = Categories.TIMELINE,
                strings = settingStrings("piko_newx_hide_post_dividers"),
                order = 325,
                defaultValue = false,
            )

        execute {
            val renderer =
                requireExactlyOne(
                    label = "NewX post divider renderer",
                    candidates = NewXPostDividerRendererFingerprint.scopedMatchAllOrNull().orEmpty(),
                )
            val originalMethod = renderer.method
            val originalCalls = resolvePostDividerCalls(originalMethod)
            if (originalCalls.size != 2) {
                throw PatchException(
                    "Expected two NewX post divider calls, found ${originalCalls.size}: " +
                        "${originalMethod}",
                )
            }
            val originalRegisterCount =
                originalMethod.implementation?.registerCount
                    ?: throw PatchException("NewX post divider renderer has no implementation")
            val owner = mutableClassDefBy(renderer.originalClassDef.type)
            val method =
                originalMethod.cloneMutable(
                    additionalRegisters =
                        originalMethod.numberOfParameterRegisters + originalCalls.size * 2,
                )
            owner.methods.remove(originalMethod)
            owner.methods.add(method)

            val calls = resolvePostDividerCalls(method)
            if (calls.size != originalCalls.size) {
                throw PatchException(
                    "NewX post divider call count changed while preparing the patch: " +
                        "before=${originalCalls.size}, after=${calls.size}",
                )
            }
            calls.sortedByDescending(PostDividerCall::index).forEachIndexed { ordinal, call ->
                val continuation = method.instructions[call.index]
                val settingRegister = originalRegisterCount + ordinal * 2
                val read =
                    hidePostDividers.injectReadWithDefault(
                        method = method,
                        index = call.index,
                        defaultValue = false,
                        registerRange = settingRegister..settingRegister + 1,
                    )
                val label = "piko_newx_hide_post_dividers_continue_$ordinal"
                method.addInstructionsWithLabels(
                    read.nextIndex,
                    """
                        if-eqz v${read.register}, :$label
                        const/16 v${call.booleanRegister}, 0x0
                    """.trimIndent(),
                    ExternalLabel(label, continuation),
                )
            }

            patchReplyFacepileDivider(hidePostDividers)
            patchTimelineModuleDividers(hidePostDividers)
        }
    }
