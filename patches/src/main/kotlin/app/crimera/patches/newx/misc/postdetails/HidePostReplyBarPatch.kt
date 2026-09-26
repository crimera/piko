package app.crimera.patches.newx.misc.postdetails

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.ToggleSettingDefinition
import app.crimera.patches.newx.settings.branchIfEnabled
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.returnVoidIfEnabled
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.patches.newx.utils.requireAtMostOne
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val COMPOSER_MINIMAL_SCOPE = "Lcom/x/composer/minimal/"
private const val POST_DETAIL_SHEET_SCOPE = "Lcom/x/postdetailsheet/"
private const val MEDIA_SCOPE = "Lcom/x/media/"
private const val INLINE_ACTION_BAR_SCOPE = "Lcom/x/inlineactionbar/"
private const val HAZE_SCOPE = "Ldev/chrisbanes/haze/"
private const val IMMERSIVE_CHROME_SCOPE = "Lcom/x/ui/immersive/chrome/"
private const val FOUNDATION_LAYOUT_SCOPE = "Landroidx/compose/foundation/layout/"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"

private val CONDITIONAL_BRANCH_OPCODES =
    setOf(
        Opcode.IF_EQ,
        Opcode.IF_NE,
        Opcode.IF_LT,
        Opcode.IF_GE,
        Opcode.IF_GT,
        Opcode.IF_LE,
        Opcode.IF_EQZ,
        Opcode.IF_NEZ,
        Opcode.IF_LTZ,
        Opcode.IF_GEZ,
        Opcode.IF_GTZ,
        Opcode.IF_LEZ,
    )

/**
 * The inline post-detail composer marks its text field with this stable Compose test tag. The
 * tag is inside the minimal-composer renderer, while the floating new-post action is rendered by
 * its caller, so returning from this renderer hides only the persistent reply bar.
 */
private object NewXPostDetailReplyBarFingerprint : Fingerprint(
    definingClass = COMPOSER_MINIMAL_SCOPE,
    returnType = "V",
    custom = { method, _ -> method.isPostDetailReplyBarRenderer() },
)

/**
 * The post-detail host owns the local inset modifier. Only its semantic data flow is fingerprinted;
 * Compose/R8 method and owner names are intentionally not part of the match.
 */
private object NewXPostDetailNavigationInsetsFingerprint : Fingerprint(
    definingClass = POST_DETAIL_SHEET_SCOPE,
    returnType = "V",
    custom = { method, _ ->
        val parameters = method.parameterTypes.map(CharSequence::toString)
        parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
            parameters.any { it.startsWith(HAZE_SCOPE) } &&
            parameters.any { it.startsWith(POST_DETAIL_SHEET_SCOPE) } &&
            method.hasNavigationInsetsCall()
    },
)

private data class NavigationInsetsRead(
    val destinationRegister: Int,
)

private data class NavigationInsetsHook(
    val method: MutableMethod,
    val callIndex: Int,
    val continuation: Instruction,
)

private data class PhotoViewerNavigationFallbackHook(
    val method: MutableMethod,
    val gateIndex: Int,
    val fallback: Instruction,
)

internal data class ImmersiveActionBarSafeAreaHook(
    val method: MutableMethod,
    val actionBarCallIndex: Int,
    val actionBarCall: Instruction,
    val modifierRegister: Int,
    val composerRegister: Int,
    val windowInsetsHolderProvider: MethodReference,
    val navigationBarsField: FieldReference,
    val insetsPaddingCall: MethodReference,
)

/**
 * How a reply-bar container applies its local navigation-bar inset. GATED containers let a runtime
 * composition flag decide: bottom-anchored immersive surfaces (the fullscreen photo screen) reserve
 * the gesture area while scrolling sheets do not. UNCONDITIONAL containers always apply the inset
 * and the hide setting removes it together with the reply bar.
 */
internal enum class InsetApplicationKind {
    GATED,
    UNCONDITIONAL,
}

private const val INSET_GATE_LOOKBACK = 16
private const val INSET_MERGE_LOOKBACK = 5

private val BRANCH_OPCODES =
    CONDITIONAL_BRANCH_OPCODES +
        setOf(
            Opcode.GOTO,
            Opcode.GOTO_16,
            Opcode.GOTO_32,
            Opcode.PACKED_SWITCH,
            Opcode.SPARSE_SWITCH,
        )

private val READ_ONLY_REGISTER_A_OPCODES =
    CONDITIONAL_BRANCH_OPCODES +
        setOf(
            Opcode.GOTO,
            Opcode.GOTO_16,
            Opcode.GOTO_32,
            Opcode.PACKED_SWITCH,
            Opcode.SPARSE_SWITCH,
            Opcode.NOP,
            Opcode.RETURN_VOID,
            Opcode.RETURN,
            Opcode.RETURN_WIDE,
            Opcode.RETURN_OBJECT,
            Opcode.THROW,
            Opcode.MONITOR_ENTER,
            Opcode.MONITOR_EXIT,
        )

