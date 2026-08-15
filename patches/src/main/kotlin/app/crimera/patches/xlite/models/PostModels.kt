package app.crimera.patches.xlite.models

import app.crimera.patches.utils.scopedMatchAll
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

private object InlineActionEntryModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(
        string("InlineActionEntry(actionType="),
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

private object PostModelCache {
    private val values = WeakHashMap<BytecodePatchContext, ResolvedXLitePostModels>()

    @Synchronized
    fun getOrPut(
        context: BytecodePatchContext,
        resolve: () -> ResolvedXLitePostModels,
    ): ResolvedXLitePostModels = values.getOrPut(context, resolve)
}

private object PostMediaModelCache {
    private val values = WeakHashMap<BytecodePatchContext, ResolvedXLitePostMediaModels>()

    @Synchronized
    fun getOrPut(
        context: BytecodePatchContext,
        resolve: () -> ResolvedXLitePostMediaModels,
    ): ResolvedXLitePostMediaModels = values.getOrPut(context, resolve)
}

private object InlineActionModelCache {
    private val values = WeakHashMap<BytecodePatchContext, ResolvedXLiteInlineActionModels>()

    @Synchronized
    fun getOrPut(
        context: BytecodePatchContext,
        resolve: () -> ResolvedXLiteInlineActionModels,
    ): ResolvedXLiteInlineActionModels = values.getOrPut(context, resolve)
}

private object InlineActionBarModelCache {
    private val values = WeakHashMap<BytecodePatchContext, ResolvedXLiteInlineActionBarModels>()

    @Synchronized
    fun getOrPut(
        context: BytecodePatchContext,
        resolve: () -> ResolvedXLiteInlineActionBarModels,
    ): ResolvedXLiteInlineActionBarModels = values.getOrPut(context, resolve)
}

private object InlineDownloadModelCache {
    private val values = WeakHashMap<BytecodePatchContext, ResolvedXLiteInlineDownloadModels>()

    @Synchronized
    fun getOrPut(
        context: BytecodePatchContext,
        resolve: () -> ResolvedXLiteInlineDownloadModels,
    ): ResolvedXLiteInlineDownloadModels = values.getOrPut(context, resolve)
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
internal fun resolvedXLitePostModels(): ResolvedXLitePostModels =
    PostModelCache.getOrPut(context) { resolvePostModels() }

context(context: BytecodePatchContext)
internal fun resolvedXLitePostMediaModels(): ResolvedXLitePostMediaModels =
    PostMediaModelCache.getOrPut(context) { resolvePostMediaModels(resolvedXLitePostModels()) }

context(context: BytecodePatchContext)
internal fun resolvedXLiteInlineActionModels(): ResolvedXLiteInlineActionModels =
    InlineActionModelCache.getOrPut(context) { resolveInlineActionModels() }

context(context: BytecodePatchContext)
internal fun resolvedXLiteInlineActionBarModels(): ResolvedXLiteInlineActionBarModels =
    InlineActionBarModelCache.getOrPut(context) {
        resolveInlineActionBarModels(resolvedXLitePostModels())
    }

context(context: BytecodePatchContext)
internal fun resolvedXLiteInlineDownloadModels(): ResolvedXLiteInlineDownloadModels =
    InlineDownloadModelCache.getOrPut(context) {
        resolveInlineDownloadModels(resolvedXLiteInlineActionModels())
    }

context(context: BytecodePatchContext)
private fun resolvePostModels(): ResolvedXLitePostModels {
    val contextualPostMatch = ContextualPostModelFingerprint.requireSingle("contextual post model")
    val canonicalPostMatch = CanonicalPostModelFingerprint.requireSingle("canonical post model")
    val canonicalPostDescriptor = canonicalPostMatch.originalClassDef.type
    val contextualPostClass = contextualPostMatch.originalClassDef
    val contextualCanonicalPostField = contextualPostClass.fields.singleOrNull { field ->
        !AccessFlags.STATIC.isSet(field.accessFlags) && field.type == canonicalPostDescriptor
    } ?: throw PatchException(
        "Expected one X-Lite contextual canonical-post field in " +
            contextualPostClass,
    )
    val contextualRepostedPostReference =
        contextualPostMatch.fieldForToStringLabel(", rePostedPost=")
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
        contextualPostDescriptor = contextualPostClass.type,
        contextualCanonicalPostField = contextualCanonicalPostField,
        contextualRepostedPostField = contextualRepostedPostField,
        repostedCanonicalPostField = repostedCanonicalPostField,
        canonicalPostDescriptor = canonicalPostDescriptor,
    )
}

context(context: BytecodePatchContext)
private fun resolvePostMediaModels(postModels: ResolvedXLitePostModels): ResolvedXLitePostMediaModels {
    val contextualPostMatch = ContextualPostModelFingerprint.requireSingle("contextual post model")
    val canonicalPostMatch = CanonicalPostModelFingerprint.requireSingle("canonical post model")

    return ResolvedXLitePostMediaModels(
        postModels = postModels,
        contextualMediaVisibilityResultsField =
            contextualPostMatch.fieldForToStringLabel(", mediaVisibilityResults="),
        canonicalPostMediaField = canonicalPostMatch.fieldForToStringLabel(", media="),
    )
}

context(context: BytecodePatchContext)
private fun resolveInlineActionModels(): ResolvedXLiteInlineActionModels {
    val inlineActionEntryMatch =
        InlineActionEntryModelFingerprint.requireSingle("inline-action entry model")
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
    val canonicalPostMatch = CanonicalPostModelFingerprint.requireSingle("canonical post model")
    val canonicalPostClass = canonicalPostMatch.originalClassDef
    val canonicalPostInterfaceDescriptor = canonicalPostClass.interfaces.singleOrNull()?.toString()
        ?: throw PatchException(
            "Expected one X-Lite canonical-post interface in $canonicalPostClass: " +
                canonicalPostClass.interfaces.joinToString(),
        )
    val inlineActionBarMatches = Fingerprint(
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
    ).scopedMatchAll()
    if (inlineActionBarMatches.size != 1) {
        throw PatchException(
            "Expected one X-Lite inline action state builder, found ${inlineActionBarMatches.size}: " +
                inlineActionBarMatches.joinToString { it.originalMethod.toString() },
        )
    }
    val inlineActionBarMatch = inlineActionBarMatches.single()

    return ResolvedXLiteInlineActionBarModels(
        canonicalPostInterfaceDescriptor = canonicalPostInterfaceDescriptor,
        canonicalPostInlineActionEntryField =
            canonicalPostMatch.fieldForToStringLabel(", inlineActionEntry="),
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
