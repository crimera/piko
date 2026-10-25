package app.crimera.patches.newx.models

import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.isAssignableTo
import app.crimera.patches.common.scopedMatchAll
import app.crimera.patches.common.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import java.util.WeakHashMap

private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val COMPOSE_MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
internal const val INLINE_ACTION_BAR_SCOPE = "Lcom/x/inlineactionbar/"
private const val ITERABLE_DESCRIPTOR = "Ljava/lang/Iterable;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val ITERATOR_DESCRIPTOR = "Ljava/util/Iterator;"
private const val ARRAY_LIST_DESCRIPTOR = "Ljava/util/ArrayList;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"

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

// Current NewX releases retain the count field while obfuscating the model owner and action type.
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

private object InlineImageMediaModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("MediaContentImage(mediaId=")),
)

private object InlineGifMediaModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("MediaContentGif(mediaId=")),
)

private object InlineVideoMediaModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("MediaContentVideo(mediaId=")),
)

private object InlineMediaVariantModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("MediaVariant(url=")),
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

internal data class ResolvedNewXInlineMediaModels(
    val sharedMediaBaseDescriptor: String,
    val imageMediaDescriptor: String,
    val imageUrlField: FieldReference,
    val gifMediaDescriptor: String,
    val gifVariantsField: FieldReference,
    val videoMediaDescriptor: String,
    val videoVariantsField: FieldReference,
    val variantDescriptor: String,
    val variantUrlField: FieldReference,
    val variantContentTypeField: FieldReference,
)

/**
 * Handles for the shared inline-action-bar layout lambda. The injected download action reuses the
 * TwitterShare carrier for its icon/click semantics, but this release's layout switch does not map
 * TwitterShare to a native kind and classifies it as Countless, which reserves a wider slot. The
 * patch identity-gates the injected action immediately before the kind model is built and rewrites
 * only its kind to IconOnly.
 */
