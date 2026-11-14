package app.crimera.patches.newx.timeline.postfilter

import app.crimera.patches.newx.models.ModelFieldAccessor
import app.crimera.patches.newx.models.ResolvedNewXPostModels
import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.models.newXPostModelResolutionPatch
import app.crimera.patches.newx.models.newXTimelineModelAdapterPatch
import app.crimera.patches.newx.models.requirePublicFields
import app.crimera.patches.newx.models.requireSingle
import app.crimera.patches.newx.models.resolveFieldAccessor
import app.crimera.patches.newx.models.resolvedNewXPostModels
import app.crimera.patches.newx.models.resolvedNewXTimelineModels
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.crimera.bytecode.Block
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.destinationRegisterOrNull
import app.crimera.patches.common.valueReachesRegister
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.numberOfParameterRegisters
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.VariableRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val INTEGER_DESCRIPTOR = "I"
private const val LONG_OBJECT_DESCRIPTOR = "Ljava/lang/Long;"
private const val OBJECT_TO_STRING_DESCRIPTOR = "Ljava/lang/Object;->toString()Ljava/lang/String;"

/** Release stubs the timeline filter declares for the post-filter bridges. */
private const val POST_TEXT_BRIDGE = "getPostText"
private const val REPLIED_POST_ID_BRIDGE = "getPostRepliedPostId"
private const val POST_MENTIONS_BRIDGE = "getPostMentions"
private const val MENTION_START_IDX_BRIDGE = "getMentionStartIdx"
private const val MENTION_END_IDX_BRIDGE = "getMentionEndIdx"
private const val MENTION_SCREEN_NAME_BRIDGE = "getMentionScreenName"
private const val POST_AUTHOR_SCREEN_NAME_BRIDGE = "getPostAuthorScreenName"
private const val POST_AUTHOR_VERIFIED_TYPE_BRIDGE = "getPostAuthorVerifiedType"
private const val POST_AUTHOR_ID_BRIDGE = "getPostAuthorId"

private const val REPLIED_POST_ID_NO_CONTEXTUAL_RESULT_LABEL =
    "piko_newx_post_replied_id_no_contextual_result"
private const val POST_MENTIONS_NO_CONTEXTUAL_RESULT_LABEL =
    "piko_newx_post_mentions_no_contextual_result"
private const val POST_MENTIONS_NULL_LABEL = "piko_newx_post_mentions_null"
private const val POST_AUTHOR_NO_CONTEXTUAL_RESULT_LABEL =
    "piko_newx_post_author_no_contextual_result"
private const val POST_AUTHOR_NULL_LABEL = "piko_newx_post_author_null"
private const val POST_AUTHOR_VERIFIED_TYPE_NO_CONTEXTUAL_RESULT_LABEL =
    "piko_newx_post_author_verified_type_no_contextual_result"
private const val POST_AUTHOR_VERIFIED_TYPE_NULL_LABEL =
    "piko_newx_post_author_verified_type_null"
private const val POST_AUTHOR_ID_NO_CONTEXTUAL_RESULT_LABEL =
    "piko_newx_post_author_id_no_contextual_result"
private const val POST_AUTHOR_ID_NULL_LABEL = "piko_newx_post_author_id_null"

private object CanonicalPostModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("CanonicalPost(id=")),
)

private object PostEntityListModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/text/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("PostEntityList(mentions=")),
)

private object MentionEntityModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/text/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("MentionEntity(userId=")),
)

private object MinimalUserModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("MinimalUser(id=")),
)

/** Post text/mention/author models resolved for the post-filter keyword bridges. */
private data class ResolvedPostTextModels(
    val postResultField: FieldReference,
    val contextualPostDescriptor: String,
    val contextualCanonicalPost: ModelFieldAccessor,
    val canonicalPostDescriptor: String,
    val repliedPostIdField: FieldReference,
    val entityListField: FieldReference,
    val mentionsField: FieldReference,
    val mentionDescriptor: String,
    val mentionStartIdxField: FieldReference,
    val mentionEndIdxField: FieldReference,
    val mentionScreenNameField: FieldReference,
    val authorField: FieldReference,
    val authorScreenNameGetter: MethodReference,
    val authorVerifiedTypeGetter: MethodReference,
    val authorIdGetter: MethodReference,
)

internal val newXTimelineTextModelAdapterPatch =
    bytecodePatch(default = false) {
        dependsOn(newXTimelineModelAdapterPatch, newXPostModelResolutionPatch)

        execute {
            val timelineModels = resolvedNewXTimelineModels()
            val postModels = resolvedNewXPostModels()
            patchPostTextBridges(
                postDescriptor = timelineModels.postDescriptor,
                textGetter = timelineModels.postTextGetter,
                postTextModels = resolvePostTextModels(
                    postDescriptor = timelineModels.postDescriptor,
                    postResultField = timelineModels.postResultField,
                    postModels = postModels,
                ),
            )
        }
    }