/**
 * Classifies the navigation-insets application at [callIndex]. A GATED application is a diamond: a
 * conditional skip branch jumps past the insets call while the fall-through arm applies it
 * straight-line and rejoins through a goto, and the skip arm leaves the modifier register
 * untouched. A gated application must keep running when the setting hides the reply bar: it is the
 * app's own gesture-area reservation, and deleting it drops the action bar into the navigation pill.
 */
internal fun Method.classifyInsetApplication(callIndex: Int): InsetApplicationKind {
    val instructions = implementation?.instructions?.toList().orEmpty()
    val resultIndex = callIndex + 1
    val destinationRegister =
        (instructions.getOrNull(resultIndex) as? OneRegisterInstruction)?.registerA
            ?: throw PatchException(
                "NewX navigation-insets application is not followed by move-result in $this",
            )
    val gate =
        requireAtMostOne(
            label = "NewX navigation-insets application gate before $callIndex in $this",
            candidates = (maxOf(0, callIndex - INSET_GATE_LOOKBACK) until callIndex).filter { index ->
                val instruction = instructions[index]
                if (instruction.opcode !in CONDITIONAL_BRANCH_OPCODES) return@filter false
                val skipTarget =
                    (instruction as? BuilderOffsetInstruction)?.target?.location?.index
                        ?: return@filter false
                skipTarget > resultIndex &&
                    (index + 1 until callIndex).none { candidate ->
                        instructions[candidate].opcode in BRANCH_OPCODES
                    }
            },
        ) ?: return InsetApplicationKind.UNCONDITIONAL
    val skipTarget = (instructions[gate] as BuilderOffsetInstruction).target.location.index
    val mergeIndex =
        ((resultIndex + 1)..(resultIndex + INSET_MERGE_LOOKBACK)).firstNotNullOfOrNull { index ->
            val instruction = instructions.getOrNull(index) ?: return@firstNotNullOfOrNull null
            if (instruction.opcode !in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)) {
                return@firstNotNullOfOrNull null
            }
            (instruction as? BuilderOffsetInstruction)?.target?.location?.index
        }
            ?: throw PatchException(
                "NewX gated navigation-insets application has no merge goto after $callIndex in $this",
            )
    if (skipTarget >= mergeIndex) {
        throw PatchException(
            "NewX gated navigation-insets application has an empty skip arm in $this",
        )
    }
    val skipArmWrites =
        (skipTarget until mergeIndex).filter { index ->
            instructions[index].writesTo(destinationRegister)
        }
    if (skipArmWrites.isNotEmpty()) {
        throw PatchException(
            "NewX gated navigation-insets application skip arm writes v$destinationRegister at " +
                "$skipArmWrites in $this",
        )
    }
    return InsetApplicationKind.GATED
}

private fun Instruction.writesTo(register: Int): Boolean {
    val destination = this as? OneRegisterInstruction ?: return false
    return destination.registerA == register && opcode !in READ_ONLY_REGISTER_A_OPCODES
}

private fun Method.isPostDetailReplyBarRenderer(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
        parameters.count { it == "Ljava/lang/String;" } == 1 &&
        parameters.any { it.startsWith(HAZE_SCOPE) }
}

private fun Method.isPhotoViewerControlsRenderer(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    val hasKnownParameterShape =
        when (parameters.size) {
            10 ->
                parameters[0].startsWith(INLINE_ACTION_BAR_SCOPE) &&
                    parameters[1] == "Z" &&
                    parameters[2].startsWith(COMPOSER_MINIMAL_SCOPE) &&
                    parameters[3].startsWith(HAZE_SCOPE) &&
                    parameters[4] == MODIFIER_DESCRIPTOR &&
                    parameters[5] == "Ljava/lang/String;" &&
                    parameters[6] == "Ljava/lang/String;" &&
                    parameters[7] == FUNCTION1_DESCRIPTOR &&
                    parameters[8] == COMPOSER_DESCRIPTOR &&
                    parameters[9] == "I"

            11 ->
                parameters[0].startsWith(HAZE_SCOPE) &&
                    parameters[1].startsWith(INLINE_ACTION_BAR_SCOPE) &&
                    parameters[2].startsWith(COMPOSER_MINIMAL_SCOPE) &&
                    parameters[3] == FUNCTION1_DESCRIPTOR &&
                    parameters[4] == "Z" &&
                    parameters[5] == MODIFIER_DESCRIPTOR &&
                    parameters[6] == FUNCTION1_DESCRIPTOR &&
                    parameters[7] == "Ljava/lang/String;" &&
                    parameters[8] == "Ljava/lang/String;" &&
                    parameters[9] == COMPOSER_DESCRIPTOR &&
                    parameters[10] == "I"

            else -> false
        }
    return AccessFlags.STATIC.isSet(accessFlags) &&
        returnType == "V" &&
        hasKnownParameterShape &&
        inlineActionBarRenderCallIndices().size == 1 &&
        navigationInsetsCallIndices().size == 1
}

