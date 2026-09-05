package app.crimera.patches.newx.timeline.postfilter

import app.crimera.patches.newx.models.ResolvedNewXPostModels
import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.models.newXPostModelResolutionPatch
import app.crimera.patches.newx.models.newXTimelineModelAdapterPatch
import app.crimera.patches.newx.models.patchBridge
import app.crimera.patches.newx.models.readObject
import app.crimera.patches.newx.models.requirePublicFields
import app.crimera.patches.newx.models.requireSingle
import app.crimera.patches.newx.models.resolveFieldAccessor
import app.crimera.patches.newx.models.resolvedNewXPostModels
import app.crimera.patches.newx.models.resolvedNewXTimelineModels
import app.crimera.patches.newx.models.smaliReference
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
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
    val contextualCanonicalPostRead: String,
    val canonicalPostDescriptor: String,
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
    val canonicalPostClass = context.mutableClassDefBy(canonicalPostDescriptor)
    canonicalPostClass.requirePublicFields(listOf(entityListField, authorField))

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
        contextualCanonicalPostRead = contextualCanonicalPostAccessor.readObject("v0"),
        canonicalPostDescriptor = canonicalPostDescriptor,
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
    if (matches.size > 1) {
        throw PatchException(
            "Expected one NewX author $name declaration in $classDef, found " +
                "${matches.size}: ${matches.joinToString()}",
        )
    }
    if (matches.size == 1) {
        val method = matches.single()
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
    filterClass.patchBridge(
        "getPostText",
        OBJECT_DESCRIPTOR,
        STRING_DESCRIPTOR,
        """
            check-cast p0, $postDescriptor
            invoke-virtual {p0}, ${textGetter.smaliReference()}
            move-result-object p0
            return-object p0
        """.trimIndent(),
    )
    filterClass.replaceContextualPostBridge(
        name = "getPostMentions",
        returnType = LIST_DESCRIPTOR,
        instructions =
            """
                move-object/from16 v0, p0
                check-cast v0, $postDescriptor
                iget-object v0, v0, ${postTextModels.postResultField}
                instance-of v1, v0, ${postTextModels.contextualPostDescriptor}
                if-eqz v1, :piko_newx_post_mentions_no_contextual_result
                check-cast v0, ${postTextModels.contextualPostDescriptor}
                ${postTextModels.contextualCanonicalPostRead}
                check-cast v0, ${postTextModels.canonicalPostDescriptor}
                iget-object v0, v0, ${postTextModels.entityListField}
                if-eqz v0, :piko_newx_post_mentions_null
                check-cast v0, ${postTextModels.entityListField.type}
                iget-object v0, v0, ${postTextModels.mentionsField}
                return-object v0
                :piko_newx_post_mentions_no_contextual_result
                :piko_newx_post_mentions_null
                const/4 v0, 0x0
                return-object v0
            """.trimIndent(),
    )
    filterClass.patchBridge(
        "getMentionStartIdx",
        OBJECT_DESCRIPTOR,
        INTEGER_DESCRIPTOR,
        """
            check-cast p0, ${postTextModels.mentionDescriptor}
            iget p0, p0, ${postTextModels.mentionStartIdxField}
            return p0
        """.trimIndent(),
    )
    filterClass.patchBridge(
        "getMentionEndIdx",
        OBJECT_DESCRIPTOR,
        INTEGER_DESCRIPTOR,
        """
            check-cast p0, ${postTextModels.mentionDescriptor}
            iget p0, p0, ${postTextModels.mentionEndIdxField}
            return p0
        """.trimIndent(),
    )
    filterClass.patchBridge(
        "getMentionScreenName",
        OBJECT_DESCRIPTOR,
        STRING_DESCRIPTOR,
        """
            check-cast p0, ${postTextModels.mentionDescriptor}
            iget-object p0, p0, ${postTextModels.mentionScreenNameField}
            return-object p0
        """.trimIndent(),
    )
    filterClass.replaceContextualPostBridge(
        name = "getPostAuthorScreenName",
        returnType = STRING_DESCRIPTOR,
        instructions =
            """
                move-object/from16 v0, p0
                check-cast v0, $postDescriptor
                iget-object v0, v0, ${postTextModels.postResultField}
                instance-of v1, v0, ${postTextModels.contextualPostDescriptor}
                if-eqz v1, :piko_newx_post_author_no_contextual_result
                check-cast v0, ${postTextModels.contextualPostDescriptor}
                ${postTextModels.contextualCanonicalPostRead}
                check-cast v0, ${postTextModels.canonicalPostDescriptor}
                iget-object v0, v0, ${postTextModels.authorField}
                if-eqz v0, :piko_newx_post_author_null
                check-cast v0, ${postTextModels.authorField.type}
                invoke-interface {v0}, ${postTextModels.authorScreenNameGetter.smaliReference()}
                move-result-object v0
                return-object v0
                :piko_newx_post_author_no_contextual_result
                :piko_newx_post_author_null
                const/4 v0, 0x0
                return-object v0
            """.trimIndent(),
    )
    filterClass.replaceContextualPostBridge(
        name = "getPostAuthorVerifiedType",
        returnType = OBJECT_DESCRIPTOR,
        instructions =
            """
                move-object/from16 v0, p0
                check-cast v0, $postDescriptor
                iget-object v0, v0, ${postTextModels.postResultField}
                instance-of v1, v0, ${postTextModels.contextualPostDescriptor}
                if-eqz v1, :piko_newx_post_author_verified_type_no_contextual_result
                check-cast v0, ${postTextModels.contextualPostDescriptor}
                ${postTextModels.contextualCanonicalPostRead}
                check-cast v0, ${postTextModels.canonicalPostDescriptor}
                iget-object v0, v0, ${postTextModels.authorField}
                if-eqz v0, :piko_newx_post_author_verified_type_null
                check-cast v0, ${postTextModels.authorField.type}
                invoke-interface {v0}, ${postTextModels.authorVerifiedTypeGetter.smaliReference()}
                move-result-object v0
                return-object v0
                :piko_newx_post_author_verified_type_no_contextual_result
                :piko_newx_post_author_verified_type_null
                const/4 v0, 0x0
                return-object v0
            """.trimIndent(),
    )
    filterClass.replaceContextualPostBridge(
        name = "getPostAuthorId",
        returnType = STRING_DESCRIPTOR,
        instructions =
            """
                move-object/from16 v0, p0
                check-cast v0, $postDescriptor
                iget-object v0, v0, ${postTextModels.postResultField}
                instance-of v1, v0, ${postTextModels.contextualPostDescriptor}
                if-eqz v1, :piko_newx_post_author_id_no_contextual_result
                check-cast v0, ${postTextModels.contextualPostDescriptor}
                ${postTextModels.contextualCanonicalPostRead}
                check-cast v0, ${postTextModels.canonicalPostDescriptor}
                iget-object v0, v0, ${postTextModels.authorField}
                if-eqz v0, :piko_newx_post_author_id_null
                check-cast v0, ${postTextModels.authorField.type}
                invoke-interface {v0}, ${postTextModels.authorIdGetter.smaliReference()}
                move-result-object v0
                if-eqz v0, :piko_newx_post_author_id_null
                invoke-virtual {v0}, Ljava/lang/Object;->toString()Ljava/lang/String;
                move-result-object v0
                return-object v0
                :piko_newx_post_author_id_no_contextual_result
                :piko_newx_post_author_id_null
                const/4 v0, 0x0
                return-object v0
            """.trimIndent(),
    )
}

private fun MutableClass.replaceContextualPostBridge(
    name: String,
    returnType: String,
    instructions: String,
) {
    val matches = methods.filter { method ->
        method.name == name &&
            method.parameterTypes.joinToString("") == OBJECT_DESCRIPTOR &&
            method.returnType == returnType
    }
    if (matches.size != 1) {
        throw PatchException(
            "Expected one NewX contextual-post bridge $name($OBJECT_DESCRIPTOR)$returnType in $this, found " +
                "${matches.size}: ${matches.joinToString()}",
        )
    }

    val originalMethod = matches.single()
    val method = originalMethod.cloneMutable(
        additionalRegisters = originalMethod.numberOfParameterRegisters + 1,
    )
    methods.remove(originalMethod)
    methods.add(method)
    val implementation = method.implementation
        ?: throw PatchException("NewX contextual-post bridge $name has no implementation")
    while (implementation.instructions.isNotEmpty()) {
        implementation.removeInstruction(implementation.instructions.lastIndex)
    }
    method.addInstructions(0, instructions.trimIndent())
}