context(context: BytecodePatchContext)
private fun resolvePostTextModels(
    postDescriptor: String,
    postResultField: FieldReference,
    postModels: ResolvedNewXPostModels,
): ResolvedPostTextModels {
    val postClass = context.mutableClassDefBy(postDescriptor)
    val contextualPostClass = context.mutableClassDefBy(postModels.contextualPostDescriptor)
    val contextualCanonicalPostAccessor =
        contextualPostClass.resolveFieldAccessor(
            postModels.contextualCanonicalPostField,
            "contextual canonical post",
        )
    postClass.requirePublicFields(listOf(postResultField))

    val canonicalPostMatch =
        CanonicalPostModelFingerprint.requireSingle("canonical post model")
    val canonicalPostDescriptor = canonicalPostMatch.originalClassDef.type
    if (canonicalPostDescriptor != postModels.canonicalPostDescriptor) {
        throw PatchException(
            "NewX canonical-post resolution disagrees with the shared post-model resolver: " +
                "timelineText=$canonicalPostDescriptor, shared=${postModels.canonicalPostDescriptor}",
        )
    }
    val entityListField = canonicalPostMatch.fieldForToStringLabel(", entityList=")
    val authorField = canonicalPostMatch.fieldForToStringLabel(", author=")
    val repliedPostIdField = canonicalPostMatch.fieldForToStringLabel(", repliedPostId=")
    repliedPostIdField.requireType(LONG_OBJECT_DESCRIPTOR, "canonical-post replied-post ID")
    val canonicalPostClass = context.mutableClassDefBy(canonicalPostDescriptor)
    canonicalPostClass.requirePublicFields(listOf(entityListField, authorField, repliedPostIdField))

    val entityListMatch =
        PostEntityListModelFingerprint.requireSingle("post-entity-list model")
    if (entityListMatch.originalClassDef.type != entityListField.type) {
        throw PatchException(
            "NewX canonical-post entity-list field does not point to the matched model: " +
                "field=$entityListField, model=${entityListMatch.originalClassDef.type}",
        )
    }
    val entityListFieldClass = context.mutableClassDefBy(entityListField.type)
    val mentionsField =
        entityListMatch.originalMethod.fieldForHelperToStringValue("PostEntityList(mentions=")
    mentionsField.requireType(LIST_DESCRIPTOR, "post-entity-list mentions")
    entityListFieldClass.requirePublicFields(listOf(mentionsField))

    val mentionMatch = MentionEntityModelFingerprint.requireSingle("mention-entity model")
    val mentionDescriptor = mentionMatch.originalClassDef.type
    val mentionStartIdxField = mentionMatch.fieldForToStringLabel(", startIdx=")
    val mentionEndIdxField = mentionMatch.originalMethod.fieldForToStringLabelThroughAppendHelper(", endIdx=") {
        context.classDefByOrNull(it)
    } ?: mentionMatch.fieldForToStringLabel(", endIdx=")
    val mentionScreenNameField = mentionMatch.originalMethod.fieldForToStringLabelThroughAppendHelper(", screenName=") {
        context.classDefByOrNull(it)
    } ?: mentionMatch.fieldForToStringLabel(", screenName=")
    mentionStartIdxField.requireType(INTEGER_DESCRIPTOR, "mention start index")
    mentionEndIdxField.requireType(INTEGER_DESCRIPTOR, "mention end index")
    mentionScreenNameField.requireType(STRING_DESCRIPTOR, "mention screen name")
    context.mutableClassDefBy(mentionDescriptor).requirePublicFields(
        listOf(mentionStartIdxField, mentionEndIdxField, mentionScreenNameField),
    )

    val minimalUserMatch = MinimalUserModelFingerprint.requireSingle("minimal-user model")
    val authorScreenNameGetter =
        resolveAuthorFieldGetter(
            authorDescriptor = authorField.type,
            minimalUserMatch = minimalUserMatch,
            fieldLabel = ", screenName=",
            expectedType = STRING_DESCRIPTOR,
            semanticName = "screen-name",
        )
    val authorVerifiedTypeGetter =
        resolveAuthorFieldGetter(
            authorDescriptor = authorField.type,
            minimalUserMatch = minimalUserMatch,
            fieldLabel = ", verifiedType=",
            expectedType = null,
            semanticName = "verified-type",
        )
    val authorIdGetter =
        resolveAuthorMethodGetter(
            authorDescriptor = authorField.type,
            name = "getId",
            semanticName = "author ID",
        )

    return ResolvedPostTextModels(
        postResultField = postResultField,
        contextualPostDescriptor = postModels.contextualPostDescriptor,
        contextualCanonicalPost = contextualCanonicalPostAccessor,
        canonicalPostDescriptor = canonicalPostDescriptor,
        repliedPostIdField = repliedPostIdField,
        entityListField = entityListField,
        mentionsField = mentionsField,
        mentionDescriptor = mentionDescriptor,
        mentionStartIdxField = mentionStartIdxField,
        mentionEndIdxField = mentionEndIdxField,
        mentionScreenNameField = mentionScreenNameField,
        authorField = authorField,
        authorScreenNameGetter = authorScreenNameGetter,
        authorVerifiedTypeGetter = authorVerifiedTypeGetter,
        authorIdGetter = authorIdGetter,
    )
}

/**
 * Handles model toString helpers that append the builder's next value inside a static formatter.
 * The label-to-value association is proven from the formatter's StringBuilder append order and
 * the call-site field register; no obfuscated helper identity is used.
 */
