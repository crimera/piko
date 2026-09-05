package app.crimera.patches.newx.models

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

internal data class ResolvedNewXPostModels(
    val contextualPostDescriptor: String,
    val contextualCanonicalPostField: FieldReference,
    val contextualRepostedPostField: FieldReference,
    val repostedCanonicalPostField: FieldReference,
    val canonicalPostDescriptor: String,
)

internal data class ResolvedNewXPostMediaModels(
    val postModels: ResolvedNewXPostModels,
    val contextualMediaVisibilityResultsField: FieldReference,
    val canonicalPostMediaField: FieldReference,
)

internal data class ResolvedNewXInlineActionModels(
    val inlineActionEntryDescriptor: String,
    val inlineActionTypeField: FieldReference,
    val inlineActionEnabledField: FieldReference,
    val postActionTypeDescriptor: String,
)

internal data class ResolvedNewXInlineActionBarModels(
    val canonicalPostInterfaceDescriptor: String,
    val canonicalPostInlineActionEntryField: FieldReference,
    val inlineActionBarDescriptor: String,
    val inlineActionStateBuilder: MethodReference,
)

internal data class ResolvedNewXInlineDownloadModels(
    val inlineActionEntryConstructor: MethodReference,
    val twitterShareActionField: FieldReference,
)

/**
 * Immutable handles for the shared post-model fingerprints. Feature resolvers derive their own
 * fields from these handles so media/action requirements do not become core-post requirements.
 */
private data class ResolvedNewXPostModelAnchors(
    val contextualPostDescriptor: String,
    val contextualPostToStringMethod: MethodReference,
    val canonicalPostDescriptor: String,
    val canonicalPostToStringMethod: MethodReference,
)

private class PostModelResolutionState {
    private var postModelAnchors: ResolvedNewXPostModelAnchors? = null
    private var postModels: ResolvedNewXPostModels? = null
    private var postMediaModels: ResolvedNewXPostMediaModels? = null
    private var inlineActionModels: ResolvedNewXInlineActionModels? = null
    private var inlineActionBarModels: ResolvedNewXInlineActionBarModels? = null
    private var inlineDownloadModels: ResolvedNewXInlineDownloadModels? = null

    context(context: BytecodePatchContext)
    fun postModelAnchors(): ResolvedNewXPostModelAnchors = synchronized(this) {
        postModelAnchors ?: resolvePostModelAnchors().also { postModelAnchors = it }
    }

    context(context: BytecodePatchContext)
    fun postModels(): ResolvedNewXPostModels = synchronized(this) {
        postModels ?: resolvePostModels().also { postModels = it }
    }

    context(context: BytecodePatchContext)
    fun postMediaModels(): ResolvedNewXPostMediaModels = synchronized(this) {
        postMediaModels ?: resolvePostMediaModels(postModels()).also { postMediaModels = it }
    }

    context(context: BytecodePatchContext)
    fun inlineActionModels(): ResolvedNewXInlineActionModels = synchronized(this) {
        inlineActionModels ?: resolveInlineActionModels().also { inlineActionModels = it }
    }

    context(context: BytecodePatchContext)
    fun inlineActionBarModels(): ResolvedNewXInlineActionBarModels = synchronized(this) {
        inlineActionBarModels ?: resolveInlineActionBarModels(postModels()).also {
            inlineActionBarModels = it
        }
    }

