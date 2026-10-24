package app.crimera.patches.newx.misc.postdetails

import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.settings.InjectedSettingRead
import app.crimera.patches.settings.SettingReadRegisterConstraint
import app.crimera.patches.settings.ToggleSettingDefinition
import app.crimera.patches.settings.branchIfEnabled
import app.crimera.patches.settings.injectRead
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.settings.returnVoidIfEnabled
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.INTEGER_LITERAL_OPCODES
import app.crimera.patches.common.INTEGER_MOVE_OPCODES
import app.crimera.patches.common.OBJECT_MOVE_OPCODES
import app.crimera.patches.common.scopedMatchAll
import app.crimera.patches.common.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.newInstance
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
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
    // The caller edge to the resolved container supplies the domain anchor. Auxiliary Compose
    // parameters are not consumed by this hook; their count and order cannot identify the UI.
    // The resolver below additionally proves call order and a common native fallback target.
    return AccessFlags.STATIC.isSet(accessFlags) &&
        returnType == "V" &&
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
                    !field.type.startsWith(FOUNDATION_LAYOUT_SCOPE)
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
    val anchorOwner =
        requireExactlyOne(
            label = "NewX reply text field lambda owner",
            candidates = context.classDefByStrings("post-detail-reply-text-field").map { it.type },
        )
    // `newInstance` still means exactly the old scan: `new-array` stores the array descriptor,
    // so a class descriptor cannot match it.
    val instantiator =
        requireExactlyOne(
            label = "NewX reply text field lambda instantiator",
            candidates =
                Fingerprint(filters = listOf(newInstance(anchorOwner)))
                    .matchAllOrNull()
                    .orEmpty()
                    .map(Match::originalMethod),
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
        )

    val postDetailMutableClass = context.mutableClassDefBy(postDetailContainer.definingClass)
    val postDetailMutableMethod =
        requireExactlyOne(
            label = "NewX mutable post-detail reply-bar container",
            candidates = postDetailMutableClass.methods.filter { method ->
                method.matches(postDetailContainer)
            },
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

private fun Method.isImmersiveMediaControlsShape(): Boolean {
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
        navigationInsetsCallIndices().size == 1
}

/** Reaching definitions include every CFG predecessor, not the preceding textual assignment. */
private class ReplyControlFlow(val method: Method) {
    private val implementation = method.implementation
        ?: throw PatchException("NewX reply content has no code: $method")
    val instructions = implementation.instructions.toList()
    private val offsets = instructions.runningFold(0) { offset, instruction -> offset + instruction.codeUnits }
    private val indicesByOffset = offsets.withIndex().associate { it.value to it.index }
    val parameterStart = implementation.registerCount - method.parameterTypes.sumOf {
        if (it.toString() in setOf("J", "D")) 2 else 1
    } - if (AccessFlags.STATIC.isSet(method.accessFlags)) 0 else 1

    init {
        if (implementation.tryBlocks.isNotEmpty()) {
            throw PatchException("NewX reply content proof does not support exception edges: $method")
        }
    }

    private fun indexAt(offset: Int): Int = indicesByOffset[offset]?.takeIf { it in instructions.indices }
        ?: throw PatchException("Invalid NewX reply content branch at $offset in $method")

    private fun target(index: Int): Int =
        indexAt(offsets[index] + (instructions[index] as OffsetInstruction).codeOffset)

    private fun switchCases(index: Int) =
        (instructions[target(index)] as? SwitchPayload)?.switchElements
            ?: throw PatchException("Missing NewX reply content switch payload in $method at $index")

    private fun successors(index: Int): List<Int> {
        val instruction = instructions[index]
        if (instruction.opcode in setOf(Opcode.RETURN_VOID, Opcode.RETURN, Opcode.RETURN_WIDE, Opcode.RETURN_OBJECT, Opcode.THROW) ||
            instruction is SwitchPayload
        ) {
            return emptyList()
        }
        if (instruction.opcode in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)) return listOf(target(index))
        if (instruction.opcode in setOf(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH)) {
            return switchCases(index).map { indexAt(offsets[index] + it.offset) } + (index + 1)
        }
        if (instruction.opcode in CONDITIONAL_BRANCH_OPCODES) return listOf(target(index), index + 1)
        return listOf(index + 1).filter { it in instructions.indices }
    }

    private val predecessors by lazy {
        Array(instructions.size) { mutableListOf<Int>() }.also { predecessors ->
            instructions.indices.forEach { index -> successors(index).forEach { predecessors[it].add(index) } }
        }
    }

    fun reachable(
        skip: Int? = null,
        constant: ((Int, Int) -> Int?)? = null,
        requiredDispatch: Int? = null,
    ): Set<Int> {
        val pending = mutableListOf(0)
        return buildSet {
            while (pending.isNotEmpty()) {
                val index = pending.removeAt(pending.lastIndex)
                if (index == skip || !add(index)) continue
                val instruction = instructions[index]
                if (constant != null && instruction.opcode in setOf(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH)) {
                    val value = constant(index, (instruction as OneRegisterInstruction).registerA)
                        ?: throw PatchException("Unknown NewX reply content switch selector in $method at $index")
                    val selected = requireAtMostOne(
                        "NewX reply content dispatch $method selector=$value",
                        switchCases(index).filter { it.key == value },
                    )
                    pending.add(selected?.let { indexAt(offsets[index] + it.offset) } ?: (index + 1))
                    continue
                }
                if (constant != null && instruction.opcode in CONDITIONAL_BRANCH_OPCODES) {
                    val left = constant(index, (instruction as OneRegisterInstruction).registerA)
                    val right = if (instruction is TwoRegisterInstruction) constant(index, instruction.registerB) else 0
                    if (index == requiredDispatch && (left == null || right == null)) {
                        throw PatchException("Unknown NewX reply content initial discriminator in $method at $index")
                    }
                    if (left != null && right != null) {
                        val taken = when (instruction.opcode) {
                            Opcode.IF_EQ, Opcode.IF_EQZ -> left == right
                            Opcode.IF_NE, Opcode.IF_NEZ -> left != right
                            Opcode.IF_LT, Opcode.IF_LTZ -> left < right
                            Opcode.IF_GE, Opcode.IF_GEZ -> left >= right
                            Opcode.IF_GT, Opcode.IF_GTZ -> left > right
                            Opcode.IF_LE, Opcode.IF_LEZ -> left <= right
                            else -> throw PatchException("Unsupported NewX reply content branch in $method at $index")
                        }
                        pending.add(if (taken) target(index) else index + 1)
                        continue
                    }
                }
                pending.addAll(successors(index))
            }
        }
    }

    private val reachableIndices by lazy { reachable() }

    // Negative origins encode entry registers; nonnegative origins identify instructions.
    fun origin(before: Int, register: Int): Int? {
        if (before !in reachableIndices) return null
        val pending = mutableListOf(before to register)
        val visited = mutableSetOf<Pair<Int, Int>>()
        val origins = mutableSetOf<Int>()
        while (pending.isNotEmpty()) {
            val edge = pending.removeAt(pending.lastIndex)
            if (!visited.add(edge)) continue
            val (index, tracked) = edge
            if (index == 0) origins.add(-tracked - 1)
            for (previous in predecessors[index]) {
                if (previous !in reachableIndices) continue
                val instruction = instructions[previous]
                val destination = (instruction as? OneRegisterInstruction)?.registerA
                if (instruction.opcode.setsWideRegister() && (destination == tracked || destination == tracked - 1)) return null
                if (!instruction.opcode.setsRegister() || destination != tracked || instruction.opcode == Opcode.CHECK_CAST) {
                    pending.add(previous to tracked)
                } else if (instruction.opcode in INTEGER_MOVE_OPCODES || instruction.opcode in OBJECT_MOVE_OPCODES) {
                    pending.add(previous to (instruction as TwoRegisterInstruction).registerB)
                } else {
                    origins.add(previous)
                }
            }
        }
        // Multiple reaching definitions mean an unknown value, not an invalid unrelated call.
        // The proof consumer must reject unknown selectors; allocation searches may ignore them.
        return origins.singleOrNull()
    }

    fun literal(before: Int, register: Int): Int? = origin(before, register)?.takeIf { it >= 0 }?.let { index ->
        (instructions[index] as? NarrowLiteralInstruction)?.narrowLiteral
            ?.takeIf { instructions[index].opcode in INTEGER_LITERAL_OPCODES }
    }
}