private fun Method.inlineActionBarRenderCallIndices(): List<Int> =
    implementation?.instructions?.mapIndexedNotNull { index, instruction ->
        if (!instruction.isStaticInvocation()) return@mapIndexedNotNull null

        val reference = instruction.getReference<MethodReference>()
            ?: return@mapIndexedNotNull null
        val parameters = reference.parameterTypes.map(CharSequence::toString)
        val isInlineActionBarRender =
            reference.returnType == "V" &&
                parameters.firstOrNull()?.startsWith(INLINE_ACTION_BAR_SCOPE) == true &&
                parameters.getOrNull(1) == MODIFIER_DESCRIPTOR &&
                parameters.any { it.startsWith(FOUNDATION_LAYOUT_SCOPE) } &&
                parameters.any { it.startsWith(HAZE_SCOPE) } &&
                parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
                parameters.count { it == "I" } >= 3
        index.takeIf { isInlineActionBarRender }
    }.orEmpty()

/**
 * The Compose compiler and R8 rename the framework bridge methods used for window insets. The
 * surrounding data flow is stable: a framework state singleton is touched, a Composer getter
 * returns the state, one of its inset fields is read, and a Modifier transformation consumes it.
 */
private fun Method.hasNavigationInsetsCall(): Boolean =
    navigationInsetsCallIndices().isNotEmpty()

private fun Method.navigationInsetsCallIndices(): List<Int> =
    implementation?.instructions?.toList()?.navigationInsetsCallIndices().orEmpty()

private fun List<Instruction>.navigationInsetsCallIndices(): List<Int> =
    indices.filter { index -> isNavigationInsetsCall(index) }

private fun List<Instruction>.isNavigationInsetsCall(index: Int): Boolean {
    val callInstruction = getOrNull(index) ?: return false
    if (!callInstruction.isStaticInvocation()) return false

    val call = callInstruction.getReference<MethodReference>() ?: return false
    val parameters = call.parameterTypes.map(CharSequence::toString)
    if (
        !call.definingClass.startsWith(FOUNDATION_LAYOUT_SCOPE) ||
            call.returnType != MODIFIER_DESCRIPTOR ||
            parameters.size != 2 ||
            parameters[0] != MODIFIER_DESCRIPTOR ||
            !parameters[1].startsWith(FOUNDATION_LAYOUT_SCOPE)
    ) {
        return false
    }

    val insetRead =
        requireAtMostOne(
            label = "NewX navigation-insets read before call $index",
            candidates =
                navigationInsetsReadsBefore(index).filter { read ->
                    callInstruction.registersUsed.contains(read.destinationRegister)
                },
        )
    return insetRead != null
}

private fun List<Instruction>.navigationInsetsReadsBefore(callIndex: Int): List<NavigationInsetsRead> =
    (0 until callIndex).flatMap { providerIndex ->
        val providerInstruction = getOrNull(providerIndex) ?: return@flatMap emptyList()
        if (!providerInstruction.isStaticInvocation()) return@flatMap emptyList()

        val provider = providerInstruction.getReference<MethodReference>()
            ?: return@flatMap emptyList()
        if (
            provider.returnType == MODIFIER_DESCRIPTOR ||
                !provider.returnType.startsWith(FOUNDATION_LAYOUT_SCOPE) ||
                provider.parameterTypes.map(CharSequence::toString) != listOf(COMPOSER_DESCRIPTOR)
        ) {
            return@flatMap emptyList()
        }

        val stateMarker = getOrNull(providerIndex - 1)
        if (stateMarker?.opcode != Opcode.SGET_OBJECT) return@flatMap emptyList()
        val stateField = stateMarker.getReference<FieldReference>()
            ?: return@flatMap emptyList()
        if (stateField.definingClass != provider.returnType) return@flatMap emptyList()

        val stateResult = getOrNull(providerIndex + 1)
        if (stateResult?.opcode != Opcode.MOVE_RESULT_OBJECT) return@flatMap emptyList()
        val stateRegister = (stateResult as? OneRegisterInstruction)?.registerA
            ?: return@flatMap emptyList()

        (providerIndex + 2 until callIndex).mapNotNull { readIndex ->
            val readInstruction = getOrNull(readIndex) ?: return@mapNotNull null
            if (readInstruction.opcode != Opcode.IGET_OBJECT) return@mapNotNull null

            val registers = readInstruction as? TwoRegisterInstruction
                ?: return@mapNotNull null
            if (registers.registerB != stateRegister) return@mapNotNull null

            val field = readInstruction.getReference<FieldReference>()
                ?: return@mapNotNull null
            if (
                field.definingClass != provider.returnType ||
                    !field.type.toString().startsWith(FOUNDATION_LAYOUT_SCOPE)
            ) {
                return@mapNotNull null
            }
            NavigationInsetsRead(registers.registerA)
        }
    }