internal data class ResolvedNewXInlineActionKindOverride(
    val layoutLambda: MethodReference,
    val kindModelConstructor: MethodReference,
    val iconOnlyField: FieldReference,
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
    private var compositionMediaModels: ResolvedNewXInlineMediaModels? = null
    private var inlineActionKindOverride: ResolvedNewXInlineActionKindOverride? = null
    private var inlineActionKindOverrideResolved: Boolean = false

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

    context(context: BytecodePatchContext)
    fun compositionMediaModels(): ResolvedNewXInlineMediaModels = synchronized(this) {
        compositionMediaModels ?: resolveNewXInlineMediaModels().also {
            compositionMediaModels = it
        }
    }

    context(context: BytecodePatchContext)
    fun inlineActionKindOverride(): ResolvedNewXInlineActionKindOverride? = synchronized(this) {
        if (!inlineActionKindOverrideResolved) {
            inlineActionKindOverride = resolveInlineActionKindOverride(inlineActionModels())
            inlineActionKindOverrideResolved = true
        }
        inlineActionKindOverride
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
internal fun resolvedNewXInlineMediaModels(): ResolvedNewXInlineMediaModels =
    postModelResolutionState().compositionMediaModels()

context(context: BytecodePatchContext)
internal fun resolvedNewXInlineActionKindOverride(): ResolvedNewXInlineActionKindOverride? =
    postModelResolutionState().inlineActionKindOverride()

/**
 * Semantic match for the NewX inline-action entry renderer. Never match this composable by an
 * exact parameter list: R8 repackages the owner (alpha.04 hides it under a
 * firebase/crashlytics namespace) and Compose inserts auxiliary params between releases
 * (Haze blur effect in alpha.01, an extra boolean in alpha.04). Invariants: void renderer,
 * first param is the entry model, Modifier and Composer present, trailing change-flags int.
 * Callers add the entry field-read filters and assert single cardinality.
 */
internal fun Method.isInlineActionEntryRenderer(entryDescriptor: String): Boolean {
    if (returnType != "V") return false
    val params = parameterTypes.map(CharSequence::toString)
    if (params.firstOrNull() != entryDescriptor) return false
    if (params.lastOrNull() != "I") return false
    if (COMPOSER_DESCRIPTOR !in params) return false
    if (COMPOSE_MODIFIER_DESCRIPTOR !in params) return false
    return true
}

/**
 * Slot of the first parameter with the given descriptor, counting wide (J/D) params as two
 * slots. For static methods the slot equals the p-register index.
 */
internal fun Method.firstParameterSlot(descriptor: String): Int {
    var slot = 0
    for (type in parameterTypes.map(CharSequence::toString)) {
        if (type == descriptor) return slot
        slot += if (type == "J" || type == "D") 2 else 1
    }
    throw PatchException("NewX renderer has no $descriptor parameter: $this")
}

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
    val inlineActionEntryMatches = InlineActionEntryModelWithCountFingerprint.scopedMatchAll()
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
    val inlineActionModels = resolvedNewXInlineActionModels()
    val canonicalPostClass = context.classDefByOrNull(postModels.canonicalPostDescriptor)
        ?: throw PatchException(
            "NewX canonical-post class was not found: ${postModels.canonicalPostDescriptor}",
        )
    val canonicalPostInterfaceDescriptor = canonicalPostClass.interfaces.singleOrNull()
        ?: throw PatchException(
            "Expected one NewX canonical-post interface in $canonicalPostClass: " +
                canonicalPostClass.interfaces.joinToString(),
        )
    val canonicalPostInlineActionEntryField = anchors.canonicalPostToStringMethod
        .resolveCurrentMethod("canonical post toString")
        .fieldForToStringLabel(", inlineActionEntry=")
    if (!canonicalPostInlineActionEntryField.type.startsWith("L")) {
        throw PatchException(
            "NewX canonical-post inline-action field is not an object collection: " +
                canonicalPostInlineActionEntryField,
        )
    }
    val inlineActionBarMatches =
        listOf(
            Fingerprint(
                definingClass = INLINE_ACTION_BAR_SCOPE,
                parameters = listOf(COMPOSER_DESCRIPTOR),
                filters = listOf(
                    methodCall(
                        definingClass = canonicalPostInterfaceDescriptor,
                        parameters = emptyList(),
                        returnType = canonicalPostInlineActionEntryField.type,
                    ),
                    methodCall(smali = "Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z"),
                ),
                custom = { method, _ ->
                    method.hasInlineActionCollectionResultFlow(
                        definingClass = canonicalPostInterfaceDescriptor,
                        collectionType = canonicalPostInlineActionEntryField.type,
                        elementType = inlineActionModels.inlineActionEntryDescriptor,
                    )
                },
            ).scopedMatchAllOrNull().orEmpty(),
            Fingerprint(
                definingClass = INLINE_ACTION_BAR_SCOPE,
                parameters = listOf(COMPOSER_DESCRIPTOR),
                filters = listOf(
                    methodCall(
                        definingClass = anchors.contextualPostDescriptor,
                        parameters = emptyList(),
                        returnType = inlineActionModels.inlineActionEntryDescriptor,
                    ),
                    methodCall(smali = "Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z"),
                ),
                custom = { method, _ ->
                    method.hasInlineActionResultFlow(
                        definingClass = anchors.contextualPostDescriptor,
                        returnType = inlineActionModels.inlineActionEntryDescriptor,
                    )
                },
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
        canonicalPostInlineActionEntryField = canonicalPostInlineActionEntryField,
        inlineActionBarDescriptor = inlineActionBarMatch.originalClassDef.type,
        inlineActionStateBuilder = inlineActionBarMatch.originalMethod,
    )
}

private fun Method.hasInlineActionResultFlow(
    definingClass: String,
    returnType: String,
): Boolean {
    val methodInstructions = implementation?.instructions?.toList() ?: return false
    val accessorCalls = methodInstructions.mapIndexedNotNull { index, instruction ->
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        if (
            reference.definingClass != definingClass ||
                reference.parameterTypes.isNotEmpty() ||
                reference.returnType != returnType ||
                methodInstructions.getOrNull(index + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT
        ) {
            return@mapIndexedNotNull null
        }
        val resultRegister =
            (methodInstructions[index + 1] as? OneRegisterInstruction)?.registerA
            ?: return@mapIndexedNotNull null
        index to resultRegister
    }
    if (accessorCalls.size != 1) return false

    val (accessorIndex, resultRegister) = accessorCalls.single()
    val valueRegisters = linkedSetOf(resultRegister)
    val consumerIndices = methodInstructions.mapIndexedNotNull { index, instruction ->
        if (index <= accessorIndex + 1) return@mapIndexedNotNull null
        val reference = instruction.getReference<MethodReference>()
            ?: return@mapIndexedNotNull null
        val arguments = instruction.registersUsed
        if (
            reference.definingClass == "Ljava/util/ArrayList;" &&
                reference.name == "add" &&
                reference.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;") &&
                reference.returnType == "Z" &&
                arguments.size == 2 &&
                arguments[1] in valueRegisters
        ) {
            return@mapIndexedNotNull index
        }
        if (instruction.opcode == Opcode.MOVE_OBJECT || instruction.opcode == Opcode.MOVE) {
            val move = instruction as? TwoRegisterInstruction ?: return@mapIndexedNotNull null
            if (move.registerB in valueRegisters) valueRegisters += move.registerA
        }
        null
    }
    return consumerIndices.size == 1
}

/**
 * The canonical-post accessor returns a collection of inline-action entries. The presenter
 * iterates that collection, casts each element to the resolved model, optionally maps it through
 * a copy/factory method, and adds the resulting entry to an ArrayList. Keep this separate from
 * the direct-entry path above because the collection descriptor is not the model descriptor.
 */
private fun Method.hasInlineActionCollectionResultFlow(
    definingClass: String,
    collectionType: String,
    elementType: String,
): Boolean {
    val methodInstructions = implementation?.instructions?.toList() ?: return false

    fun moveResultObjectRegister(index: Int): Int? {
        val moveResult = methodInstructions.getOrNull(index + 1)
            ?: return null
        if (moveResult.opcode != Opcode.MOVE_RESULT_OBJECT) return null
        return (moveResult as? OneRegisterInstruction)?.registerA
    }

    val accessorCalls = methodInstructions.mapIndexedNotNull { index, instruction ->
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        if (
            reference.definingClass != definingClass ||
                reference.parameterTypes.isNotEmpty() ||
                reference.returnType != collectionType
        ) {
            return@mapIndexedNotNull null
        }
        val resultRegister = moveResultObjectRegister(index)
            ?: return@mapIndexedNotNull null
        index to resultRegister
    }
    if (accessorCalls.size != 1) return false

    val (accessorIndex, collectionResultRegister) = accessorCalls.single()
    val collectionRegisters = linkedSetOf(collectionResultRegister)
    val iteratorCalls = mutableListOf<Pair<Int, Int>>()
    for (index in accessorIndex + 2 until methodInstructions.size) {
        val instruction = methodInstructions[index]
        val reference = instruction.getReference<MethodReference>()
        if (
            reference != null &&
                reference.definingClass == ITERABLE_DESCRIPTOR &&
                reference.name == "iterator" &&
                reference.parameterTypes.isEmpty() &&
                reference.returnType == ITERATOR_DESCRIPTOR &&
                instruction.registersUsed.firstOrNull() in collectionRegisters
        ) {
            moveResultObjectRegister(index)?.let { iteratorResultRegister ->
                iteratorCalls += index to iteratorResultRegister
            }
        }
        updateObjectAliases(instruction, collectionRegisters)
    }
    if (iteratorCalls.size != 1) return false

    val (iteratorIndex, iteratorResultRegister) = iteratorCalls.single()
    val iteratorRegisters = linkedSetOf(iteratorResultRegister)
    val nextCalls = mutableListOf<Pair<Int, Int>>()
    for (index in iteratorIndex + 2 until methodInstructions.size) {
        val instruction = methodInstructions[index]
        val reference = instruction.getReference<MethodReference>()
        if (
            reference != null &&
                reference.definingClass == ITERATOR_DESCRIPTOR &&
                reference.name == "next" &&
                reference.parameterTypes.isEmpty() &&
                reference.returnType == "Ljava/lang/Object;" &&
                instruction.registersUsed.firstOrNull() in iteratorRegisters
        ) {
            moveResultObjectRegister(index)?.let { nextResultRegister ->
                nextCalls += index to nextResultRegister
            }
        }
        updateObjectAliases(instruction, iteratorRegisters)
    }
    if (nextCalls.size != 1) return false

    val (nextIndex, nextResultRegister) = nextCalls.single()
    val elementRegisters = linkedSetOf(nextResultRegister)
    val elementCastIndices = mutableListOf<Pair<Int, Int>>()
    for (index in nextIndex + 2 until methodInstructions.size) {
        val instruction = methodInstructions[index]
        if (instruction.opcode == Opcode.CHECK_CAST) {
            val castRegister = (instruction as? OneRegisterInstruction)?.registerA
            val castType = instruction.getReference<TypeReference>()?.type
            if (castRegister in elementRegisters && castType == elementType) {
                castRegister?.let { elementCastIndices += index to it }
            }
        }
        updateObjectAliases(instruction, elementRegisters)
    }
    if (elementCastIndices.size != 1) return false

    val (elementCastIndex, elementRegister) = elementCastIndices.single()
    val valueRegisters = linkedSetOf(elementRegister)
    val pendingEntryResults = mutableMapOf<Int, Int>()
    val consumerIndices = mutableListOf<Int>()
    for (index in elementCastIndex + 1 until methodInstructions.size) {
        val instruction = methodInstructions[index]
        val reference = instruction.getReference<MethodReference>()
        val arguments = instruction.registersUsed

        if (
            reference != null &&
                reference.definingClass == ARRAY_LIST_DESCRIPTOR &&
                reference.name == "add" &&
                reference.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;") &&
                reference.returnType == "Z" &&
                arguments.size == 2 &&
                arguments[1] in valueRegisters
        ) {
            consumerIndices += index
        }

        if (
            reference != null &&
                reference.returnType == elementType &&
                arguments.any { it in valueRegisters }
        ) {
            moveResultObjectRegister(index)?.let { resultRegister ->
                pendingEntryResults[index + 1] = resultRegister
            }
        }

        if (instruction.opcode == Opcode.MOVE_RESULT_OBJECT) {
            val resultRegister = (instruction as? OneRegisterInstruction)?.registerA
            if (resultRegister != null) {
                valueRegisters.remove(resultRegister)
                if (pendingEntryResults.remove(index) == resultRegister) {
                    valueRegisters += resultRegister
                }
            }
        } else {
            updateObjectAliases(instruction, valueRegisters)
        }
    }
    return consumerIndices.size == 1
}

private fun updateObjectAliases(
    instruction: Instruction,
    registers: MutableSet<Int>,
) {
    when (instruction.opcode) {
        Opcode.MOVE_OBJECT,
        Opcode.MOVE_OBJECT_FROM16,
        Opcode.MOVE_OBJECT_16,
        -> {
            val move = instruction as? TwoRegisterInstruction ?: return
            registers.remove(move.registerA)
            if (move.registerB in registers) registers += move.registerA
        }
        Opcode.MOVE_RESULT_OBJECT -> {
            (instruction as? OneRegisterInstruction)?.registerA?.let(registers::remove)
        }
        else -> Unit
    }
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

context(context: BytecodePatchContext)
private fun resolveNewXInlineMediaModels(): ResolvedNewXInlineMediaModels {
    val imageMatch = requireExactlyOne(
        "NewX inline image media model",
        InlineImageMediaModelFingerprint.scopedMatchAll(),
    )
    val gifMatch = requireExactlyOne(
        "NewX inline GIF media model",
        InlineGifMediaModelFingerprint.scopedMatchAll(),
    )
    val videoMatch = requireExactlyOne(
        "NewX inline video media model",
        InlineVideoMediaModelFingerprint.scopedMatchAll(),
    )
    val variantMatch = requireExactlyOne(
        "NewX inline media variant model",
        InlineMediaVariantModelFingerprint.scopedMatchAll(),
    )

    val imageDescriptor = imageMatch.originalClassDef.type
    val gifDescriptor = gifMatch.originalClassDef.type
    val videoDescriptor = videoMatch.originalClassDef.type
    val variantDescriptor = variantMatch.originalClassDef.type
    val imageUrlField = imageMatch.fieldForToStringLabel(", imageUrl=")
    val videoVariantsField = videoMatch.fieldForToStringLabel(", variants=")
    val gifVariantsField = resolveInlineGifVariantsField(
        gifMatch.originalMethod,
        gifDescriptor,
        videoVariantsField.type,
    )
    val variantUrlField = variantMatch.fieldForToStringLabel("MediaVariant(url=")
    val variantContentTypeField = resolveInlineMediaVariantContentTypeField(
        variantMatch.originalMethod,
        variantDescriptor,
        variantUrlField,
    )

    requireInlineMediaField(imageDescriptor, imageUrlField, "image URL", STRING_DESCRIPTOR)
    requireInlineMediaField(gifDescriptor, gifVariantsField, "GIF variants")
    requireInlineMediaField(videoDescriptor, videoVariantsField, "video variants")
    requireInlineMediaVariantAccessors(
        gifDescriptor,
        gifVariantsField,
        videoDescriptor,
        videoVariantsField,
    )
    requireInlineMediaField(variantDescriptor, variantUrlField, "variant URL", STRING_DESCRIPTOR)
    requireInlineMediaField(
        variantDescriptor,
        variantContentTypeField,
        "variant content type",
        STRING_DESCRIPTOR,
    )
    if (!context.isAssignableTo(gifVariantsField.type, LIST_DESCRIPTOR)) {
        throw PatchException("NewX inline GIF variants are not a List: $gifVariantsField")
    }
    if (!context.isAssignableTo(videoVariantsField.type, LIST_DESCRIPTOR)) {
        throw PatchException("NewX inline video variants are not a List: $videoVariantsField")
    }

    val imageHierarchy = superclassChain(imageDescriptor)
    val sharedMediaBases = imageHierarchy.filter { candidate ->
        candidate != OBJECT_DESCRIPTOR &&
            context.isAssignableTo(gifDescriptor, candidate) &&
            context.isAssignableTo(videoDescriptor, candidate)
    }
    val mostSpecificSharedBases = sharedMediaBases.filter { candidate ->
        sharedMediaBases.none { other ->
            other != candidate && context.isAssignableTo(other, candidate)
        }
    }
    val sharedMediaBase = requireExactlyOne(
        "NewX inline image/GIF/video common media base",
        mostSpecificSharedBases,
    )

    return ResolvedNewXInlineMediaModels(
        sharedMediaBaseDescriptor = sharedMediaBase,
        imageMediaDescriptor = imageDescriptor,
        imageUrlField = imageUrlField,
        gifMediaDescriptor = gifDescriptor,
        gifVariantsField = gifVariantsField,
        videoMediaDescriptor = videoDescriptor,
        videoVariantsField = videoVariantsField,
        variantDescriptor = variantDescriptor,
        variantUrlField = variantUrlField,
        variantContentTypeField = variantContentTypeField,
    )
}

/**
 * The GIF `, variants=` label is another terminal packed-helper argument, so the shared label
 * resolver cannot connect it to the following field read. Resolve the video slot first; the GIF
 * toString must then contain exactly one instance read of that same collection type. Its exact
 * field accessor must share the video's getter signature through a common model interface.
 */
internal fun resolveInlineGifVariantsField(
    gifToString: Method,
    gifDescriptor: String,
    variantsFieldType: String,
): FieldReference {
    if (gifToString.definingClass != gifDescriptor) {
        throw PatchException("NewX GIF media toString owner changed: $gifToString / $gifDescriptor")
    }
    val instructions = gifToString.implementation?.instructions?.toList()
        ?: throw PatchException("NewX GIF media toString has no implementation: $gifToString")
    requireExactlyOne(
        "NewX GIF media toString variants label",
        instructions.indices.filter { index ->
            instructions[index].getReference<StringReference>()?.string == ", variants="
        },
    )
    val fieldReads = instructions.mapIndexedNotNull { index, instruction ->
        val field = instruction.getReference<FieldReference>() ?: return@mapIndexedNotNull null
        if (
            instruction.opcode == Opcode.IGET_OBJECT &&
                field.definingClass == gifDescriptor && field.type == variantsFieldType
        ) {
            index to field
        } else {
            null
        }
    }
    return requireExactlyOne(
        "NewX GIF variants field read matching the video variants slot type",
        fieldReads,
    ).second
}

context(context: BytecodePatchContext)
private fun requireInlineMediaVariantAccessors(
    gifDescriptor: String,
    gifVariantsField: FieldReference,
    videoDescriptor: String,
    videoVariantsField: FieldReference,
) {
    val gifGetter = requireDirectInlineVariantsGetter(gifDescriptor, gifVariantsField)
    val videoGetter = requireDirectInlineVariantsGetter(videoDescriptor, videoVariantsField)
    if (!sameMethodSignature(gifGetter, videoGetter)) {
        throw PatchException(
            "NewX GIF/video variants getters disagree: $gifGetter / $videoGetter",
        )
    }
    val gifInterfaces = modelInterfaceHierarchy(gifDescriptor)
    val videoInterfaces = modelInterfaceHierarchy(videoDescriptor)
    val commonAccessorInterfaces = gifInterfaces.intersect(videoInterfaces).filter { descriptor ->
        val interfaceClass = context.classDefByOrNull(descriptor) ?: return@filter false
        AccessFlags.INTERFACE.isSet(interfaceClass.accessFlags) &&
            interfaceClass.methods.any { method ->
                AccessFlags.ABSTRACT.isSet(method.accessFlags) && sameMethodSignature(method, gifGetter)
            }
    }
    requireExactlyOne(
        "NewX GIF/video variants getter shared interface",
        commonAccessorInterfaces,
    )
}

context(context: BytecodePatchContext)
private fun requireDirectInlineVariantsGetter(
    modelDescriptor: String,
    variantsField: FieldReference,
): Method {
    val modelClass = context.classDefByOrNull(modelDescriptor)
        ?: throw PatchException("NewX inline media model class was not found: $modelDescriptor")
    val getters = modelClass.methods.filter { method ->
        if (
            AccessFlags.STATIC.isSet(method.accessFlags) ||
                method.parameterTypes.isNotEmpty() || method.returnType != variantsField.type
        ) {
            return@filter false
        }
        val code = method.implementation?.instructions?.toList() ?: return@filter false
        if (code.size != 2 || code[0].opcode != Opcode.IGET_OBJECT || code[1].opcode != Opcode.RETURN_OBJECT) {
            return@filter false
        }
        val read = code[0] as? TwoRegisterInstruction ?: return@filter false
        val returned = code[1] as? OneRegisterInstruction ?: return@filter false
        val registerCount = method.implementation?.registerCount ?: return@filter false
        code[0].getReference<FieldReference>()?.toString() == variantsField.toString() &&
            read.registerB == registerCount - 1 && returned.registerA == read.registerA
    }
    return requireExactlyOne("NewX inline media variants getter for $variantsField", getters)
}

context(context: BytecodePatchContext)
private fun modelInterfaceHierarchy(modelDescriptor: String): Set<String> {
    val interfaces = linkedSetOf<String>()
    val pending = mutableListOf(modelDescriptor)
    val visited = mutableSetOf<String>()
    while (pending.isNotEmpty()) {
        val descriptor = pending.removeAt(pending.lastIndex)
        if (!visited.add(descriptor)) continue
        val modelClass = context.classDefByOrNull(descriptor)
            ?: throw PatchException("NewX inline media hierarchy class was not found: $descriptor")
        modelClass.interfaces.forEach { interfaceDescriptor ->
            if (interfaces.add(interfaceDescriptor)) pending += interfaceDescriptor
        }
        modelClass.superclass?.takeIf { it != OBJECT_DESCRIPTOR }?.let(pending::add)
    }
    return interfaces
}

private fun sameMethodSignature(first: Method, second: Method): Boolean =
    first.name == second.name &&
        first.parameterTypes.map(CharSequence::toString) == second.parameterTypes.map(CharSequence::toString) &&
        first.returnType == second.returnType

/**
 * Resolves the remaining media-variant String field from its validated model schema. In current
 * targets the `, contentType=` label is the final argument to a packed builder helper, while the
 * content type value is appended by a later helper call; the shared label resolver cannot connect
 * those calls. The URL still comes from that shared resolver, and only the verified two-String,
 * boxed-bitrate instance schema makes its complement the content type without relying on order or
 * obfuscated field names.
 */
internal fun resolveInlineMediaVariantContentTypeField(
    variantToString: Method,
    variantDescriptor: String,
    variantUrlField: FieldReference,
): FieldReference {
    val instructions = variantToString.implementation?.instructions?.toList()
        ?: throw PatchException("NewX media variant toString has no implementation: $variantToString")
    listOf("MediaVariant(url=", ", bitRate=", ", contentType=").forEach { label ->
        requireExactlyOne(
            "NewX media variant toString label '$label'",
            instructions.indices.filter { index ->
                instructions[index].getReference<StringReference>()?.string == label
            },
        )
    }
    if (variantToString.definingClass != variantDescriptor) {
        throw PatchException(
            "NewX media variant toString owner changed: $variantToString / $variantDescriptor",
        )
    }
    if (variantUrlField.definingClass != variantDescriptor || variantUrlField.type != STRING_DESCRIPTOR) {
        throw PatchException("NewX media variant URL field changed: $variantUrlField")
    }

    val modelFieldReads = instructions.mapNotNull { instruction ->
        val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
        field.takeIf { it.definingClass == variantDescriptor }?.let { instruction to it }
    }
    if (modelFieldReads.any { (instruction, _) -> instruction.opcode != Opcode.IGET_OBJECT }) {
        throw PatchException(
            "NewX media variant toString has a non-object instance field read: $variantToString",
        )
    }
    val modelFields = modelFieldReads.map { it.second }.distinctBy(FieldReference::toString)
    val stringFields = modelFields.filter { it.type == STRING_DESCRIPTOR }
    val bitrateFields = modelFields.filter { it.type == "Ljava/lang/Integer;" }
    if (modelFields.size != 3 || stringFields.size != 2 || bitrateFields.size != 1) {
        throw PatchException(
            "NewX media variant toString schema changed; expected two String fields and one boxed " +
                "Integer field, found ${modelFields.joinToString()}: $variantToString",
        )
    }
    if (variantUrlField !in stringFields) {
        throw PatchException("NewX media variant URL is not a toString String field: $variantUrlField")
    }
    val contentTypeCandidates = stringFields.filter { it.toString() != variantUrlField.toString() }
    return requireExactlyOne("NewX media variant content type field", contentTypeCandidates)
}

context(context: BytecodePatchContext)
private fun requireInlineMediaField(
    modelDescriptor: String,
    field: FieldReference,
    semanticName: String,
    expectedType: String? = null,
) {
    if (expectedType != null && field.type != expectedType) {
        throw PatchException("NewX inline $semanticName field has type ${field.type}: $field")
    }
    if (!context.isAssignableTo(modelDescriptor, field.definingClass)) {
        throw PatchException(
            "NewX inline $semanticName field owner ${field.definingClass} is outside " +
                "$modelDescriptor hierarchy",
        )
    }
    val declaringClass = context.classDefByOrNull(field.definingClass)
        ?: throw PatchException("NewX inline $semanticName field owner was not found: $field")
    val declaration = declaringClass.fields.singleOrNull { it.toString() == field.toString() }
        ?: throw PatchException("NewX inline $semanticName field declaration was not found: $field")
    if (AccessFlags.STATIC.isSet(declaration.accessFlags)) {
        throw PatchException("NewX inline $semanticName field is static: $field")
    }
    if (!AccessFlags.PUBLIC.isSet(declaration.accessFlags)) {
        throw PatchException("NewX inline $semanticName field is not public: $field")
    }
    context.mutableClassDefBy(field.definingClass).requirePublicFields(listOf(field))
}

context(context: BytecodePatchContext)
private fun superclassChain(descriptor: String): List<String> {
    val result = mutableListOf<String>()
    var current: String? = descriptor
    while (current != null && current != OBJECT_DESCRIPTOR) {
        if (current in result) {
            throw PatchException("NewX inline media hierarchy contains a cycle at $current")
        }
        result += current
        current = context.classDefByOrNull(current)?.superclass
            ?: if (result.last() == descriptor) {
                throw PatchException("NewX inline media model class was not found: $descriptor")
            } else {
                null
            }
    }
    return result
}

context(context: BytecodePatchContext)
private fun resolveInlineActionKindLayoutLambda(
    entryModels: ResolvedNewXInlineActionModels,
): Method =
    resolveInlineActionKindLayoutMethod(
        Fingerprint(
            definingClass = INLINE_ACTION_BAR_SCOPE,
            name = "invoke",
            returnType = "Ljava/lang/Object;",
            // The lambda may acquire a layout receiver. Consumers depend on the entry field
            // read and the uniquely validated kind construction below, not its erased arity.
            filters = listOf(
                fieldAccess(
                    opcode = Opcode.IGET_OBJECT,
                    reference = entryModels.inlineActionTypeField,
                ),
            ),
        ).scopedMatchAll().map { it.originalMethod },
        entryModels.inlineActionTypeField,
    )

internal fun resolveInlineActionKindLayoutMethod(
    methods: Collection<Method>,
    actionTypeField: FieldReference,
): Method = requireExactlyOne(
    "NewX inline-action kind layout lambda",
    methods.filter { method ->
        method.definingClass.startsWith(INLINE_ACTION_BAR_SCOPE) &&
            method.name == "invoke" && method.returnType == "Ljava/lang/Object;" &&
            method.implementation?.instructions?.any { instruction ->
                instruction.opcode == Opcode.IGET_OBJECT &&
                    instruction.getReference<FieldReference>()?.toString() == actionTypeField.toString()
            } == true
    },
)

context(context: BytecodePatchContext)
private fun resolveInlineActionKindOverride(
    entryModels: ResolvedNewXInlineActionModels,
): ResolvedNewXInlineActionKindOverride? {
    // The download action is injected with the TwitterShare carrier so it keeps the native share
    // slot's icon/click wiring. 12.29's action-bar layout classifies TwitterShare as Countless
    // because its kind switch has no entry for it, which reserves a wider slot than the icon-only
    // layout. Resolve the shared layout lambda and the kind model it constructs so the patch can
    // rewrite only the injected action's kind. The action-type field read grounds the lambda as
    // the one that consumes the inline-action entries.
    //
    // Older targets keep a boolean-only kind model with no IconOnly enum to rewrite:
    // 12.27/12.28-alpha.01 use `(Z)`, 12.28-alpha.04/prod use `(ZZZ)`. Those releases worked
    // without the override, so a validated legacy shape returns null and the patch skips the
    // rewrite. Any other shape (no recognized kind constructor, or an ambiguous enum match)
    // still fails closed.
    val layoutLambda = resolveInlineActionKindLayoutLambda(entryModels)

    val kindConstructor = resolveInlineActionEnumKindConstructor(layoutLambda)
        ?: run {
            // Boolean-only kind model: no IconOnly enum to rewrite. Validated shapes only.
            resolveInlineActionBooleanKindConstructor(layoutLambda)
            return null
        }
    val kindEnumDescriptor = kindConstructor.parameterTypes.last().toString()
    val kindEnumClass = context.classDefByOrNull(kindEnumDescriptor)
        ?: throw PatchException("NewX inline-action kind enum was not found: $kindEnumDescriptor")
    if (!AccessFlags.ENUM.isSet(kindEnumClass.accessFlags)) {
        throw PatchException("NewX inline-action kind type is not an enum: $kindEnumClass")
    }
    val iconOnlyField = requireExactlyOne(
        "NewX inline-action IconOnly kind",
        kindEnumClass.fields.filter { field ->
            field.name == "IconOnly" &&
                field.type == kindEnumDescriptor &&
                AccessFlags.STATIC.isSet(field.accessFlags)
        },
    )

    return ResolvedNewXInlineActionKindOverride(
        layoutLambda = layoutLambda,
        kindModelConstructor = kindConstructor,
        iconOnlyField = iconOnlyField,
    )
}

context(context: BytecodePatchContext)
private fun resolveInlineActionEnumKindConstructor(
    layoutLambda: Method,
): MethodReference? {
    val kindConstructors = layoutLambda.implementation
        ?.instructions
        ?.toList()
        .orEmpty()
        .mapIndexedNotNull { index, instruction ->
            index.takeIf {
                isInlineActionKindModelConstructor(instruction) { type ->
                    context.classDefByOrNull(type)?.let { kindClass ->
                        AccessFlags.ENUM.isSet(kindClass.accessFlags)
                    } == true
                }
            }?.let { constructorIndex -> constructorIndex to instruction }
        }
    val candidate = requireAtMostOne(
        "NewX inline-action kind model constructor",
        kindConstructors,
    ) ?: return null
    return candidate.second.getReference<MethodReference>()
        ?: throw PatchException(
            "NewX inline-action kind model constructor has no reference: ${candidate.second}",
        )
}

context(context: BytecodePatchContext)
private fun resolveInlineActionBooleanKindConstructor(
    layoutLambda: Method,
): MethodReference {
    val legacyConstructors = layoutLambda.implementation
        ?.instructions
        ?.toList()
        .orEmpty()
        .mapIndexedNotNull { index, instruction ->
            index.takeIf { isLegacyInlineActionKindModelConstructor(instruction) }
        }
    val constructorIndex = requireExactlyOne(
        "NewX legacy inline-action boolean kind model constructor",
        legacyConstructors,
    )
    return layoutLambda.implementation?.instructions?.toList()?.get(constructorIndex)
        ?.getReference<MethodReference>()
        ?: throw PatchException(
            "NewX legacy inline-action boolean kind model constructor has no reference: ${layoutLambda}",
        )
}

/**
 * A kind model constructor takes exactly three parameters and its final parameter is the kind
 * enum. The final parameter is a reference type, so it is never wide; the intermediate layout
 * constructors this must not match (for example `Modifier` padding) have different arities.
 */
internal fun isInlineActionKindModelConstructor(
    instruction: Instruction,
    isEnumType: (String) -> Boolean,
): Boolean {
    if (instruction.opcode != Opcode.INVOKE_DIRECT) return false
    val reference = instruction.getReference<MethodReference>() ?: return false
    if (reference.name != "<init>") return false
    val parameterTypes = reference.parameterTypes.map(CharSequence::toString)
    if (parameterTypes.size != 3) return false
    val kindType = parameterTypes.last()
    if (kindType == "J" || kindType == "D") return false
    return isEnumType(kindType)
}

/**
 * Validated legacy shapes of the same kind model before 12.29 introduced the packed-slot enum:
 * `(Z)` on 12.27/12.28-alpha.01 and `(ZZZ)` on 12.28-alpha.04/prod. All parameters are boolean,
 * so the `Modifier` padding constructor (`FFFF`) and the enum model (`ZZ` + reference) never
 * match. Any other arity fails closed in the resolver instead of silently skipping.
 */
internal fun isLegacyInlineActionKindModelConstructor(instruction: Instruction): Boolean {
    if (instruction.opcode != Opcode.INVOKE_DIRECT) return false
    val reference = instruction.getReference<MethodReference>() ?: return false
    if (reference.name != "<init>") return false
    val parameterTypes = reference.parameterTypes.map(CharSequence::toString)
    if (parameterTypes.size != 1 && parameterTypes.size != 3) return false
    return parameterTypes.all { it == "Z" }
}