/** Only the constructed instance's selected arm may anchor an animated composer. */
private fun ReplyControlFlow.allocatedContentCallsComposer(
    allocationIndex: Int,
    minimalContainer: Method,
    classByType: (String) -> ClassDef?,
): Boolean {
    val type = instructions[allocationIndex].getReference<TypeReference>()?.type ?: return false
    val definition = classByType(type) ?: return false
    if (definition.methods.none { it.callSiteIndices(minimalContainer).isNotEmpty() }) return false
    val entry = requireExactlyOne(
        "NewX reply content erased invoke in $type",
        definition.methods.filter { method ->
            method.name == "invoke" && method.returnType == "Ljava/lang/Object;" &&
                method.parameterTypes.all { it.toString() == "Ljava/lang/Object;" } &&
                !AccessFlags.STATIC.isSet(method.accessFlags)
        },
    )
    if (entry.callSiteIndices(minimalContainer).isEmpty()) {
        throw PatchException("NewX reply content $type delegates its composer through an unproven helper: $entry")
    }
    val constructorIndex = requireExactlyOne(
        "NewX reply content constructor for allocation $allocationIndex in $method",
        instructions.indices.filter { index ->
            val instruction = instructions[index]
            val reference = instruction.getReference<MethodReference>() ?: return@filter false
            instruction.opcode in setOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE) &&
                reference.definingClass == type && reference.name == "<init>" &&
                instruction.registersUsed.firstOrNull()?.let { origin(index, it) == allocationIndex } == true
        },
    )
    val constructorReference = instructions[constructorIndex].getReference<MethodReference>()!!
    val constructor = requireExactlyOne(
        "NewX reply content constructor $constructorReference",
        definition.methods.filter { constructorReference.matches(it) },
    )
    val constructorFlow = ReplyControlFlow(constructor)
    val entryFlow = ReplyControlFlow(entry)
    val discriminatorTypes = setOf("B", "S", "C", "I", "Z")
    val hasScalarCapture = definition.fields.any { it.type in discriminatorTypes && !AccessFlags.STATIC.isSet(it.accessFlags) }
    // A merged callback must dispatch on its capture before entering a case. Unknown expressions
    // at that boundary cannot be treated as permission to explore every unrelated R8 case.
    val dispatchIndex = entryFlow.instructions.indexOfFirst { it.opcode in BRANCH_OPCODES }
    var dispatchProven = !hasScalarCapture
    val selected = entryFlow.reachable(requiredDispatch = dispatchIndex.takeIf { hasScalarCapture }, constant = { branchIndex, register ->
        entryFlow.literal(branchIndex, register) ?: run {
            val readIndex = entryFlow.origin(branchIndex, register)?.takeIf { it >= 0 } ?: return@run null
            val read = entryFlow.instructions[readIndex]
            val field = read.getReference<FieldReference>() ?: return@run null
            if (read.opcode !in setOf(Opcode.IGET, Opcode.IGET_BYTE, Opcode.IGET_SHORT, Opcode.IGET_CHAR, Opcode.IGET_BOOLEAN) ||
                field.definingClass != type ||
                entryFlow.origin(readIndex, (read as TwoRegisterInstruction).registerB) != -entryFlow.parameterStart - 1
            ) return@run null
            if (definition.fields.none { it.toString() == field.toString() && AccessFlags.FINAL.isSet(it.accessFlags) }) {
                throw PatchException("Unproven NewX reply content discriminator $field in $entry")
            }
            val storeIndex = requireExactlyOne(
                "NewX reply content discriminator initialization $field in $constructor",
                constructorFlow.instructions.indices.filter { index ->
                    val instruction = constructorFlow.instructions[index]
                    instruction.opcode in setOf(Opcode.IPUT, Opcode.IPUT_BYTE, Opcode.IPUT_SHORT, Opcode.IPUT_CHAR, Opcode.IPUT_BOOLEAN) &&
                        instruction.getReference<FieldReference>()?.toString() == field.toString()
                },
            )
            val store = constructorFlow.instructions[storeIndex] as TwoRegisterInstruction
            if (constructorFlow.origin(storeIndex, store.registerB) != -constructorFlow.parameterStart - 1 ||
                constructorFlow.reachable(skip = storeIndex).any {
                    constructorFlow.instructions[it].opcode == Opcode.RETURN_VOID
                }
            ) throw PatchException("NewX reply discriminator initialization does not dominate construction: $constructor")
            val source = constructorFlow.origin(storeIndex, store.registerA)
                ?: throw PatchException("Unknown NewX reply discriminator source in $constructor")
            val value = if (source >= 0) {
                constructorFlow.literal(storeIndex, store.registerA)
            } else {
                val word = -source - 1 - constructorFlow.parameterStart
                if (word <= 0) null else instructions[constructorIndex].registersUsed.getOrNull(word)?.let {
                    literal(constructorIndex, it)
                }
            } ?: throw PatchException("Unknown NewX reply discriminator argument for $field at $constructorIndex in $method")
            if (branchIndex == dispatchIndex) dispatchProven = true
            when (field.type) {
                "B" -> value.toByte().toInt()
                "S" -> value.toShort().toInt()
                "C" -> value.toChar().code
                "I" -> value
                "Z" -> value.takeIf { it == 0 || it == 1 }
                    ?: throw PatchException("Invalid NewX reply boolean discriminator $value for $field")
                else -> throw PatchException("Unsupported NewX reply discriminator representation: $field")
            }
        }
    })
    if (!dispatchProven) throw PatchException("NewX reply content first dispatch is not a proven scalar capture: $entry")
    val calls = entry.callSiteIndices(minimalContainer).filter { it in selected }
    if (calls.isEmpty()) return false
    requireExactlyOne("NewX selected reply content composer call in $entry", calls)
    if (selected.none { index ->
            val instruction = entryFlow.instructions[index]
            instruction.opcode == Opcode.CHECK_CAST && instruction.getReference<TypeReference>()?.type ==
                "Landroidx/compose/animation/AnimatedVisibilityScope;"
        }
    ) throw PatchException("NewX reply content is not an AnimatedVisibility scope: $entry")

    val wrapperIndex = requireExactlyOne(
        "NewX reply content Compose wrapper in $method",
        instructions.indices.filter { index ->
            val instruction = instructions[index]
            val reference = instruction.getReference<MethodReference>() ?: return@filter false
            if (!instruction.isStaticInvocation() || !reference.definingClass.startsWith("Landroidx/compose/runtime/internal/") ||
                !reference.returnType.startsWith("Landroidx/compose/runtime/internal/") ||
                COMPOSER_DESCRIPTOR !in reference.parameterTypes.map(CharSequence::toString)
            ) return@filter false
            reference.parameterTypes.indices.any { parameter ->
                reference.parameterTypes[parameter].toString() == "Lkotlin/Function;" &&
                    instruction.argumentRegister(reference, parameter)?.let { origin(index, it) == allocationIndex } == true
            }
        },
    )
    if (instructions.getOrNull(wrapperIndex + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException("NewX reply content wrapper has no result in $method at $wrapperIndex")
    }
    requireExactlyOne(
        "NewX animated reply content consumer in $method",
        instructions.indices.filter { index ->
            val instruction = instructions[index]
            val reference = instruction.getReference<MethodReference>() ?: return@filter false
            if (!instruction.isStaticInvocation() || !reference.definingClass.startsWith("Landroidx/compose/animation/") ||
                reference.returnType != "V" || COMPOSER_DESCRIPTOR !in reference.parameterTypes.map(CharSequence::toString)
            ) return@filter false
            reference.parameterTypes.indices.any { parameter ->
                reference.parameterTypes[parameter].toString() == instructions[wrapperIndex].getReference<MethodReference>()!!.returnType &&
                    instruction.argumentRegister(reference, parameter)?.let { origin(index, it) == wrapperIndex + 1 } == true
            }
        },
    )
    return true
}