private fun Instruction.isStaticInvocation(): Boolean =
    opcode == Opcode.INVOKE_STATIC || opcode == Opcode.INVOKE_STATIC_RANGE

private fun Method.isMinimalComposerRendererCaller(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return returnType == "V" &&
        parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
        parameters.count { it == MODIFIER_DESCRIPTOR } == 1 &&
        parameters.any { it.startsWith(HAZE_SCOPE) }
}

private fun Method.isMinimalComposerContainerCaller(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return AccessFlags.STATIC.isSet(accessFlags) &&
        returnType == "V" &&
        parameters.size == 8 &&
        parameters[0].startsWith(COMPOSER_MINIMAL_SCOPE) &&
        parameters[1].startsWith(HAZE_SCOPE) &&
        parameters[2] == MODIFIER_DESCRIPTOR &&
        parameters[3] == "Z" &&
        parameters[4] == FUNCTION1_DESCRIPTOR &&
        parameters[5] == COMPOSER_DESCRIPTOR &&
        parameters[6] == "I" &&
        parameters[7] == "I"
}

private fun Method.isPostDetailReplyBarContainer(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return returnType == "V" &&
        parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
        parameters.any { it.startsWith(HAZE_SCOPE) } &&
        parameters.any { it.startsWith(POST_DETAIL_SHEET_SCOPE) }
}

private fun Method.callSiteIndices(target: Method): List<Int> =
    implementation?.instructions?.mapIndexedNotNull { index, instruction ->
        if (!instruction.isStaticInvocation()) return@mapIndexedNotNull null
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        index.takeIf { reference.matches(target) }
    }.orEmpty()

private fun MethodReference.matches(target: Method): Boolean =
    definingClass == target.definingClass &&
        name == target.name &&
        returnType == target.returnType &&
        parameterTypes.map(CharSequence::toString) == target.parameterTypes.map(CharSequence::toString)

private fun MutableMethod.matches(target: Method): Boolean =
    definingClass == target.definingClass &&
        name == target.name &&
        returnType == target.returnType &&
        parameterTypes.map(CharSequence::toString) == target.parameterTypes.map(CharSequence::toString)

context(context: BytecodePatchContext)
private fun resolvePostDetailReplyBarRenderer(): Match {
    val shapeMatches = NewXPostDetailReplyBarFingerprint.scopedMatchAll()
    // Older releases carry the test tag directly in the renderer; newer ones host it
    // in a Function2 lambda one hop down (alpha.04). Prefer the direct match when present.
    val directMatches = shapeMatches.filter { match ->
        match.method.implementation?.instructions?.any { instruction ->
            instruction.getReference<StringReference>()?.string ==
                "post-detail-reply-text-field"
        } == true
    }
    if (directMatches.isNotEmpty()) {
        return requireExactlyOne(
            label = "NewX post-detail reply bar renderer",
            candidates = directMatches,
        )
    }
    // The reply text field test tag moved one hop down: the renderer no longer contains
    // it directly (alpha.04 hosts it in a Function2 lambda instantiated by a helper).
    // Resolve owner -> instantiator -> direct caller instead of hardcoding any of them.
    // One shared snapshot feeds both passes below instead of two full dex traversals.
    val classDefs = buildList {
        context.classDefForEach { add(it) }
    }
    val anchorOwners = classDefs.mapNotNull { classDef ->
        classDef.type.takeIf {
            classDef.methods.any { method ->
                method.implementation?.instructions?.any { instruction ->
                    instruction.getReference<StringReference>()?.string ==
                        "post-detail-reply-text-field"
                } == true
            }
        }
    }
    val anchorOwner =
        requireExactlyOne(
            label = "NewX reply text field lambda owner",
            candidates = anchorOwners,
        )
    val instantiators = buildList {
        classDefs.forEach { classDef ->
            classDef.methods.forEach { method ->
                if (method.implementation?.instructions?.any { instruction ->
                        instruction.opcode == Opcode.NEW_INSTANCE &&
                            instruction.getReference<TypeReference>()?.type == anchorOwner
                    } == true) {
                    add(method)
                }
            }
        }
    }
    val instantiator =
        requireExactlyOne(
            label = "NewX reply text field lambda instantiator",
            candidates = instantiators,
            describe = { "$it" },
        )
    return requireExactlyOne(
        label = "NewX post-detail reply bar renderer",
        candidates = shapeMatches.filter { match ->
            match.method.callSiteIndices(instantiator).isNotEmpty()
        },
    )
}