    context(context: BytecodePatchContext)
    fun inlineDownloadModels(): ResolvedNewXInlineDownloadModels = synchronized(this) {
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

internal val newXPostModelResolutionPatch =
    bytecodePatch(default = false) {
        execute {
            resolvedNewXPostModels()
        }
    }

internal val newXPostMediaModelResolutionPatch =
    bytecodePatch(default = false) {
        dependsOn(newXPostModelResolutionPatch)

        execute {
            resolvedNewXPostMediaModels()
        }
    }

internal val newXInlineActionModelResolutionPatch =
    bytecodePatch(default = false) {
        execute {
            resolvedNewXInlineActionModels()
        }
    }

internal val newXInlineActionBarModelResolutionPatch =
    bytecodePatch(default = false) {
        dependsOn(newXPostModelResolutionPatch)

        execute {
            resolvedNewXInlineActionBarModels()
        }
    }

internal val newXInlineDownloadModelResolutionPatch =
    bytecodePatch(default = false) {
        dependsOn(newXInlineActionModelResolutionPatch)

        execute {
            resolvedNewXInlineDownloadModels()
        }
    }

context(context: BytecodePatchContext)
private fun postModelResolutionState(): PostModelResolutionState =
    PostModelResolutionCache.getOrPut(context)

context(context: BytecodePatchContext)
private fun resolvedNewXPostModelAnchors(): ResolvedNewXPostModelAnchors =
    postModelResolutionState().postModelAnchors()

context(context: BytecodePatchContext)
internal fun resolvedNewXPostModels(): ResolvedNewXPostModels =
    postModelResolutionState().postModels()

context(context: BytecodePatchContext)
internal fun resolvedNewXPostMediaModels(): ResolvedNewXPostMediaModels =
    postModelResolutionState().postMediaModels()

context(context: BytecodePatchContext)
internal fun resolvedNewXInlineActionModels(): ResolvedNewXInlineActionModels =
    postModelResolutionState().inlineActionModels()

context(context: BytecodePatchContext)
internal fun resolvedNewXInlineActionBarModels(): ResolvedNewXInlineActionBarModels =
    postModelResolutionState().inlineActionBarModels()

context(context: BytecodePatchContext)
internal fun resolvedNewXInlineDownloadModels(): ResolvedNewXInlineDownloadModels =
    postModelResolutionState().inlineDownloadModels()

context(context: BytecodePatchContext)
private fun resolvePostModelAnchors(): ResolvedNewXPostModelAnchors {
    val contextualPostMatch = ContextualPostModelFingerprint.requireSingle("contextual post model")
    val canonicalPostMatch = CanonicalPostModelFingerprint.requireSingle("canonical post model")
    return ResolvedNewXPostModelAnchors(
        contextualPostDescriptor = contextualPostMatch.originalClassDef.type,
        contextualPostToStringMethod = contextualPostMatch.originalMethod,
        canonicalPostDescriptor = canonicalPostMatch.originalClassDef.type,
        canonicalPostToStringMethod = canonicalPostMatch.originalMethod,
    )
}

context(context: BytecodePatchContext)
private fun resolvePostModels(): ResolvedNewXPostModels {
    val anchors = resolvedNewXPostModelAnchors()
    val contextualPostClass = context.classDefByOrNull(anchors.contextualPostDescriptor)
        ?: throw PatchException(
            "NewX contextual-post class was not found: ${anchors.contextualPostDescriptor}",
        )
    val canonicalPostDescriptor = anchors.canonicalPostDescriptor
    val contextualCanonicalPostField = contextualPostClass.fields.singleOrNull { field ->
        !AccessFlags.STATIC.isSet(field.accessFlags) && field.type == canonicalPostDescriptor
    } ?: throw PatchException(
        "Expected one NewX contextual canonical-post field in " +
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
        "NewX contextual reposted-post field was not found in $contextualPostClass",
    )
    if (AccessFlags.STATIC.isSet(contextualRepostedPostField.accessFlags)) {
        throw PatchException(
            "NewX contextual reposted-post field is static: $contextualRepostedPostField",
        )
    }
    val repostedPostClass = context.classDefByOrNull(contextualRepostedPostField.type)
        ?: throw PatchException(
            "NewX reposted-post class was not found: ${contextualRepostedPostField.type}",
        )
    val repostedCanonicalPostField = repostedPostClass.fields.singleOrNull { field ->
        !AccessFlags.STATIC.isSet(field.accessFlags) && field.type == canonicalPostDescriptor
    } ?: throw PatchException(
        "Expected one NewX reposted canonical-post field in $repostedPostClass",
    )

    return ResolvedNewXPostModels(
        contextualPostDescriptor = anchors.contextualPostDescriptor,
        contextualCanonicalPostField = contextualCanonicalPostField,
        contextualRepostedPostField = contextualRepostedPostField,
        repostedCanonicalPostField = repostedCanonicalPostField,
        canonicalPostDescriptor = canonicalPostDescriptor,
    )
}

context(context: BytecodePatchContext)
private fun resolvePostMediaModels(postModels: ResolvedNewXPostModels): ResolvedNewXPostMediaModels {
    val anchors = resolvedNewXPostModelAnchors()
    val contextualPostMethod = anchors.contextualPostToStringMethod.resolveCurrentMethod(
        "contextual post toString",
    )
    val canonicalPostMethod = anchors.canonicalPostToStringMethod.resolveCurrentMethod(
        "canonical post toString",
    )

    return ResolvedNewXPostMediaModels(
        postModels = postModels,
        contextualMediaVisibilityResultsField =
            contextualPostMethod.fieldForToStringLabel(", mediaVisibilityResults="),
        canonicalPostMediaField = canonicalPostMethod.fieldForToStringLabel(", media="),
    )
}

context(context: BytecodePatchContext)
private fun resolveInlineActionModels(): ResolvedNewXInlineActionModels {
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
            "Expected one NewX inline-action entry model across known shapes, found " +
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
        throw PatchException("NewX inline-action type field is not an object: $inlineActionTypeField")
    }
    if (inlineActionEnabledField.type != "Z") {
        throw PatchException("NewX inline-action enabled field is not boolean: $inlineActionEnabledField")
    }

    return ResolvedNewXInlineActionModels(
        inlineActionEntryDescriptor = inlineActionEntryClass.type,
        inlineActionTypeField = inlineActionTypeField,
        inlineActionEnabledField = inlineActionEnabledField,
        postActionTypeDescriptor = inlineActionTypeField.type,
    )
}

context(context: BytecodePatchContext)
private fun resolveInlineActionBarModels(
    postModels: ResolvedNewXPostModels,
): ResolvedNewXInlineActionBarModels {
    val anchors = resolvedNewXPostModelAnchors()
    val canonicalPostClass = context.classDefByOrNull(postModels.canonicalPostDescriptor)
        ?: throw PatchException(
            "NewX canonical-post class was not found: ${postModels.canonicalPostDescriptor}",
        )
    val canonicalPostInterfaceDescriptor = canonicalPostClass.interfaces.singleOrNull()?.toString()
        ?: throw PatchException(
            "Expected one NewX canonical-post interface in $canonicalPostClass: " +
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
            "Expected one NewX inline action state builder, found ${inlineActionBarMatches.size}: " +
                inlineActionBarMatches.joinToString { it.originalMethod.toString() },
        )
    }
    val inlineActionBarMatch = inlineActionBarMatches.single()

