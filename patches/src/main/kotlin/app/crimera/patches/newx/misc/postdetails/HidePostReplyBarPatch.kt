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
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.string
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * The inline post-detail composer marks its text field with this stable Compose test tag. The
 * tag is inside the minimal-composer renderer, while the floating new-post action is rendered by
 * its caller, so returning from this renderer hides only the persistent reply bar.
 */
private object NewXPostDetailReplyBarFingerprint : Fingerprint(
    definingClass = "Lcom/x/composer/minimal/",
    returnType = "V",
    filters = listOf(string("post-detail-reply-text-field")),
)

private const val COMPOSER_MINIMAL_SCOPE = "Lcom/x/composer/minimal/"
private const val POST_DETAIL_SHEET_SCOPE = "Lcom/x/postdetailsheet/"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val COMPOSABLE_LAMBDA_DESCRIPTOR = "Landroidx/compose/runtime/internal/f;"
private const val WINDOW_INSETS_DESCRIPTOR = "Landroidx/compose/foundation/layout/c;"
private const val WINDOW_INSETS_STATE_DESCRIPTOR = "Landroidx/compose/foundation/layout/e4;"
private const val WINDOW_INSETS_PROVIDER_DESCRIPTOR = "Landroidx/compose/foundation/layout/c4;"
private const val WINDOW_INSETS_MODIFIER_DESCRIPTOR = "Landroidx/compose/foundation/layout/d4;"

private object NewXMainNavigationInsetsFingerprint : Fingerprint(
    definingClass = "Lcom/x/android/main/MainActivity;",
    parameters = listOf("Z", COMPOSABLE_LAMBDA_DESCRIPTOR, COMPOSER_DESCRIPTOR, "I"),
    returnType = "V",
    custom = { method, _ -> method.hasNavigationBarPaddingCall() },
)

private object NewXPostDetailNavigationInsetsFingerprint : Fingerprint(
    definingClass = POST_DETAIL_SHEET_SCOPE,
    custom = { method, _ -> method.hasPostDetailNavigationInsetsCall() },
)

private data class NavigationInsetsHook(
    val method: MutableMethod,
    val callIndex: Int,
    val continuation: Instruction,
)