/** Direct container invocation or a proven selected AnimatedVisibility content invocation. */
internal fun Method.replyComposerAnchorIndices(
    minimalContainer: Method,
    classByType: (String) -> ClassDef?,
): List<Int> {
    val flow by lazy { ReplyControlFlow(this) }
    return implementation?.instructions?.mapIndexedNotNull { index, instruction ->
        when {
            instruction.isStaticInvocation() ->
                index.takeIf {
                    instruction.getReference<MethodReference>()?.matches(minimalContainer) == true
                }
            instruction.opcode == Opcode.NEW_INSTANCE ->
                index.takeIf { flow.allocatedContentCallsComposer(index, minimalContainer, classByType) }
            else -> null
        }
    }.orEmpty()
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
                        if (method.isImmersiveMediaControlsShape()) add(method)
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
        )
    val instructions = method.instructions.toList()
    val actionBarCallIndex =
        requireExactlyOne(
            label = "NewX immersive-media inline action-bar call in $method",
            candidates = method.inlineActionBarRenderCallIndices(),
        )
    val minimalComposerCallIndex =
        requireExactlyOne(
            label = "NewX immersive-media reply-composer anchor in $method",
            candidates = method.replyComposerAnchorIndices(minimalContainer) { context.classDefByOrNull(it) },
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
                    field.type.startsWith(FOUNDATION_LAYOUT_SCOPE)
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
    val settingRead = setting.injectReadBeforeActionBar(hook)
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
        moveResult(settingRead.register, hook.windowInsetsHolderProvider.returnType)
        iget(settingRead.register, settingRead.register, hook.navigationBarsField)
        invokeStatic(hook.insetsPaddingCall, hook.modifierRegister, settingRead.register)
        moveResult(hook.modifierRegister, hook.insetsPaddingCall.returnType)
    }
}

