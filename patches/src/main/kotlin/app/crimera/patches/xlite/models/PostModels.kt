package app.crimera.patches.xlite.models

import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.WeakHashMap

private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val INLINE_ACTION_BAR_SCOPE = "Lcom/x/inlineactionbar/"

/**
 * Base post models: labels that are present whether or not the release keeps media or
 * action-entry facts. Media and action labels are resolved only by the narrower resolvers
 * that actually consume them.
 */
private object ContextualPostModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(
        string("ContextualPost(canonicalPost="),
        string(", quotedPost="),
    ),
)

private object CanonicalPostModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("CanonicalPost(id=")),
)

// ALPHA PATH: the inline-action model has no count field.
// TODO: Remove this fingerprint when alpha compatibility is deprecated.
private object InlineActionEntryModelWithoutCountFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(
        string("InlineActionEntry(actionType="),
        string(", isEnabled="),
    ),
)

// BETA PATH: the inline-action model adds a count field; keep this shape for future updates.
private object InlineActionEntryModelWithCountFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(
        string("InlineActionEntry(actionType="),
        string(", count="),
        string(", isEnabled="),
    ),
)

internal data class ResolvedXLitePostModels(
    val contextualPostDescriptor: String,
    val contextualCanonicalPostField: FieldReference,
    val contextualRepostedPostField: FieldReference,
    val repostedCanonicalPostField: FieldReference,
    val canonicalPostDescriptor: String,
)

internal data class ResolvedXLitePostMediaModels(
    val postModels: ResolvedXLitePostModels,
    val contextualMediaVisibilityResultsField: FieldReference,
    val canonicalPostMediaField: FieldReference,
)

internal data class ResolvedXLiteInlineActionModels(
    val inlineActionEntryDescriptor: String,
    val inlineActionTypeField: FieldReference,
    val inlineActionEnabledField: FieldReference,
    val postActionTypeDescriptor: String,
)

internal data class ResolvedXLiteInlineActionBarModels(
    val canonicalPostInterfaceDescriptor: String,
    val canonicalPostInlineActionEntryField: FieldReference,
    val inlineActionBarDescriptor: String,
    val inlineActionStateBuilder: MethodReference,
)

internal data class ResolvedXLiteInlineDownloadModels(
    val inlineActionEntryConstructor: MethodReference,
    val twitterShareActionField: FieldReference,
)

/**
 * Immutable handles for the shared post-model fingerprints. Feature resolvers derive their own
 * fields from these handles so media/action requirements do not become core-post requirements.
 */
private data class ResolvedXLitePostModelAnchors(
    val contextualPostDescriptor: String,
    val contextualPostToStringMethod: MethodReference,
    val canonicalPostDescriptor: String,
    val canonicalPostToStringMethod: MethodReference,
)

private class PostModelResolutionState {
    private var postModelAnchors: ResolvedXLitePostModelAnchors? = null
    private var postModels: ResolvedXLitePostModels? = null
    private var postMediaModels: ResolvedXLitePostMediaModels? = null
    private var inlineActionModels: ResolvedXLiteInlineActionModels? = null
    private var inlineActionBarModels: ResolvedXLiteInlineActionBarModels? = null
    private var inlineDownloadModels: ResolvedXLiteInlineDownloadModels? = null

    context(context: BytecodePatchContext)
    fun postModelAnchors(): ResolvedXLitePostModelAnchors = synchronized(this) {
        postModelAnchors ?: resolvePostModelAnchors().also { postModelAnchors = it }
    }

    context(context: BytecodePatchContext)
    fun postModels(): ResolvedXLitePostModels = synchronized(this) {
        postModels ?: resolvePostModels().also { postModels = it }
    }

    context(context: BytecodePatchContext)
    fun postMediaModels(): ResolvedXLitePostMediaModels = synchronized(this) {
        postMediaModels ?: resolvePostMediaModels(postModels()).also { postMediaModels = it }
    }

    context(context: BytecodePatchContext)
    fun inlineActionModels(): ResolvedXLiteInlineActionModels = synchronized(this) {
        inlineActionModels ?: resolveInlineActionModels().also { inlineActionModels = it }
    }

    context(context: BytecodePatchContext)
    fun inlineActionBarModels(): ResolvedXLiteInlineActionBarModels = synchronized(this) {
        inlineActionBarModels ?: resolveInlineActionBarModels(postModels()).also {
            inlineActionBarModels = it
        }
    }