/**
 * The renderer is called through minimal-composer helpers before the post-detail sheet adds the
 * navigation-bar inset. Resolving that call chain keeps the container hooks independent of the
 * obfuscated minimal-composer and post-detail-sheet owners/method names.
 */
context(context: BytecodePatchContext)
private fun resolvePostDetailReplyBarContainers(renderer: Match): Pair<MutableMethod, MutableMethod> {
    val rendererCaller =
        findUniqueCaller(
            target = renderer.originalMethod,
            scope = COMPOSER_MINIMAL_SCOPE,
            label = "NewX minimal reply-bar renderer caller",
            predicate = Method::isMinimalComposerRendererCaller,
        )
    val minimalContainerCaller =
        findUniqueCaller(
            target = rendererCaller,
            scope = COMPOSER_MINIMAL_SCOPE,
            label = "NewX minimal reply-bar composition caller",
            predicate = Method::isMinimalComposerContainerCaller,
        )
    val postDetailContainer =
        findUniqueCaller(
            target = minimalContainerCaller,
            scope = POST_DETAIL_SHEET_SCOPE,
            label = "NewX post-detail reply-bar container",
            predicate = Method::isPostDetailReplyBarContainer,
        )

    val minimalMutableClass = context.mutableClassDefBy(minimalContainerCaller.definingClass)
    val minimalMutableMethod =
        requireExactlyOne(
            label = "NewX mutable minimal reply-bar composition caller",
            candidates = minimalMutableClass.methods.filter { method ->
                method.matches(minimalContainerCaller)
            },
        ) as? MutableMethod
            ?: throw PatchException(
                "NewX minimal reply-bar composition caller is not mutable: $minimalContainerCaller",
            )

    val postDetailMutableClass = context.mutableClassDefBy(postDetailContainer.definingClass)
    val postDetailMutableMethod =
        requireExactlyOne(
            label = "NewX mutable post-detail reply-bar container",
            candidates = postDetailMutableClass.methods.filter { method ->
                method.matches(postDetailContainer)
            },
        ) as? MutableMethod
            ?: throw PatchException(
                "NewX post-detail reply-bar container is not mutable: $postDetailContainer",
            )

    return minimalMutableMethod to postDetailMutableMethod
}

context(context: BytecodePatchContext)
private fun findUniqueCaller(
    target: Method,
    scope: String,
    label: String,
    predicate: (Method) -> Boolean,
): Method {
    val candidates = buildList {
        context.classDefForEach { classDef ->
            if (!classDef.type.startsWith(scope)) return@classDefForEach
            classDef.methods.forEach { method ->
                if (!predicate(method)) return@forEach
                val callSites = method.callSiteIndices(target)
                if (callSites.isNotEmpty()) add(method to callSites)
            }
        }
    }
    val (caller, callSites) = requireExactlyOne(label, candidates)
    requireExactlyOne("$label callsite to $target", callSites)
    return caller
}

private fun requireNavigationInsetsHook(
    method: MutableMethod,
    label: String,
): NavigationInsetsHook {
    val instructions = method.instructions.toList()
    val callIndices = instructions.navigationInsetsCallIndices()
    val callIndex =
        requireExactlyOne(
            label = "$label navigation-insets call in $method",
            candidates = callIndices,
        )
    if (instructions.getOrNull(callIndex + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException(
            "$label navigation-insets call is not followed by move-result-object in $method",
        )
    }
    val continuation =
        instructions.getOrNull(callIndex + 2)
            ?: throw PatchException(
                "$label navigation-insets call has no continuation in $method",
            )
    return NavigationInsetsHook(method, callIndex, continuation)
}

context(context: BytecodePatchContext)
private fun resolvePostDetailNavigationInsetsHook(
    postDetailContainer: MutableMethod,
): NavigationInsetsHook {
    val matches = NewXPostDetailNavigationInsetsFingerprint.scopedMatchAllOrNull().orEmpty()
    val match =
        requireExactlyOne(
            label = "NewX post-detail navigation inset renderer",
            candidates = matches,
        )
    if (!postDetailContainer.matches(match.originalMethod)) {
        throw PatchException(
            "NewX post-detail navigation inset renderer is not the reply-bar container: " +
                "${match.originalMethod} vs $postDetailContainer",
        )
    }
    return requireNavigationInsetsHook(postDetailContainer, "NewX post-detail")
}

private fun Method.isImmersiveMediaControlsRenderer(minimalContainer: Method): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return AccessFlags.STATIC.isSet(accessFlags) &&
        returnType == "V" &&
        parameters.any { it.startsWith(INLINE_ACTION_BAR_SCOPE) } &&
        parameters.any { it.startsWith(COMPOSER_MINIMAL_SCOPE) } &&
        parameters.any { it.startsWith(HAZE_SCOPE) } &&
        parameters.any { it.startsWith(IMMERSIVE_CHROME_SCOPE) } &&
        parameters.count { it == MODIFIER_DESCRIPTOR } == 1 &&
        parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
        inlineActionBarRenderCallIndices().size == 1 &&
        callSiteIndices(minimalContainer).size == 1 &&
        navigationInsetsCallIndices().size == 1
}