    return ResolvedNewXInlineActionBarModels(
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
    entryModels: ResolvedNewXInlineActionModels,
): ResolvedNewXInlineDownloadModels {
    val inlineActionEntryClass = context.classDefByOrNull(entryModels.inlineActionEntryDescriptor)
        ?: throw PatchException(
            "NewX inline action entry class was not found: ${entryModels.inlineActionEntryDescriptor}",
        )
    val inlineActionEntryConstructor = inlineActionEntryClass.methods.singleOrNull { method ->
        method.name == "<init>" &&
            method.parameterTypes.map(CharSequence::toString) ==
            listOf(entryModels.postActionTypeDescriptor, "Ljava/lang/Long;", "Z") &&
            method.returnType == "V"
    } ?: throw PatchException(
        "Expected one NewX inline-action constructor in $inlineActionEntryClass",
    )
    val actionTypeClass = context.classDefByOrNull(entryModels.postActionTypeDescriptor)
        ?: throw PatchException(
            "NewX post action type class was not found: ${entryModels.postActionTypeDescriptor}",
        )
    val twitterShareActionField = actionTypeClass.fields.singleOrNull { field ->
        AccessFlags.STATIC.isSet(field.accessFlags) &&
            field.type == entryModels.postActionTypeDescriptor &&
            field.name == "TwitterShare"
    } ?: throw PatchException(
        "Expected one NewX TwitterShare action constant in $actionTypeClass",
    )

    return ResolvedNewXInlineDownloadModels(
        inlineActionEntryConstructor = inlineActionEntryConstructor,
        twitterShareActionField = twitterShareActionField,
    )
}