    context(context: BytecodePatchContext)
    fun inlineDownloadModels(): ResolvedXLiteInlineDownloadModels = synchronized(this) {
        inlineDownloadModels ?: resolveInlineDownloadModels(inlineActionModels()).also {
            inlineDownloadModels = it
        }
    }
}

private object PostModelResolutionCache {
    private val values = WeakHashMap<BytecodePatchContext, PostModelResolutionState>()

    @Synchronized
    fun getOrPut(context: BytecodePatchContext): PostModelResolutionState =
        values.getOrPut(context) { PostModelResolutionState() }
}

internal val xLitePostModelResolutionPatch =
    bytecodePatch(default = false) {
        execute {
            resolvedXLitePostModels()
        }
    }

internal val xLitePostMediaModelResolutionPatch =
    bytecodePatch(default = false) {
        dependsOn(xLitePostModelResolutionPatch)

        execute {
            resolvedXLitePostMediaModels()
        }
    }

internal val xLiteInlineActionModelResolutionPatch =
    bytecodePatch(default = false) {
        execute {
            resolvedXLiteInlineActionModels()
        }
    }

internal val xLiteInlineActionBarModelResolutionPatch =
    bytecodePatch(default = false) {
        dependsOn(xLitePostModelResolutionPatch)

        execute {
            resolvedXLiteInlineActionBarModels()
        }
    }

internal val xLiteInlineDownloadModelResolutionPatch =
    bytecodePatch(default = false) {
        dependsOn(xLiteInlineActionModelResolutionPatch)

        execute {
            resolvedXLiteInlineDownloadModels()
        }
    }

context(context: BytecodePatchContext)
private fun postModelResolutionState(): PostModelResolutionState =
    PostModelResolutionCache.getOrPut(context)

context(context: BytecodePatchContext)
private fun resolvedXLitePostModelAnchors(): ResolvedXLitePostModelAnchors =
    postModelResolutionState().postModelAnchors()

context(context: BytecodePatchContext)
internal fun resolvedXLitePostModels(): ResolvedXLitePostModels =
    postModelResolutionState().postModels()

context(context: BytecodePatchContext)
internal fun resolvedXLitePostMediaModels(): ResolvedXLitePostMediaModels =
    postModelResolutionState().postMediaModels()

context(context: BytecodePatchContext)
internal fun resolvedXLiteInlineActionModels(): ResolvedXLiteInlineActionModels =
    postModelResolutionState().inlineActionModels()

context(context: BytecodePatchContext)
internal fun resolvedXLiteInlineActionBarModels(): ResolvedXLiteInlineActionBarModels =
    postModelResolutionState().inlineActionBarModels()

context(context: BytecodePatchContext)
internal fun resolvedXLiteInlineDownloadModels(): ResolvedXLiteInlineDownloadModels =
    postModelResolutionState().inlineDownloadModels()

context(context: BytecodePatchContext)
private fun resolvePostModelAnchors(): ResolvedXLitePostModelAnchors {
    val contextualPostMatch = ContextualPostModelFingerprint.requireSingle("contextual post model")
    val canonicalPostMatch = CanonicalPostModelFingerprint.requireSingle("canonical post model")
    return ResolvedXLitePostModelAnchors(
        contextualPostDescriptor = contextualPostMatch.originalClassDef.type,
        contextualPostToStringMethod = contextualPostMatch.originalMethod,
        canonicalPostDescriptor = canonicalPostMatch.originalClassDef.type,
        canonicalPostToStringMethod = canonicalPostMatch.originalMethod,
    )
}