private fun Instruction.argumentRegister(
    reference: MethodReference,
    parameterIndex: Int,
): Int? {
    val registerOffset =
        reference.parameterTypes
            .take(parameterIndex)
            .sumOf { parameter -> if (parameter.toString() in setOf("J", "D")) 2 else 1 }
    return registersUsed.getOrNull(registerOffset)
}

/**
 * Resolves the 12.29 immersive-media action row and reuses the exact navigation-bar inset object
 * from its native no-composer spacer. Applying that inset to the row modifier moves the controls;
 * forcing the separate spacer branch was ineffective here and added blank space to other layouts.
 */
context(context: BytecodePatchContext)
private fun resolveImmersiveActionBarSafeAreaHook(
    minimalContainer: MutableMethod,
): ImmersiveActionBarSafeAreaHook? {
    val originalMethod =
        requireAtMostOne(
            label = "NewX immersive-media action-bar renderer",
            candidates = buildList<Method> {
                context.classDefForEach { classDef ->
                    if (!classDef.type.startsWith(MEDIA_SCOPE)) return@classDefForEach
                    classDef.methods.forEach { method ->
                        if (method.isImmersiveMediaControlsRenderer(minimalContainer)) add(method)
                    }
                }
            },
        ) ?: return null
    val method =
        requireExactlyOne(
            label = "NewX mutable immersive-media action-bar renderer",
            candidates = context.mutableClassDefBy(originalMethod.definingClass).methods.filter { candidate ->
                candidate.matches(originalMethod)
            },
        ) as? MutableMethod
            ?: throw PatchException(
                "NewX immersive-media action-bar renderer is not mutable: $originalMethod",
            )
    val instructions = method.instructions.toList()
    val actionBarCallIndex =
        requireExactlyOne(
            label = "NewX immersive-media inline action-bar call in $method",
            candidates = method.inlineActionBarRenderCallIndices(),
        )
    val minimalComposerCallIndex =
        requireExactlyOne(
            label = "NewX immersive-media reply-composer call in $method",
            candidates = method.callSiteIndices(minimalContainer),
        )
    val navigationInsetsCallIndex =
        requireExactlyOne(
            label = "NewX immersive-media navigation-insets call in $method",
            candidates = method.navigationInsetsCallIndices(),
        )
    if (!(actionBarCallIndex < minimalComposerCallIndex && minimalComposerCallIndex < navigationInsetsCallIndex)) {
        throw PatchException(
            "Unexpected immersive-media control order in $method: action bar at $actionBarCallIndex, " +
                "reply composer at $minimalComposerCallIndex, navigation inset at $navigationInsetsCallIndex",
        )
    }

    val actionBarCall = instructions[actionBarCallIndex]
    val actionBarReference = actionBarCall.getReference<MethodReference>()
        ?: throw PatchException("NewX immersive-media action-bar call has no reference in $method")
    val modifierParameterIndex =
        requireExactlyOne(
            label = "NewX immersive-media action-bar Modifier parameter",
            candidates = actionBarReference.parameterTypes.indices.filter { index ->
                actionBarReference.parameterTypes[index].toString() == MODIFIER_DESCRIPTOR
            },
        )
    val composerParameterIndex =
        requireExactlyOne(
            label = "NewX immersive-media action-bar Composer parameter",
            candidates = actionBarReference.parameterTypes.indices.filter { index ->
                actionBarReference.parameterTypes[index].toString() == COMPOSER_DESCRIPTOR
            },
        )
    val modifierRegister = actionBarCall.argumentRegister(actionBarReference, modifierParameterIndex)
        ?: throw PatchException("NewX immersive-media action-bar Modifier register is unavailable in $method")
    val composerRegister = actionBarCall.argumentRegister(actionBarReference, composerParameterIndex)
        ?: throw PatchException("NewX immersive-media action-bar Composer register is unavailable in $method")

    val insetsPaddingCall = instructions[navigationInsetsCallIndex].getReference<MethodReference>()
        ?: throw PatchException("NewX immersive-media insets call has no reference in $method")
    val insetsRegister = instructions[navigationInsetsCallIndex].registersUsed.getOrNull(1)
        ?: throw PatchException("NewX immersive-media insets call has no inset register in $method")
    val navigationBarsReadIndex =
        requireExactlyOne(
            label = "NewX immersive-media navigation-bars field read in $method",
            candidates = (minimalComposerCallIndex + 1 until navigationInsetsCallIndex).filter { index ->
                val instruction = instructions[index]
                val registers = instruction as? TwoRegisterInstruction ?: return@filter false
                val field = instruction.getReference<FieldReference>() ?: return@filter false
                instruction.opcode == Opcode.IGET_OBJECT &&
                    registers.registerA == insetsRegister &&
                    field.type.toString().startsWith(FOUNDATION_LAYOUT_SCOPE)
            },
        )
    val navigationBarsRead = instructions[navigationBarsReadIndex] as TwoRegisterInstruction
    val navigationBarsField = instructions[navigationBarsReadIndex].getReference<FieldReference>()
        ?: throw PatchException("NewX immersive-media navigation-bars read has no field in $method")
    val windowInsetsHolderProvider =
        requireExactlyOne(
            label = "NewX immersive-media WindowInsets holder provider in $method",
            candidates = (minimalComposerCallIndex + 1 until navigationBarsReadIndex).mapNotNull { index ->
                val instruction = instructions[index]
                if (!instruction.isStaticInvocation()) return@mapNotNull null
                val reference = instruction.getReference<MethodReference>() ?: return@mapNotNull null
                val result = instructions.getOrNull(index + 1) as? OneRegisterInstruction
                    ?: return@mapNotNull null
                reference.takeIf {
                    reference.returnType == navigationBarsField.definingClass &&
                        reference.parameterTypes.map(CharSequence::toString) == listOf(COMPOSER_DESCRIPTOR) &&
                        result.opcode == Opcode.MOVE_RESULT_OBJECT &&
                        result.registerA == navigationBarsRead.registerB
                }
            },
        )

    return ImmersiveActionBarSafeAreaHook(
        method = method,
        actionBarCallIndex = actionBarCallIndex,
        actionBarCall = actionBarCall,
        modifierRegister = modifierRegister,
        composerRegister = composerRegister,
        windowInsetsHolderProvider = windowInsetsHolderProvider,
        navigationBarsField = navigationBarsField,
        insetsPaddingCall = insetsPaddingCall,
    )
}

