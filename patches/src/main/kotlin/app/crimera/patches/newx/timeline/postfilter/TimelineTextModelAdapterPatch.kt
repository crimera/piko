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
import app.crimera.patches.newx.utils.requireExactlyOne
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.p0Register
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
    val mentionEndIdxField = mentionMatch.fieldForToStringLabel(", endIdx=")
    val mentionScreenNameField = mentionMatch.fieldForToStringLabel(", screenName=")
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