context(context: BytecodePatchContext)
private fun resolvePostModels(): ResolvedXLitePostModels {
    val anchors = resolvedXLitePostModelAnchors()
    val contextualPostClass = context.classDefByOrNull(anchors.contextualPostDescriptor)
        ?: throw PatchException(
            "X-Lite contextual-post class was not found: ${anchors.contextualPostDescriptor}",
        )
    val canonicalPostDescriptor = anchors.canonicalPostDescriptor
    val contextualCanonicalPostField = contextualPostClass.fields.singleOrNull { field ->
        !AccessFlags.STATIC.isSet(field.accessFlags) && field.type == canonicalPostDescriptor
    } ?: throw PatchException(
        "Expected one X-Lite contextual canonical-post field in " +
            contextualPostClass,
    )
    val contextualPostMethod = anchors.contextualPostToStringMethod.resolveCurrentMethod(
        "contextual post toString",
    )
    val contextualRepostedPostReference =
        contextualPostMethod.fieldForToStringLabel(", rePostedPost=")
    val contextualRepostedPostField = contextualPostClass.fields.singleOrNull { field ->
        field.toString() == contextualRepostedPostReference.toString()
    } ?: throw PatchException(
        "X-Lite contextual reposted-post field was not found in $contextualPostClass",
    )
    if (AccessFlags.STATIC.isSet(contextualRepostedPostField.accessFlags)) {
        throw PatchException(
            "X-Lite contextual reposted-post field is static: $contextualRepostedPostField",
        )
    }
    val repostedPostClass = context.classDefByOrNull(contextualRepostedPostField.type)
        ?: throw PatchException(
            "X-Lite reposted-post class was not found: ${contextualRepostedPostField.type}",
        )
    val repostedCanonicalPostField = repostedPostClass.fields.singleOrNull { field ->
        !AccessFlags.STATIC.isSet(field.accessFlags) && field.type == canonicalPostDescriptor
    } ?: throw PatchException(
        "Expected one X-Lite reposted canonical-post field in $repostedPostClass",
    )

    return ResolvedXLitePostModels(
        contextualPostDescriptor = anchors.contextualPostDescriptor,
        contextualCanonicalPostField = contextualCanonicalPostField,
        contextualRepostedPostField = contextualRepostedPostField,
        repostedCanonicalPostField = repostedCanonicalPostField,
        canonicalPostDescriptor = canonicalPostDescriptor,
    )
}

context(context: BytecodePatchContext)
private fun resolvePostMediaModels(postModels: ResolvedXLitePostModels): ResolvedXLitePostMediaModels {
    val anchors = resolvedXLitePostModelAnchors()
    val contextualPostMethod = anchors.contextualPostToStringMethod.resolveCurrentMethod(
        "contextual post toString",
    )
    val canonicalPostMethod = anchors.canonicalPostToStringMethod.resolveCurrentMethod(
        "canonical post toString",
    )

    return ResolvedXLitePostMediaModels(
        postModels = postModels,
        contextualMediaVisibilityResultsField =
            contextualPostMethod.fieldForToStringLabel(", mediaVisibilityResults="),
        canonicalPostMediaField = canonicalPostMethod.fieldForToStringLabel(", media="),
    )
}

context(context: BytecodePatchContext)
private fun resolveInlineActionModels(): ResolvedXLiteInlineActionModels {
    val inlineActionEntryMatches =
        listOf(
            // ALPHA PATH: model without count.
            InlineActionEntryModelWithoutCountFingerprint.scopedMatchAll(),
            // BETA PATH: model with count.
            InlineActionEntryModelWithCountFingerprint.scopedMatchAll(),
        ).flatten()
            .distinctBy { it.originalMethod.toString() }
    if (inlineActionEntryMatches.size != 1) {
        throw PatchException(
            "Expected one X-Lite inline-action entry model across known shapes, found " +
                "${inlineActionEntryMatches.size}: " +
                inlineActionEntryMatches.joinToString { it.originalMethod.toString() },
        )
    }
    val inlineActionEntryMatch = inlineActionEntryMatches.single()
    val inlineActionEntryClass = inlineActionEntryMatch.originalClassDef
    val inlineActionTypeField =
        inlineActionEntryMatch.fieldForToStringLabel("InlineActionEntry(actionType=")
    val inlineActionEnabledField = inlineActionEntryMatch.fieldForBooleanToStringLabel(", isEnabled=")
    if (!inlineActionTypeField.type.startsWith("L")) {
        throw PatchException("X-Lite inline-action type field is not an object: $inlineActionTypeField")
    }
    if (inlineActionEnabledField.type != "Z") {
        throw PatchException("X-Lite inline-action enabled field is not boolean: $inlineActionEnabledField")
    }

    return ResolvedXLiteInlineActionModels(
        inlineActionEntryDescriptor = inlineActionEntryClass.type,
        inlineActionTypeField = inlineActionTypeField,
        inlineActionEnabledField = inlineActionEnabledField,
        postActionTypeDescriptor = inlineActionTypeField.type,
    )
}