internal fun applyImmersiveActionBarSafeAreaHook(
    setting: ToggleSettingDefinition,
    hook: ImmersiveActionBarSafeAreaHook,
) {
    val settingRead =
        setting.injectRead(
            method = hook.method,
            index = hook.actionBarCallIndex,
            excludedRegisters = listOf(hook.modifierRegister, hook.composerRegister),
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    hook.method.insertHook(
        index = settingRead.nextIndex,
        // The old plain insertion left incoming labels on the action-bar call, so a path that
        // branched straight to it still skips the padding block.
        relocateBranchTargets = false,
    ) {
        // The toggle register doubles as the insets scratch: `if-eqz` consumed it and the
        // setting-off path skips the block that overwrites it.
        ifEqz(settingRead.register, Target.Original)
        // The old smali hardcoded `invoke-static/range {vC .. vC}`; the typed API emits the 35c
        // form while the Composer register fits four bits and that same range form when it does not.
        invokeStatic(hook.windowInsetsHolderProvider, hook.composerRegister)
        moveResult(settingRead.register, hook.windowInsetsHolderProvider.returnType.toString())
        iget(settingRead.register, settingRead.register, hook.navigationBarsField)
        invokeStatic(hook.insetsPaddingCall, hook.modifierRegister, settingRead.register)
        moveResult(hook.modifierRegister, hook.insetsPaddingCall.returnType.toString())
    }
}

/**
 * The photo viewer chooses between the reply composer and a native navigation-bar spacer after
 * rendering its action bar. Resolve the existing no-composer branch from call order and control
 * flow so the hide setting can select it without recreating Compose inset behavior.
 */
context(context: BytecodePatchContext)
private fun resolvePhotoViewerNavigationFallbackHook(
    minimalContainer: MutableMethod,
): PhotoViewerNavigationFallbackHook {
    val originalMethod =
        findUniqueCaller(
            target = minimalContainer,
            scope = MEDIA_SCOPE,
            label = "NewX photo-viewer controls renderer",
            predicate = Method::isPhotoViewerControlsRenderer,
        )
    val method =
        requireExactlyOne(
            label = "NewX mutable photo-viewer controls renderer",
            candidates =
                context.mutableClassDefBy(originalMethod.definingClass).methods.filter { method ->
                    method.matches(originalMethod)
                },
        ) as? MutableMethod
            ?: throw PatchException(
                "NewX photo-viewer controls renderer is not mutable: $originalMethod",
            )
    val instructions = method.instructions.toList()
    val actionBarCalls = method.inlineActionBarRenderCallIndices()
    val minimalComposerCalls = method.callSiteIndices(minimalContainer)
    val navigationInsetCalls = method.navigationInsetsCallIndices()
    val actionBarCall =
        requireExactlyOne(
            label = "NewX photo-viewer action-bar call in $method",
            candidates = actionBarCalls,
        )
    val minimalComposerCall =
        requireExactlyOne(
            label = "NewX photo-viewer reply-composer call in $method",
            candidates = minimalComposerCalls,
        )
    val navigationInsetCall =
        requireExactlyOne(
            label = "NewX photo-viewer navigation-inset call in $method",
            candidates = navigationInsetCalls,
        )
    if (!(actionBarCall < minimalComposerCall && minimalComposerCall < navigationInsetCall)) {
        throw PatchException(
            "Unexpected photo-viewer control-flow order in $method: action bar at $actionBarCall, " +
                "reply composer at $minimalComposerCall, navigation inset at $navigationInsetCall",
        )
    }

    val fallbackBranches =
        (actionBarCall + 1 until minimalComposerCall).mapNotNull { index ->
            val instruction = instructions[index]
            if (instruction.opcode !in CONDITIONAL_BRANCH_OPCODES) return@mapNotNull null
            val branch = instruction as? BuilderOffsetInstruction
                ?: throw PatchException(
                    "Photo-viewer conditional branch is not mutable at $index in $method: " +
                        instruction,
                )
            val target = branch.target.location.instruction ?: return@mapNotNull null
            val targetIndex = branch.target.location.index
            Triple(index, targetIndex, target).takeIf {
                targetIndex in (minimalComposerCall + 1)..navigationInsetCall
            }
        }
    val fallbackTargets = fallbackBranches.distinctBy { (_, targetIndex, _) -> targetIndex }
    if (fallbackBranches.isEmpty()) {
        throw PatchException(
            "Expected at least one photo-viewer reply gate in $method, found none",
        )
    }
    val fallbackTarget =
        requireExactlyOne(
            label = "NewX photo-viewer navigation fallback target in $method",
            candidates = fallbackTargets,
        )

    return PhotoViewerNavigationFallbackHook(
        method = method,
        // newx-resolver-lint: allow instruction-order raw-first because bytecode order is the contract.
        gateIndex = fallbackBranches.minOf { (index, _, _) -> index },
        fallback = fallbackTarget.third,
    )
}

@Suppress("unused")
val newXHidePostReplyBarPatch =
    bytecodePatch(
        name = "NewX: Hide post reply bar",
        description = "Hides the persistent post-detail reply bar while keeping the compose button available.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val hidePostReplyBar =
            newXToggle(
                id = "newx.post_actions_media.hide_post_reply_bar",
                category = Categories.POST_ACTIONS_MEDIA,
                strings = settingStrings("piko_newx_hide_post_reply_bar"),
                order = 100,
                defaultValue = false,
            )

        execute {
            val renderer =
                resolvePostDetailReplyBarRenderer()
            val (minimalContainer, postDetailSheetContainer) = resolvePostDetailReplyBarContainers(renderer)
            val postDetailNavigationInsetsHook =
                resolvePostDetailNavigationInsetsHook(postDetailSheetContainer)
            val photoViewerNavigationFallbackHook =
                resolvePhotoViewerNavigationFallbackHook(minimalContainer)
            val immersiveActionBarSafeAreaHook =
                resolveImmersiveActionBarSafeAreaHook(minimalContainer)

            val postDetailInsetApplication =
                postDetailSheetContainer.classifyInsetApplication(postDetailNavigationInsetsHook.callIndex)

            // MainActivity's navigation-bar padding is the screen-wide safe area, not reply-bar
            // spacing. Keep it so the remaining post actions stay above the gesture pill.
            hidePostReplyBar.returnVoidIfEnabled(renderer.method, 0)
            hidePostReplyBar.returnVoidIfEnabled(minimalContainer, 0)
            // A gated inset is the app's own "this composition is bottom-anchored" signal (the
            // fullscreen photo screen). Keep it applied: it reserves the gesture area once the
            // composer is hidden, and `minimalContainer` already removes the reply bar, its
            // gradient scrim, and the blur box. Only unconditional insets (legacy containers) are
            // removed together with the reply bar as before.
            if (postDetailInsetApplication == InsetApplicationKind.UNCONDITIONAL) {
                hidePostReplyBar.branchIfEnabled(
                    postDetailNavigationInsetsHook.method,
                    postDetailNavigationInsetsHook.callIndex,
                    postDetailNavigationInsetsHook.continuation,
                )
                hidePostReplyBar.returnVoidIfEnabled(postDetailSheetContainer, 0)
            }
            photoViewerNavigationFallbackHook.let { hook ->
                hidePostReplyBar.branchIfEnabled(hook.method, hook.gateIndex, hook.fallback)
            }
            immersiveActionBarSafeAreaHook?.let { hook ->
                // 12.29's new immersive renderer owns the visible likes/repost/share row. Pad that
                // row itself; do not force its separate no-composer spacer, which affects shared
                // media layouts and previously introduced a blank timeline gap.
                applyImmersiveActionBarSafeAreaHook(hidePostReplyBar, hook)
            }
        }
    }