internal fun Method.fieldForToStringLabelThroughAppendHelper(
    label: String,
    classByType: (String) -> com.android.tools.smali.dexlib2.iface.ClassDef?,
): FieldReference? {
    val methodImplementation = implementation ?: return null
    val code = methodImplementation.instructions.toList()
    // Append order is meaningful only on a single straight-line formatting path.
    if (code.any { it is com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction } ||
        methodImplementation.tryBlocks.isNotEmpty()
    ) throw PatchException("NewX model formatter label proof requires a straight-line body: $this")
    val labelIndices = code.indices.filter { index ->
        code[index].getReference<StringReference>()?.string == label
    }
    if (labelIndices.isEmpty()) return null
    val labelIndex = requireExactlyOne("NewX model label '$label' in $this", labelIndices)
    val labelRegister = (code[labelIndex] as? OneRegisterInstruction)?.registerA
        ?: throw PatchException("NewX model label '$label' has no register in $this")

    val directLabelAppends = code.indices.filter { index ->
        if (index <= labelIndex) return@filter false
        val reference = code[index].getReference<MethodReference>() ?: return@filter false
        reference.isStringBuilderAppend("Ljava/lang/String;") &&
            code[index].invokedArgumentRegister(reference, 0)?.let { argument ->
                code.valueReachesRegister(labelIndex, labelRegister, index, argument)
            } == true
    }
    val formatterCalls = code.indices.filter { index ->
        if (index <= labelIndex) return@filter false
        val instruction = code[index]
        if (instruction.opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)) return@filter false
        val reference = instruction.getReference<MethodReference>() ?: return@filter false
        reference.returnType == STRING_DESCRIPTOR &&
            reference.parameterTypes.count { it.toString() == "Ljava/lang/StringBuilder;" } == 1
    }

    val candidateProofs = mutableListOf<String>()
    val fields = formatterCalls.flatMap { formatterIndex ->
        val reference = code[formatterIndex].getReference<MethodReference>()!!
        val formatterClass = classByType(reference.definingClass)
            ?: throw PatchException("NewX model formatter owner was not found: ${reference.definingClass}")
        val formatter = requireExactlyOne(
            "NewX model formatter $reference",
            formatterClass.methods.filter { it.toString() == reference.toString() },
        )
        if (!AccessFlags.STATIC.isSet(formatter.accessFlags)) {
            throw PatchException("NewX model formatter is not static: $formatter")
        }
        val builderParameter = requireExactlyOne(
            "NewX StringBuilder parameter in $formatter",
            reference.parameterTypes.indices.filter { reference.parameterTypes[it].toString() == "Ljava/lang/StringBuilder;" },
        )
        val builderArgument = code[formatterIndex].invokedArgumentRegister(reference, builderParameter)
            ?: throw PatchException("NewX model formatter has no builder argument: $formatter")
        val labelArgumentCandidate = requireAtMostOne("NewX formatter argument for '$label' in $this",
            reference.parameterTypes.indices.filter { parameter ->
            reference.parameterTypes[parameter].toString() == STRING_DESCRIPTOR &&
                code[formatterIndex].invokedArgumentRegister(reference, parameter)?.let { argument ->
                    code.valueReachesRegister(labelIndex, labelRegister, formatterIndex, argument)
                } == true
        })
        val directAppend = requireAtMostOne("NewX direct append for '$label' in $this", directLabelAppends.filter { appendIndex ->
            if (appendIndex >= formatterIndex) return@filter false
            val append = code[appendIndex]
            val builder = append.registersUsed.firstOrNull() ?: return@filter false
                builder == builderArgument &&
                code.subList(appendIndex + 1, formatterIndex).none { it.destinationRegisterOrNull() == builder }
        })
        val labelArgument = labelArgumentCandidate.takeIf { directAppend == null }
        if (labelArgument == null && directAppend == null) return@flatMap emptyList()
        candidateProofs.add("call=$formatterIndex labelArgument=$labelArgument directAppend=$directAppend")

        val formatterCode = formatter.implementation?.instructions?.toList()
            ?: throw PatchException("NewX model formatter has no body: $formatter")
        if (formatterCode.any { it is com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction } ||
            formatter.implementation!!.tryBlocks.isNotEmpty()
        ) throw PatchException("NewX append-order proof requires a straight-line formatter: $formatter")
        val builderRegister = formatter.parameterRegister(builderParameter)
            ?: throw PatchException("NewX model formatter builder register is unavailable: $formatter")
        val appends = formatterCode.indices.filter { index ->
            val append = formatterCode[index].getReference<MethodReference>() ?: return@filter false
            append.definingClass == "Ljava/lang/StringBuilder;" && append.name == "append" &&
                append.parameterTypes.size == 1 && append.returnType == "Ljava/lang/StringBuilder;" &&
                formatterCode[index].registersUsed.firstOrNull()?.let { receiver ->
                    formatterCode.valueReachesRegister(-1, builderRegister, index, receiver)
                } == true
        }
        if (appends.isEmpty()) {
            throw PatchException(
                "NewX model formatter has no builder append for '$label' in $formatter " +
                    "(builder=v$builderRegister)",
            )
        }

        val labelAppend = if (labelArgument != null) {
            val labelParameterRegister = formatter.parameterRegister(labelArgument)
                ?: throw PatchException("NewX model formatter label register is unavailable: $formatter")
            requireExactlyOne(
                "NewX formatter append for model label '$label' in $formatter",
                appends.filter { appendIndex ->
                    val append = formatterCode[appendIndex].getReference<MethodReference>()!!
                    append.parameterTypes.single().toString() == STRING_DESCRIPTOR &&
                        formatterCode[appendIndex].invokedArgumentRegister(append, 0)?.let { argument ->
                            formatterCode.valueReachesRegister(-1, labelParameterRegister, appendIndex, argument)
                        } == true
                },
            )
        } else {
            appends.first()
        }
        val valueAppend = if (labelArgument == null) {
            labelAppend
        } else {
            appends.filter { it > labelAppend }.minOrNull()
                ?: throw PatchException("NewX formatter has no value append after '$label' in $formatter")
        }
        val valueReference = formatterCode[valueAppend].getReference<MethodReference>()!!
        val valueRegister = formatterCode[valueAppend].invokedArgumentRegister(valueReference, 0)
            ?: throw PatchException("NewX formatter value append has no value register: $formatter")
        val valueParameter = formatter.parameterIndexForRegister(valueRegister)
            ?: throw PatchException("NewX formatter appends a non-parameter value for '$label': $formatter")
        val callArgument = code[formatterIndex].invokedArgumentRegister(reference, valueParameter)
            ?: throw PatchException("NewX formatter value argument is unavailable for '$label': $formatter")
        val startIndex = if (directAppend != null) directAppend + 1 else labelIndex + 1
        val fieldIndexes = code.indices.filter { fieldIndex ->
            if (fieldIndex !in startIndex until formatterIndex) return@filter false
            val instruction = code[fieldIndex]
            if (instruction.opcode !in setOf(Opcode.IGET, Opcode.IGET_WIDE, Opcode.IGET_OBJECT,
                    Opcode.IGET_BOOLEAN, Opcode.IGET_BYTE, Opcode.IGET_CHAR, Opcode.IGET_SHORT)
            ) return@filter false
            val field = instruction.getReference<FieldReference>() ?: return@filter false
            val registers = instruction as? TwoRegisterInstruction ?: return@filter false
            val destination = (instruction as? OneRegisterInstruction)?.registerA ?: return@filter false
            val receiverRegister = methodImplementation.registerCount -
                parameterTypes.sumOf { parameter -> if (parameter.toString() in setOf("J", "D")) 2 else 1 } - 1
            field.definingClass == definingClass &&
                code.formatterFieldValueReaches(fieldIndex, destination, formatterIndex, callArgument) &&
                field.type == reference.parameterTypes[valueParameter].toString() &&
                code.valueReachesRegister(-1, receiverRegister, fieldIndex, registers.registerB)
        }
        candidateProofs[candidateProofs.lastIndex] +=
            " valueParameter=$valueParameter callArgument=$callArgument fieldIndexes=$fieldIndexes"
        fieldIndexes.mapNotNull { fieldIndex -> code[fieldIndex].getReference<FieldReference>() }
    }
    if (fields.isEmpty()) {
        if (candidateProofs.isNotEmpty()) {
            throw PatchException(
                "NewX model formatter could not prove a field after '$label' in $this: $candidateProofs",
            )
        }
        return null
    }
    return requireExactlyOne("NewX model field for '$label' in $this", fields.distinctBy(FieldReference::toString))
}