context(context: BytecodePatchContext)
private fun resolveInlineActionBarModels(
    postModels: ResolvedXLitePostModels,
): ResolvedXLiteInlineActionBarModels {
    val anchors = resolvedXLitePostModelAnchors()
    val canonicalPostClass = context.classDefByOrNull(postModels.canonicalPostDescriptor)
        ?: throw PatchException(
            "X-Lite canonical-post class was not found: ${postModels.canonicalPostDescriptor}",
        )
    val canonicalPostInterfaceDescriptor = canonicalPostClass.interfaces.singleOrNull()?.toString()
        ?: throw PatchException(
            "Expected one X-Lite canonical-post interface in $canonicalPostClass: " +
                canonicalPostClass.interfaces.joinToString(),
        )
    val inlineActionBarMatches =
        listOf(
            Fingerprint(
                definingClass = INLINE_ACTION_BAR_SCOPE,
                parameters = listOf(COMPOSER_DESCRIPTOR),
                filters = listOf(
                    methodCall(
                        definingClass = canonicalPostInterfaceDescriptor,
                        parameters = emptyList(),
                        returnType = "L",
                    ),
                    methodCall(smali = "Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z"),
                ),
            ).scopedMatchAllOrNull().orEmpty(),
            Fingerprint(
                definingClass = INLINE_ACTION_BAR_SCOPE,
                parameters = listOf(COMPOSER_DESCRIPTOR),
                filters = listOf(
                    methodCall(
                        smali =
                            "Lcom/x/models/ContextualPost;->getInlineActionEntry()" +
                                "Lkotlinx/collections/immutable/c;",
                    ),
                    methodCall(smali = "Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z"),
                ),
            ).scopedMatchAllOrNull().orEmpty(),
        ).flatten()
            .distinctBy { it.originalMethod.toString() }
    if (inlineActionBarMatches.size != 1) {
        throw PatchException(
            "Expected one X-Lite inline action state builder, found ${inlineActionBarMatches.size}: " +
                inlineActionBarMatches.joinToString { it.originalMethod.toString() },
        )
    }
    val inlineActionBarMatch = inlineActionBarMatches.single()

    return ResolvedXLiteInlineActionBarModels(
        canonicalPostInterfaceDescriptor = canonicalPostInterfaceDescriptor,
        canonicalPostInlineActionEntryField = anchors.canonicalPostToStringMethod
            .resolveCurrentMethod("canonical post toString")
            .fieldForToStringLabel(", inlineActionEntry="),
        inlineActionBarDescriptor = inlineActionBarMatch.originalClassDef.type,
        inlineActionStateBuilder = inlineActionBarMatch.originalMethod,
    )
}

context(context: BytecodePatchContext)
private fun resolveInlineDownloadModels(
    entryModels: ResolvedXLiteInlineActionModels,
): ResolvedXLiteInlineDownloadModels {
    val inlineActionEntryClass = context.classDefByOrNull(entryModels.inlineActionEntryDescriptor)
        ?: throw PatchException(
            "X-Lite inline action entry class was not found: ${entryModels.inlineActionEntryDescriptor}",
        )
    val inlineActionEntryConstructor = inlineActionEntryClass.methods.singleOrNull { method ->
        method.name == "<init>" &&
            method.parameterTypes.map(CharSequence::toString) ==
            listOf(entryModels.postActionTypeDescriptor, "Ljava/lang/Long;", "Z") &&
            method.returnType == "V"
    } ?: throw PatchException(
        "Expected one X-Lite inline-action constructor in $inlineActionEntryClass",
    )
    val actionTypeClass = context.classDefByOrNull(entryModels.postActionTypeDescriptor)
        ?: throw PatchException(
            "X-Lite post action type class was not found: ${entryModels.postActionTypeDescriptor}",
        )
    val twitterShareActionField = actionTypeClass.fields.singleOrNull { field ->
        AccessFlags.STATIC.isSet(field.accessFlags) &&
            field.type == entryModels.postActionTypeDescriptor &&
            field.name == "TwitterShare"
    } ?: throw PatchException(
        "Expected one X-Lite TwitterShare action constant in $actionTypeClass",
    )

    return ResolvedXLiteInlineDownloadModels(
        inlineActionEntryConstructor = inlineActionEntryConstructor,
        twitterShareActionField = twitterShareActionField,
    )
}
