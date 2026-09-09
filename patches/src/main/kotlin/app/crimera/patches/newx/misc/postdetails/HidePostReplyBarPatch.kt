package app.crimera.patches.newx.misc.postdetails

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.branchIfEnabled
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.returnVoidIfEnabled
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val COMPOSER_MINIMAL_SCOPE = "Lcom/x/composer/minimal/"
private const val POST_DETAIL_SHEET_SCOPE = "Lcom/x/postdetailsheet/"
private const val HAZE_SCOPE = "Ldev/chrisbanes/haze/"
private const val FOUNDATION_LAYOUT_SCOPE = "Landroidx/compose/foundation/layout/"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val COMPOSABLE_LAMBDA_DESCRIPTOR = "Landroidx/compose/runtime/internal/f;"

/**
 * The inline post-detail composer marks its text field with this stable Compose test tag. The
 * tag is inside the minimal-composer renderer, while the floating new-post action is rendered by
 * its caller, so returning from this renderer hides only the persistent reply bar.
 */
private object NewXPostDetailReplyBarFingerprint : Fingerprint(
    definingClass = COMPOSER_MINIMAL_SCOPE,
    returnType = "V",
    filters = listOf(string("post-detail-reply-text-field")),
    custom = { method, _ -> method.isPostDetailReplyBarRenderer() },
)

/**
 * The root Compose renderer keeps this stable signature across targets. Older targets do not
 * contain the navigation-insets call; the resolver explicitly validates that legacy shape instead
 * of treating a missing fingerprint as a successful optional patch.
 */
private object NewXMainNavigationRootFingerprint : Fingerprint(
    definingClass = "Lcom/x/android/main/MainActivity;",
    parameters = listOf("Z", COMPOSABLE_LAMBDA_DESCRIPTOR, COMPOSER_DESCRIPTOR, "I"),
    returnType = "V",
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

private fun Method.isPostDetailReplyBarRenderer(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
        parameters.count { it == "Ljava/lang/String;" } == 1 &&
        parameters.any { it.startsWith(HAZE_SCOPE) }
}

private fun Method.hasString(value: String): Boolean =
    implementation?.instructions?.any { instruction ->
        instruction.getReference<StringReference>()?.string == value
    } == true

private fun Method.hasStrings(vararg values: String): Boolean = values.all(::hasString)

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

    val insetRead = navigationInsetsReadsBefore(index).singleOrNull { read ->
        callInstruction.registersUsed.contains(read.destinationRegister)
    }
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
    return returnType == "V" &&
        parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
        parameters.count { it == MODIFIER_DESCRIPTOR } == 1 &&
        parameters.any { it.startsWith(COMPOSER_MINIMAL_SCOPE) } &&
        parameters.any { it.startsWith(HAZE_SCOPE) } &&
        hasStrings("inlineComposer", "hazeState")
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
        minimalMutableClass.methods.singleOrNull { method ->
            method.matches(minimalContainerCaller)
        } as? MutableMethod
            ?: throw PatchException(
                "NewX minimal reply-bar composition caller is not mutable: $minimalContainerCaller",
            )

    val postDetailMutableClass = context.mutableClassDefBy(postDetailContainer.definingClass)
    val postDetailMutableMethod =
        postDetailMutableClass.methods.singleOrNull { method ->
            method.matches(postDetailContainer)
        } as? MutableMethod
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
                if (callSites.isNotEmpty()) add(method to callSites.size)
            }
        }
    }
    val invalidCallCounts = candidates.filter { (_, callCount) -> callCount != 1 }
    if (invalidCallCounts.isNotEmpty()) {
        throw PatchException(
            "Expected one call to $target in $label candidates, found: " +
                invalidCallCounts.joinToString { (method, callCount) -> "$method ($callCount)" },
        )
    }
    if (candidates.size != 1) {
        throw PatchException(
            "Expected one $label, found ${candidates.size}: " +
                candidates.joinToString { (method, _) -> method.toString() },
        )
    }
    return candidates.single().first
}

private fun requireNavigationInsetsHook(
    method: MutableMethod,
    label: String,
): NavigationInsetsHook {
    val instructions = method.instructions.toList()
    val callIndices = instructions.navigationInsetsCallIndices()
    if (callIndices.size != 1) {
        throw PatchException(
            "Expected one $label navigation-insets call in $method, found " +
                callIndices.size,
        )
    }

    val callIndex = callIndices.single()
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
private fun resolveMainNavigationInsetsHook(): NavigationInsetsHook? {
    val matches = NewXMainNavigationRootFingerprint.scopedMatchAllOrNull().orEmpty()
    if (matches.size != 1) {
        throw PatchException(
            "Expected one NewX main navigation root renderer, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
    }
    val method = matches.single().method
    val callCount = method.navigationInsetsCallIndices().size
    if (callCount == 0) {
        if (method.hasString("insets")) {
            throw PatchException(
                "NewX main navigation root has the inset marker but no semantic inset call: $method",
            )
        }
        return null
    }
    if (callCount != 1) {
        throw PatchException(
            "Expected one NewX main navigation-insets call in $method, found $callCount",
        )
    }
    return requireNavigationInsetsHook(method, "NewX main")
}

context(context: BytecodePatchContext)
private fun resolvePostDetailNavigationInsetsHook(
    postDetailContainer: MutableMethod,
): NavigationInsetsHook {
    val matches = NewXPostDetailNavigationInsetsFingerprint.scopedMatchAllOrNull().orEmpty()
    if (matches.size != 1) {
        throw PatchException(
            "Expected one NewX post-detail navigation inset renderer, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
    }
    val match = matches.single()
    if (!postDetailContainer.matches(match.originalMethod)) {
        throw PatchException(
            "NewX post-detail navigation inset renderer is not the reply-bar container: " +
                "${match.originalMethod} vs $postDetailContainer",
        )
    }
    return requireNavigationInsetsHook(postDetailContainer, "NewX post-detail")
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
            val matches = NewXPostDetailReplyBarFingerprint.scopedMatchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX post-detail reply bar renderer, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }
            val renderer = matches.single()
            val (minimalContainer, postDetailSheetContainer) = resolvePostDetailReplyBarContainers(renderer)
            val navigationInsetsHook = resolveMainNavigationInsetsHook()
            val postDetailNavigationInsetsHook =
                resolvePostDetailNavigationInsetsHook(postDetailSheetContainer)

            hidePostReplyBar.returnVoidIfEnabled(renderer.method, 0)
            hidePostReplyBar.returnVoidIfEnabled(minimalContainer, 0)
            navigationInsetsHook?.let { hook ->
                hidePostReplyBar.branchIfEnabled(hook.method, hook.callIndex, hook.continuation)
            }
            postDetailNavigationInsetsHook?.let { hook ->
                hidePostReplyBar.branchIfEnabled(hook.method, hook.callIndex, hook.continuation)
            }
            hidePostReplyBar.returnVoidIfEnabled(postDetailSheetContainer, 0)
        }
    }