/** Formatting values may move between registers, but must not be overwritten before append. */
private fun List<Instruction>.formatterFieldValueReaches(
    fieldIndex: Int,
    fieldRegister: Int,
    callIndex: Int,
    argumentRegister: Int,
): Boolean {
    val aliases = mutableSetOf(fieldRegister)
    val moves = setOf(Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16,
        Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)
    for (index in fieldIndex + 1 until callIndex) {
        val instruction = this[index]
        if (instruction.opcode == Opcode.CHECK_CAST) continue
        val sourceIsAlias = instruction.opcode in moves &&
            (instruction as TwoRegisterInstruction).registerB in aliases
        val destination = if (instruction.opcode.setsRegister()) {
            (instruction as OneRegisterInstruction).registerA
        } else null
        destination?.let { destination ->
            aliases.remove(destination)
            if (instruction.opcode.setsWideRegister()) aliases.remove(destination + 1)
            if (sourceIsAlias) aliases.add(destination)
        }
    }
    return argumentRegister in aliases
}

private fun Method.parameterRegister(parameterIndex: Int): Int? {
    if (parameterIndex !in parameterTypes.indices) return null
    val implementation = implementation ?: return null
    var register = implementation.registerCount - parameterTypes.sumOf { parameter ->
        if (parameter.toString() in setOf("J", "D")) 2 else 1
    } - if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    parameterTypes.take(parameterIndex).forEach { parameter ->
        register += if (parameter.toString() in setOf("J", "D")) 2 else 1
    }
    return register
}

private fun Method.parameterIndexForRegister(register: Int): Int? = requireAtMostOne(
    "NewX formatter parameter for v$register in $this",
    parameterTypes.indices.filter { parameterRegister(it) == register },
)

private fun MethodReference.isStringBuilderAppend(valueType: String): Boolean =
    definingClass == "Ljava/lang/StringBuilder;" && name == "append" &&
        parameterTypes.map(CharSequence::toString) == listOf(valueType) &&
        returnType == "Ljava/lang/StringBuilder;"