/**
 * The renderer is called through minimal-composer helpers before the post-detail sheet adds the
 * navigation-bar inset. Resolving that call chain keeps the second hook independent of the
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
            if (classDef.type.startsWith(scope)) {
                classDef.methods.forEach { method ->
                    if (method.isCallerOf(target) && predicate(method)) add(method)
                }
            }
        }
    }
    if (candidates.size != 1) {
        throw PatchException(
            "Expected one $label, found ${candidates.size}: " +
                candidates.joinToString { it.toString() },
        )
    }
    return candidates.single()
}

private fun Method.isMinimalComposerRendererCaller(): Boolean =
    returnType == "V" &&
        parameterTypes.map(CharSequence::toString).contains(MODIFIER_DESCRIPTOR)

private fun Method.isMinimalComposerContainerCaller(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return returnType == "V" &&
        parameters.firstOrNull()?.startsWith(COMPOSER_MINIMAL_SCOPE) == true &&
        parameters.getOrNull(1)?.startsWith("Ldev/chrisbanes/haze/") == true &&
        parameters.getOrNull(2) == MODIFIER_DESCRIPTOR &&
        parameters.getOrNull(3) == "Z" &&
        parameters.contains(COMPOSER_DESCRIPTOR)
}

private fun Method.isPostDetailReplyBarContainer(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return returnType == "V" && COMPOSER_DESCRIPTOR in parameters
}

private fun Method.isCallerOf(target: Method): Boolean =
    implementation?.instructions?.any { instruction ->
        if (instruction.opcode != Opcode.INVOKE_STATIC &&
            instruction.opcode != Opcode.INVOKE_STATIC_RANGE
        ) {
            return@any false
        }
        val reference = instruction.getReference<MethodReference>() ?: return@any false
        reference.matches(target)
    } == true

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

private fun Method.hasNavigationBarPaddingCall(): Boolean =
    implementation?.instructions?.count { instruction ->
        instruction.isNavigationBarPaddingCall()
    } == 1

private fun Method.hasPostDetailNavigationInsetsCall(): Boolean {
    val instructions = implementation?.instructions?.toList() ?: return false
    return instructions.indices.count { index ->
        instructions.isPostDetailNavigationInsetsCall(index)
    } == 1
}

private fun Instruction.isNavigationBarPaddingCall(): Boolean {
    val reference = getReference<MethodReference>() ?: return false
    return reference.definingClass.startsWith("Landroidx/compose/foundation/layout/") &&
        reference.name == "P" &&
        reference.returnType == MODIFIER_DESCRIPTOR &&
        reference.parameterTypes.map(CharSequence::toString) ==
            listOf(MODIFIER_DESCRIPTOR, WINDOW_INSETS_DESCRIPTOR)
}

private fun List<Instruction>.isPostDetailNavigationInsetsCall(index: Int): Boolean {
    val call = getOrNull(index)?.getReference<MethodReference>() ?: return false
    if (
        call.definingClass != "Landroidx/compose/foundation/layout/f;" ||
        call.name != "s" ||
        call.returnType != MODIFIER_DESCRIPTOR ||
        call.parameterTypes.map(CharSequence::toString) !=
            listOf(MODIFIER_DESCRIPTOR, WINDOW_INSETS_MODIFIER_DESCRIPTOR)
    ) {
        return false
    }

    if (getOrNull(index - 2)?.opcode != Opcode.MOVE_RESULT_OBJECT) return false
    val insetField = getOrNull(index - 1)?.getReference<FieldReference>() ?: return false
    if (
        insetField.definingClass != WINDOW_INSETS_STATE_DESCRIPTOR ||
        insetField.name != "e" ||
        insetField.type != WINDOW_INSETS_DESCRIPTOR
    ) {
        return false
    }

    val provider = getOrNull(index - 3)?.getReference<MethodReference>() ?: return false
    if (
        provider.definingClass != WINDOW_INSETS_PROVIDER_DESCRIPTOR ||
        provider.name != "e" ||
        provider.returnType != WINDOW_INSETS_STATE_DESCRIPTOR ||
        provider.parameterTypes.map(CharSequence::toString) != listOf(COMPOSER_DESCRIPTOR)
    ) {
        return false
    }

    val stateField = getOrNull(index - 4)?.getReference<FieldReference>() ?: return false
    return stateField.definingClass == WINDOW_INSETS_STATE_DESCRIPTOR &&
        stateField.name == "w"
}

context(context: BytecodePatchContext)
private fun resolveMainNavigationInsetsHook(): NavigationInsetsHook? {
    val matches = NewXMainNavigationInsetsFingerprint.scopedMatchAllOrNull().orEmpty()
    if (matches.size > 1) {
        throw PatchException(
            "Expected at most one NewX main navigation inset renderer, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
    }
    val match = matches.singleOrNull() ?: return null
    val callIndices =
        match.method.instructions.mapIndexedNotNull { index, instruction ->
            index.takeIf { instruction.isNavigationBarPaddingCall() }
        }
    if (callIndices.size != 1) {
        throw PatchException(
            "Expected one NewX main navigation-bar padding call in ${match.method}, found " +
                callIndices.size,
        )
    }

    val callIndex = callIndices.single()
    if (match.method.instructions.getOrNull(callIndex + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException(
            "NewX main navigation-bar padding call is not followed by move-result-object in " +
                match.method,
        )
    }
    val continuation =
        match.method.instructions.getOrNull(callIndex + 2)
            ?: throw PatchException(
                "NewX main navigation-bar padding call has no continuation in ${match.method}",
            )
    return NavigationInsetsHook(match.method, callIndex, continuation)
}

context(context: BytecodePatchContext)
private fun resolvePostDetailNavigationInsetsHook(): NavigationInsetsHook? {
    val matches = NewXPostDetailNavigationInsetsFingerprint.scopedMatchAllOrNull().orEmpty()
    if (matches.size > 1) {
        throw PatchException(
            "Expected at most one NewX post-detail navigation inset renderer, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
    }
    val match = matches.singleOrNull() ?: return null
    val instructions = match.method.instructions.toList()
    val callIndices =
        instructions.mapIndexedNotNull { index, _ ->
            index.takeIf { instructions.isPostDetailNavigationInsetsCall(index) }
        }
    if (callIndices.size != 1) {
        throw PatchException(
            "Expected one NewX post-detail navigation inset call in ${match.method}, found " +
                callIndices.size,
        )
    }

    val callIndex = callIndices.single()
    val continuation =
        match.method.instructions.getOrNull(callIndex + 1)
            ?: throw PatchException(
                "NewX post-detail navigation inset call has no continuation in ${match.method}",
            )
    return NavigationInsetsHook(match.method, callIndex, continuation)
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
            val postDetailNavigationInsetsHook = resolvePostDetailNavigationInsetsHook()
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