/**
 * The setting read and the padding block need a four-bit scratch register that is free at their
 * injection point. The action-bar call passes its whole argument block as a range, so every low
 * register is live there. Move the injection up to the latest earlier index where one is free.
 *
 * Between that index and the call, the modifier and composer must keep the values the call
 * consumes, and no label may sit in the window: a path that skipped the block would also skip the
 * padding. The search only retries [injectRead]; a failed attempt throws before it edits the method.
 */
private fun ToggleSettingDefinition.injectReadBeforeActionBar(hook: ImmersiveActionBarSafeAreaHook): InjectedSettingRead {
    val instructions = hook.method.instructions.toList()
    val guardedRegisters = listOf(hook.modifierRegister, hook.composerRegister)
    val lastGuardedUse =
        (0 until hook.actionBarCallIndex).lastOrNull { index ->
            instructions[index].registersUsed.any { register -> register in guardedRegisters }
        } ?: -1
    val lastLabelledIndex =
        (0..hook.actionBarCallIndex).lastOrNull { index ->
            instructions[index].location.labels.isNotEmpty()
        } ?: -1
    val earliestIndex = maxOf(lastGuardedUse, lastLabelledIndex) + 1
    var failure: PatchException? = null
    for (index in hook.actionBarCallIndex downTo earliestIndex) {
        try {
            return injectRead(
                method = hook.method,
                index = index,
                excludedRegisters = guardedRegisters,
                registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
            )
        } catch (exception: PatchException) {
            failure = exception
        }
    }
    throw PatchException(
        "NewX immersive-media setting read has no four-bit scratch register between index " +
            "$earliestIndex and ${hook.actionBarCallIndex} in ${hook.method}",
        failure,
    )
}