private fun Instruction.invokedArgumentRegister(reference: MethodReference, parameterIndex: Int): Int? {
    val receiverCount = if (opcode == Opcode.INVOKE_STATIC || opcode == Opcode.INVOKE_STATIC_RANGE) 0 else 1
    val argumentIndex = receiverCount + reference.parameterTypes.take(parameterIndex).sumOf { parameter ->
        if (parameter.toString() in setOf("J", "D")) 2 else 1
    }
    return registersUsed.getOrNull(argumentIndex)
}

context(context: BytecodePatchContext)
private fun resolveAuthorFieldGetter(
    authorDescriptor: String,
    minimalUserMatch: app.morphe.patcher.Match,
    fieldLabel: String,
    expectedType: String?,
    semanticName: String,
): MethodReference {
    val field = minimalUserMatch.originalMethod.fieldForHelperToStringValue(fieldLabel)
    if (expectedType != null) {
        field.requireType(expectedType, "minimal-user $semanticName")
    } else if (!field.type.startsWith("L") || !field.type.endsWith(";")) {
        throw PatchException("Expected NewX minimal-user $semanticName field to be an object: $field")
    }
    val userClass = context.mutableClassDefBy(minimalUserMatch.originalClassDef.type)
    val getterMatches = userClass.methods.filter { method ->
        method.isDirectFieldGetter(field)
    }
    if (getterMatches.size != 1) {
        throw PatchException(
            "Expected one NewX minimal-user $semanticName accessor in $userClass, found " +
                "${getterMatches.size}: ${getterMatches.joinToString()}",
        )
    }
    val getter = getterMatches.single()
    return resolveInterfaceMethod(
        descriptor = authorDescriptor,
        name = getter.name,
        parameterTypes = getter.parameterTypes.map(CharSequence::toString),
        returnType = getter.returnType,
    ) ?: throw PatchException(
        "NewX author type $authorDescriptor does not expose the minimal-user $semanticName accessor " +
            "${getter.name}()",
    )
}

context(context: BytecodePatchContext)
private fun resolveAuthorMethodGetter(
    authorDescriptor: String,
    name: String,
    semanticName: String,
): MethodReference {
    val getter =
        resolveInterfaceMethodByName(
            descriptor = authorDescriptor,
            name = name,
            parameterTypes = emptyList(),
        ) ?: throw PatchException(
            "NewX author type $authorDescriptor does not expose the $semanticName accessor $name()",
        )
    if (!getter.returnType.startsWith("L") || !getter.returnType.endsWith(";")) {
        throw PatchException("NewX $semanticName accessor must return an object: $getter")
    }
    return getter
}

context(context: BytecodePatchContext)
private fun resolveInterfaceMethodByName(
    descriptor: String,
    name: String,
    parameterTypes: List<String>,
    visited: MutableSet<String> = mutableSetOf(),
): MethodReference? {
    if (!visited.add(descriptor)) return null
    val classDef = context.classDefByOrNull(descriptor) ?: return null
    val matches = classDef.methods.filter { method ->
        method.name == name &&
            method.parameterTypes.map(CharSequence::toString) == parameterTypes
    }
    if (matches.isNotEmpty()) {
        val method = requireExactlyOne("NewX author $name declaration in $classDef", matches)
        return ImmutableMethodReference(
            descriptor,
            name,
            parameterTypes,
            method.returnType,
        )
    }
    for (interfaceDescriptor in classDef.interfaces) {
        resolveInterfaceMethodByName(
            descriptor = interfaceDescriptor.toString(),
            name = name,
            parameterTypes = parameterTypes,
            visited = visited,
        )?.let { return it }
    }
    return null
}

context(context: BytecodePatchContext)
private fun resolveInterfaceMethod(
    descriptor: String,
    name: String,
    parameterTypes: List<String>,
    returnType: String,
    visited: MutableSet<String> = mutableSetOf(),
): MethodReference? {
    if (!visited.add(descriptor)) return null
    val classDef = context.classDefByOrNull(descriptor) ?: return null
    val matches = classDef.methods.filter { method ->
        method.name == name &&
            method.parameterTypes.map(CharSequence::toString) == parameterTypes &&
            method.returnType == returnType
    }
    if (matches.size > 1) {
        throw PatchException(
            "Expected one NewX author screen-name declaration in $classDef, found " +
                "${matches.size}: ${matches.joinToString()}",
        )
    }
    if (matches.size == 1) {
        return ImmutableMethodReference(descriptor, name, parameterTypes, returnType)
    }
    for (interfaceDescriptor in classDef.interfaces) {
        resolveInterfaceMethod(
            descriptor = interfaceDescriptor.toString(),
            name = name,
            parameterTypes = parameterTypes,
            returnType = returnType,
            visited = visited,
        )?.let { return it }
    }
    return null
}

