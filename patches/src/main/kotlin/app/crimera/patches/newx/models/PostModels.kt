package app.crimera.patches.newx.models

import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import java.util.WeakHashMap

private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val INLINE_ACTION_BAR_SCOPE = "Lcom/x/inlineactionbar/"
private const val ITERABLE_DESCRIPTOR = "Ljava/lang/Iterable;"
private const val ITERATOR_DESCRIPTOR = "Ljava/util/Iterator;"
private const val ARRAY_LIST_DESCRIPTOR = "Ljava/util/ArrayList;"

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
    val inlineActionModels = resolvedNewXInlineActionModels()
    val canonicalPostClass = context.classDefByOrNull(postModels.canonicalPostDescriptor)
        ?: throw PatchException(
            "NewX canonical-post class was not found: ${postModels.canonicalPostDescriptor}",
        )
    val canonicalPostInterfaceDescriptor = canonicalPostClass.interfaces.singleOrNull()?.toString()
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
 * BETA PATH: the canonical-post accessor returns the collection of inline-action entries. The
 * presenter iterates that collection, casts each element to the resolved model, optionally maps
 * it through a copy/factory method, and adds the resulting entry to an ArrayList. Keep this
 * separate from the direct-entry path above because the collection descriptor is not the model
 * descriptor (for example, immutable `b` versus `k4` in unified 12.22).
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