/**
 * The photo viewer chooses between the reply composer and a native navigation-bar spacer after
 * rendering its action bar. Resolve the existing no-composer branch from call order and control
 * flow so the hide setting can select it without recreating Compose inset behavior.
 */
context(context: BytecodePatchContext)
private fun resolvePhotoViewerNavigationFallbackHook(
    minimalContainer: MutableMethod,
    immersiveHook: ImmersiveActionBarSafeAreaHook?,
): PhotoViewerNavigationFallbackHook? {
    val directCallers = buildList<Method> {
        context.classDefForEach { classDef ->
            if (!classDef.type.startsWith(MEDIA_SCOPE)) return@classDefForEach
            classDef.methods.forEach { method ->
                if (method.callSiteIndices(minimalContainer).isNotEmpty()) add(method)
            }
        }
    }
    val candidates = directCallers.filter { it.isPhotoViewerControlsRenderer() }
    // 12.37 renders PHOTO/VIDEO/GIF through the shared immersive chrome; the legacy photo host with
    // its native-spacer fallback is gone. The photo hook is omitted only when no media method calls
    // the container directly, the immersive safe-area hook resolved, and that immersive renderer is
    // the sole media-scope inline-action row render, so every media action row stays covered.
    // Anything else falls through to the fail-closed diagnostic below.
    if (candidates.isEmpty() && directCallers.isEmpty() && immersiveHook != null) {
        val mediaRowRenderers = buildList<Method> {
            context.classDefForEach { classDef ->
                if (!classDef.type.startsWith(MEDIA_SCOPE)) return@classDefForEach
                classDef.methods.forEach { method ->
                    if (method.inlineActionBarRenderCallIndices().isNotEmpty()) add(method)
                }
            }
        }
        val onlyRowRenderer = requireAtMostOne(
            label = "NewX media inline-action row renderer (immersive-only reply shape)",
            candidates = mediaRowRenderers,
        )
        if (onlyRowRenderer != null && immersiveHook.method.matches(onlyRowRenderer)) return null
    }
    val missingCapability = if (candidates.isEmpty()) {
        val containerCallers = mutableListOf<String>()
        val actionRowConsumers = mutableListOf<String>()
        context.classDefForEach { definition ->
            definition.methods.forEach { method ->
                val composerCalls = method.callSiteIndices(minimalContainer)
                if (composerCalls.isNotEmpty()) containerCallers.add("$method at $composerCalls")
                val rowCalls = method.inlineActionBarRenderCallIndices()
                if (rowCalls.isNotEmpty()) actionRowConsumers.add("$method at $rowCalls")
            }
        }
        "NewX media reply-bar capability is not proven: no direct photo/native-navigation-fallback " +
            "renderer for $minimalContainer. Direct media callers: $directCallers. " +
            "Selected immersive safe-area renderer: ${immersiveHook?.method ?: "absent"}. " +
            "All direct container callers: $containerCallers. All action-row consumers: $actionRowConsumers. " +
            "A selected animated content path proves its own immersive hook, not removal of the " +
            "legacy photo surface or closed coverage of media action-row consumption. " +
            "The historical compact composer has no native spacer and cannot replace that contract. " +
            "Refusing to omit the required photo hook"
    } else {
        "NewX photo-viewer controls renderer with native navigation fallback"
    }
    val originalMethod =
        requireExactlyOne(
            label = missingCapability,
            candidates = candidates,
        )
    val method =
        requireExactlyOne(
            label = "NewX mutable photo-viewer controls renderer",
            candidates =
                context.mutableClassDefBy(originalMethod.definingClass).methods.filter { method ->
                    method.matches(originalMethod)
                },
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
        unsupportedOnMount()
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
            val immersiveActionBarSafeAreaHook =
                resolveImmersiveActionBarSafeAreaHook(minimalContainer)
            val photoViewerNavigationFallbackHook =
                resolvePhotoViewerNavigationFallbackHook(minimalContainer, immersiveActionBarSafeAreaHook)

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
            photoViewerNavigationFallbackHook?.let { hook ->
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