private fun Method.fieldForHelperToStringValue(label: String): FieldReference {
    val instructions = implementation?.instructions?.toList().orEmpty()
    val labelIndex = instructions.indexOfFirst { instruction ->
        instruction.getReference<StringReference>()?.string == label
    }
    if (labelIndex < 0) {
        throw PatchException("NewX model label '$label' was not found in $this")
    }
    val labelRegister = (instructions[labelIndex] as? OneRegisterInstruction)?.registerA
        ?: throw PatchException("NewX model label '$label' has an unsupported register layout in $this")
    val helper = instructions.withIndex()
        .drop(labelIndex + 1)
        .firstOrNull { (_, instruction) ->
            instruction.getReference<MethodReference>() != null &&
                instruction.argumentRegisters().contains(labelRegister)
        } ?: throw PatchException("NewX model label '$label' has no helper consumer in $this")
    val arguments = helper.value.argumentRegisters()
    val labelArgumentIndex = arguments.indexOf(labelRegister)
    if (labelArgumentIndex < 0) {
        throw PatchException("NewX model label '$label' has an unsupported helper layout in $this")
    }
    val valueRegister = arguments.getOrNull(labelArgumentIndex + 1)
    if (valueRegister != null) {
        instructions.take(helper.index).asReversed().firstNotNullOfOrNull { instruction ->
            if (instruction.opcode != Opcode.IGET_OBJECT) return@firstNotNullOfOrNull null
            val field = instruction.getReference<FieldReference>() ?: return@firstNotNullOfOrNull null
            val read = instruction as? TwoRegisterInstruction ?: return@firstNotNullOfOrNull null
            field.takeIf {
                read.registerA == valueRegister && field.definingClass == definingClass
            }
        }?.let { return it }
    }
    instructions.withIndex()
        .drop(labelIndex + 1)
        .firstNotNullOfOrNull { (index, instruction) ->
            if (instruction.opcode != Opcode.IGET_OBJECT) return@firstNotNullOfOrNull null
            val field = instruction.getReference<FieldReference>() ?: return@firstNotNullOfOrNull null
            if (field.definingClass != definingClass) return@firstNotNullOfOrNull null
            val read = instruction as? TwoRegisterInstruction ?: return@firstNotNullOfOrNull null
            instructions.drop(index + 1).firstOrNull { consumer ->
                consumer.getReference<MethodReference>()?.name == "append" &&
                    consumer.argumentRegisters().contains(read.registerA)
            }?.let { field }
        }?.let { return it }
    throw PatchException("NewX model field for '$label' was not found in $this")
}

private fun Instruction.argumentRegisters(): List<Int> {
    val variable = this as? VariableRegisterInstruction ?: return emptyList()
    val count = variable.registerCount
    if (this is FiveRegisterInstruction) {
        return listOf(registerC, registerD, registerE, registerF, registerG).take(count)
    }
    if (this is RegisterRangeInstruction) {
        return (startRegister until startRegister + count).toList()
    }
    return emptyList()
}

private fun Method.isDirectFieldGetter(field: FieldReference): Boolean {
    val instructions = implementation?.instructions?.toList() ?: return false
    if (instructions.size != 2) return false
    if (instructions[0].opcode != Opcode.IGET_OBJECT) return false
    if (instructions[0].getReference<FieldReference>()?.toString() != field.toString()) {
        return false
    }
    return instructions[1].opcode == Opcode.RETURN_OBJECT
}

private fun FieldReference.requireType(expectedType: String, semanticName: String) {
    if (type == expectedType) return
    throw PatchException(
        "Expected NewX $semanticName field of type $expectedType, found $this",
    )
}

context(context: BytecodePatchContext)
private fun patchPostTextBridges(
    postDescriptor: String,
    textGetter: MethodReference,
    postTextModels: ResolvedPostTextModels,
) {
    val filterClass = context.mutableClassDefBy(TIMELINE_FILTER_DESCRIPTOR)

    /**
     * Resolves the single NewX timeline bridge [name] and prepares it for injection: a bridge that
     * needs local register headroom is cloned with [additionalRegisters] registers added on top of
     * its parameter registers, and [replaceBody] empties the release stub before the injected block.
     */
    fun resolveBridge(
        name: String,
        parameters: String,
        returnType: String,
        additionalRegisters: Int = 0,
        replaceBody: Boolean = false,
    ): MutableMethod {
        val matches =
            filterClass.methods.filter { method ->
                method.name == name &&
                    method.parameterTypes.joinToString("") == parameters &&
                    method.returnType == returnType
            }
        if (matches.size != 1) {
            throw PatchException(
                "Expected one NewX timeline bridge $name($parameters)$returnType, found " +
                    "${matches.size}: ${matches.joinToString()}",
            )
        }

        val originalMethod = matches.single()
        val method =
            if (additionalRegisters == 0) {
                originalMethod
            } else {
                val clonedMethod =
                    originalMethod.cloneMutable(
                        additionalRegisters = originalMethod.numberOfParameterRegisters + additionalRegisters,
                    )
                filterClass.methods.remove(originalMethod)
                filterClass.methods.add(clonedMethod)
                clonedMethod
            }
        if (replaceBody) {
            val implementation =
                method.implementation
                    ?: throw PatchException("NewX timeline bridge $name has no implementation")
            while (implementation.instructions.isNotEmpty()) {
                implementation.removeInstruction(implementation.instructions.lastIndex)
            }
        }
        return method
    }

    // A prepended bridge returns in front of the release stub, so the stub body stays behind it as
    // dead code and a branch into the method head keeps skipping the bridge, exactly as the plain
    // smali insertion did.
    val postTextBridge = resolveBridge(POST_TEXT_BRIDGE, OBJECT_DESCRIPTOR, STRING_DESCRIPTOR)
    postTextBridge.insertHook(index = 0, relocateBranchTargets = false) {
        val receiverRegister = postTextBridge.p0Register
        checkCast(receiverRegister, postDescriptor)
        invokeVirtual(textGetter, receiverRegister)
        moveResult(receiverRegister, STRING_DESCRIPTOR)
        returnObject(receiverRegister)
    }

    val mentionStartIdxBridge =
        resolveBridge(MENTION_START_IDX_BRIDGE, OBJECT_DESCRIPTOR, INTEGER_DESCRIPTOR)
    mentionStartIdxBridge.insertHook(index = 0, relocateBranchTargets = false) {
        val receiverRegister = mentionStartIdxBridge.p0Register
        checkCast(receiverRegister, postTextModels.mentionDescriptor)
        iget(receiverRegister, receiverRegister, postTextModels.mentionStartIdxField)
        returnValue(receiverRegister)
    }

    val mentionEndIdxBridge =
        resolveBridge(MENTION_END_IDX_BRIDGE, OBJECT_DESCRIPTOR, INTEGER_DESCRIPTOR)
    mentionEndIdxBridge.insertHook(index = 0, relocateBranchTargets = false) {
        val receiverRegister = mentionEndIdxBridge.p0Register
        checkCast(receiverRegister, postTextModels.mentionDescriptor)
        iget(receiverRegister, receiverRegister, postTextModels.mentionEndIdxField)
        returnValue(receiverRegister)
    }

    val mentionScreenNameBridge =
        resolveBridge(MENTION_SCREEN_NAME_BRIDGE, OBJECT_DESCRIPTOR, STRING_DESCRIPTOR)
    mentionScreenNameBridge.insertHook(index = 0, relocateBranchTargets = false) {
        val receiverRegister = mentionScreenNameBridge.p0Register
        checkCast(receiverRegister, postTextModels.mentionDescriptor)
        iget(receiverRegister, receiverRegister, postTextModels.mentionScreenNameField)
        returnObject(receiverRegister)
    }

    // The contextual-post bridges replace the release stub body, so the block runs in the local
    // registers the extra registers keep below the parameter block (v0/v1) and p0 is the release
    // union result.
    val repliedPostIdBridge =
        resolveBridge(
            REPLIED_POST_ID_BRIDGE,
            OBJECT_DESCRIPTOR,
            OBJECT_DESCRIPTOR,
            additionalRegisters = 1,
            replaceBody = true,
        )
    repliedPostIdBridge.insertHook(0, relocateBranchTargets = false) {
        val workRegister = 0
        readContextualCanonicalPost(
            receiverRegister = repliedPostIdBridge.p0Register,
            workRegister = workRegister,
            typeCheckRegister = 1,
            postDescriptor = postDescriptor,
            postTextModels = postTextModels,
            noContextualResultLabel = REPLIED_POST_ID_NO_CONTEXTUAL_RESULT_LABEL,
        )
        iget(workRegister, workRegister, postTextModels.repliedPostIdField)
        returnObject(workRegister)
        label(REPLIED_POST_ID_NO_CONTEXTUAL_RESULT_LABEL)
        constInt(workRegister, 0)
        returnObject(workRegister)
    }

    val postMentionsBridge =
        resolveBridge(
            POST_MENTIONS_BRIDGE,
            OBJECT_DESCRIPTOR,
            LIST_DESCRIPTOR,
            additionalRegisters = 1,
            replaceBody = true,
        )
    postMentionsBridge.insertHook(0, relocateBranchTargets = false) {
        val workRegister = 0
        readContextualCanonicalPost(
            receiverRegister = postMentionsBridge.p0Register,
            workRegister = workRegister,
            typeCheckRegister = 1,
            postDescriptor = postDescriptor,
            postTextModels = postTextModels,
            noContextualResultLabel = POST_MENTIONS_NO_CONTEXTUAL_RESULT_LABEL,
        )
        iget(workRegister, workRegister, postTextModels.entityListField)
        ifEqz(workRegister, Target.Local(POST_MENTIONS_NULL_LABEL))
        checkCast(workRegister, postTextModels.entityListField.type)
        iget(workRegister, workRegister, postTextModels.mentionsField)
        returnObject(workRegister)
        label(POST_MENTIONS_NO_CONTEXTUAL_RESULT_LABEL)
        label(POST_MENTIONS_NULL_LABEL)
        constInt(workRegister, 0)
        returnObject(workRegister)
    }

    val authorScreenNameBridge =
        resolveBridge(
            POST_AUTHOR_SCREEN_NAME_BRIDGE,
            OBJECT_DESCRIPTOR,
            STRING_DESCRIPTOR,
            additionalRegisters = 1,
            replaceBody = true,
        )
    authorScreenNameBridge.insertHook(0, relocateBranchTargets = false) {
        val workRegister = 0
        readContextualCanonicalPost(
            receiverRegister = authorScreenNameBridge.p0Register,
            workRegister = workRegister,
            typeCheckRegister = 1,
            postDescriptor = postDescriptor,
            postTextModels = postTextModels,
            noContextualResultLabel = POST_AUTHOR_NO_CONTEXTUAL_RESULT_LABEL,
        )
        iget(workRegister, workRegister, postTextModels.authorField)
        ifEqz(workRegister, Target.Local(POST_AUTHOR_NULL_LABEL))
        checkCast(workRegister, postTextModels.authorField.type)
        invokeInterface(postTextModels.authorScreenNameGetter, workRegister)
        moveResult(workRegister, STRING_DESCRIPTOR)
        returnObject(workRegister)
        label(POST_AUTHOR_NO_CONTEXTUAL_RESULT_LABEL)
        label(POST_AUTHOR_NULL_LABEL)
        constInt(workRegister, 0)
        returnObject(workRegister)
    }

    val authorVerifiedTypeBridge =
        resolveBridge(
            POST_AUTHOR_VERIFIED_TYPE_BRIDGE,
            OBJECT_DESCRIPTOR,
            OBJECT_DESCRIPTOR,
            additionalRegisters = 1,
            replaceBody = true,
        )
    authorVerifiedTypeBridge.insertHook(0, relocateBranchTargets = false) {
        val workRegister = 0
        readContextualCanonicalPost(
            receiverRegister = authorVerifiedTypeBridge.p0Register,
            workRegister = workRegister,
            typeCheckRegister = 1,
            postDescriptor = postDescriptor,
            postTextModels = postTextModels,
            noContextualResultLabel = POST_AUTHOR_VERIFIED_TYPE_NO_CONTEXTUAL_RESULT_LABEL,
        )
        iget(workRegister, workRegister, postTextModels.authorField)
        ifEqz(workRegister, Target.Local(POST_AUTHOR_VERIFIED_TYPE_NULL_LABEL))
        checkCast(workRegister, postTextModels.authorField.type)
        invokeInterface(postTextModels.authorVerifiedTypeGetter, workRegister)
        moveResult(workRegister, OBJECT_DESCRIPTOR)
        returnObject(workRegister)
        label(POST_AUTHOR_VERIFIED_TYPE_NO_CONTEXTUAL_RESULT_LABEL)
        label(POST_AUTHOR_VERIFIED_TYPE_NULL_LABEL)
        constInt(workRegister, 0)
        returnObject(workRegister)
    }

    val authorIdBridge =
        resolveBridge(
            POST_AUTHOR_ID_BRIDGE,
            OBJECT_DESCRIPTOR,
            STRING_DESCRIPTOR,
            additionalRegisters = 1,
            replaceBody = true,
        )
    authorIdBridge.insertHook(0, relocateBranchTargets = false) {
        val workRegister = 0
        readContextualCanonicalPost(
            receiverRegister = authorIdBridge.p0Register,
            workRegister = workRegister,
            typeCheckRegister = 1,
            postDescriptor = postDescriptor,
            postTextModels = postTextModels,
            noContextualResultLabel = POST_AUTHOR_ID_NO_CONTEXTUAL_RESULT_LABEL,
        )
        iget(workRegister, workRegister, postTextModels.authorField)
        ifEqz(workRegister, Target.Local(POST_AUTHOR_ID_NULL_LABEL))
        checkCast(workRegister, postTextModels.authorField.type)
        invokeInterface(postTextModels.authorIdGetter, workRegister)
        moveResult(workRegister, OBJECT_DESCRIPTOR)
        ifEqz(workRegister, Target.Local(POST_AUTHOR_ID_NULL_LABEL))
        invokeVirtual(methodReference(OBJECT_TO_STRING_DESCRIPTOR), workRegister)
        moveResult(workRegister, STRING_DESCRIPTOR)
        returnObject(workRegister)
        label(POST_AUTHOR_ID_NO_CONTEXTUAL_RESULT_LABEL)
        label(POST_AUTHOR_ID_NULL_LABEL)
        constInt(workRegister, 0)
        returnObject(workRegister)
    }
}

/**
 * Emits the prologue the contextual-post bridges shared: copies the release union result from
 * [receiverRegister] into [workRegister], jumps to [noContextualResultLabel] when it is not a
 * contextual post, and leaves the canonical post in [workRegister].
 */
private fun Block.readContextualCanonicalPost(
    receiverRegister: Int,
    workRegister: Int,
    typeCheckRegister: Int,
    postDescriptor: String,
    postTextModels: ResolvedPostTextModels,
    noContextualResultLabel: String,
) {
    move(workRegister, receiverRegister, OBJECT_DESCRIPTOR)
    checkCast(workRegister, postDescriptor)
    iget(workRegister, workRegister, postTextModels.postResultField)
    instanceOf(typeCheckRegister, workRegister, postTextModels.contextualPostDescriptor)
    ifEqz(typeCheckRegister, Target.Local(noContextualResultLabel))
    checkCast(workRegister, postTextModels.contextualPostDescriptor)
    readModelAccessor(postTextModels.contextualCanonicalPost, workRegister)
    checkCast(workRegister, postTextModels.canonicalPostDescriptor)
}

/**
 * Emits the model read the previous smali strings described: the generated getter when the model
 * exposes one, otherwise a direct field access.
 */
private fun Block.readModelAccessor(
    accessor: ModelFieldAccessor,
    register: Int,
) {
    val getter = accessor.getter
    if (getter == null) {
        iget(register, register, accessor.field)
        return
    }
    invokeVirtual(getter, register)
    moveResult(register, accessor.field.type)
}
