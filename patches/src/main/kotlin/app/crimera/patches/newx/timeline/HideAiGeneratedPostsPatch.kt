package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.multiChoice
import app.crimera.patches.newx.models.ModelFieldAccessor
import app.crimera.patches.newx.models.resolvedNewXPostModels
import app.crimera.patches.newx.models.resolveFieldAccessor
import app.crimera.patches.newx.models.resolvedNewXTimelineModels
import app.crimera.patches.newx.models.newXPostModelResolutionPatch
import app.crimera.patches.newx.models.newXTimelineModelAdapterPatch
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.crimera.bytecode.Block
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

@Suppress("unused")
val newXHideAiGeneratedPostsPatch =
    bytecodePatch(
        name = "NewX: Hide AI-generated posts",
        description = "Hides selected AI-generated posts from NewX timelines.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXTimelineModelAdapterPatch, newXPostModelResolutionPatch, newXTimelineFilterPatch)

        val aiSourcesToHide =
            newXSettings {
                category(Categories.CONTENT) {
                    multiChoice(
                        id = "newx.content.hide_ai_generated_posts",
                        strings = settingStrings("piko_newx_hide_ai_generated_posts"),
                        order = 300,
                        defaultValue = emptySet(),
                        options =
                            listOf(
                                choice("UserMarked", "piko_newx_hide_ai_generated_posts_user_marked"),
                                choice("AutoDetected", "piko_newx_hide_ai_generated_posts_auto_detected"),
                                choice("SourceNotIdentified", "piko_newx_hide_ai_generated_posts_source_not_identified"),
                            ),
                    )
                }
            }

        execute {
            val accessors = resolveAiDisclosureAccessors()
            patchAiDisclosureAccessors(accessors)

        }
    }

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val CONTENT_DISCLOSURE_HELPER = "getContentDisclosure"
private const val HAS_AI_DISCLOSURE_HELPER = "hasAiGeneratedDisclosure"
private const val SOURCE_HELPER = "getAiDetectionSource"
private const val NO_CONTEXTUAL_POST_RESULT_LABEL = "piko_newx_no_contextual_post_result"

private object ContentDisclosureModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(string("ContentDisclosure(hasPaidPromotionDisclosure=")),
)

private data class AiDisclosureAccessors(
    val timelinePostDescriptor: String,
    val timelinePostResult: ModelFieldAccessor,
    val contextualPostDescriptor: String,
    val contextualCanonicalPost: ModelFieldAccessor,
    val canonicalContentDisclosure: ModelFieldAccessor,
    val contentDisclosureDescriptor: String,
    val hasAiDisclosure: ModelFieldAccessor,
    val source: ModelFieldAccessor,
)

context(context: BytecodePatchContext)
private fun resolveAiDisclosureAccessors(): AiDisclosureAccessors {
    // Disclosure models may expose fields directly or through generated getters.
    val contentDisclosureMatches =
        ContentDisclosureModelFingerprint.scopedMatchAll()
    if (contentDisclosureMatches.size != 1) {
        throw PatchException(
            "Expected one NewX content disclosure model, found ${contentDisclosureMatches.size}: " +
                contentDisclosureMatches.joinToString { it.originalMethod.toString() },
        )
    }
    val contentDisclosureMatch = contentDisclosureMatches.single()
    val contentDisclosureDescriptor = contentDisclosureMatch.classDef.type
    val contentDisclosureClass = context.mutableClassDefBy(contentDisclosureDescriptor)
    val disclosureBooleanFields =
        contentDisclosureMatch.instanceFieldsRead("Z")
    if (disclosureBooleanFields.size != 3) {
        throw PatchException(
            "Expected three ordered NewX content-disclosure booleans, found " +
                "${disclosureBooleanFields.size}: ${disclosureBooleanFields.joinToString()}",
        )
    }
    val hasAiDisclosureField = disclosureBooleanFields[1]
    val sourceField =
        requireExactlyOne(
            "NewX content-disclosure source field",
            contentDisclosureClass.fields.filter { field ->
                !AccessFlags.STATIC.isSet(field.accessFlags) &&
                    field.type.startsWith("L") &&
                    field.type.endsWith(";")
            },
        )
    val hasAiDisclosureAccessor =
        contentDisclosureClass.resolveFieldAccessor(hasAiDisclosureField, "AI disclosure")
    val sourceAccessor =
        contentDisclosureClass.resolveFieldAccessor(sourceField, "AI disclosure source")

    val timelineModels = resolvedNewXTimelineModels()
    val timelinePostClass = context.mutableClassDefBy(timelineModels.postDescriptor)
    val timelinePostResultAccessor =
        timelinePostClass.resolveFieldAccessor(timelineModels.postResultField, "timeline post result")
    val postModels = resolvedNewXPostModels()

    val contextualPostClass = context.mutableClassDefBy(postModels.contextualPostDescriptor)
    val canonicalPostClass = context.mutableClassDefBy(postModels.canonicalPostDescriptor)
    val canonicalContentDisclosureField =
        requireExactlyOne(
            "NewX canonical-post content-disclosure field",
            canonicalPostClass.fields.filter { field ->
                field.type == contentDisclosureDescriptor && !AccessFlags.STATIC.isSet(field.accessFlags)
            },
        )

    val contextualCanonicalPostAccessor =
        contextualPostClass.resolveFieldAccessor(
            postModels.contextualCanonicalPostField,
            "contextual canonical post",
        )
    val canonicalContentDisclosureAccessor =
        canonicalPostClass.resolveFieldAccessor(
            canonicalContentDisclosureField,
            "canonical content disclosure",
        )
    if (!sourceField.type.startsWith("L") || !sourceField.type.endsWith(";")) {
        throw PatchException("NewX content disclosure source is not an object: $sourceField")
    }
    if (hasAiDisclosureField.type != "Z") {
        throw PatchException("NewX AI disclosure field is not a boolean: $hasAiDisclosureField")
    }
    return AiDisclosureAccessors(
        timelinePostDescriptor = timelineModels.postDescriptor,
        timelinePostResult = timelinePostResultAccessor,
        contextualPostDescriptor = postModels.contextualPostDescriptor,
        contextualCanonicalPost = contextualCanonicalPostAccessor,
        canonicalContentDisclosure = canonicalContentDisclosureAccessor,
        contentDisclosureDescriptor = contentDisclosureDescriptor,
        hasAiDisclosure = hasAiDisclosureAccessor,
        source = sourceAccessor,
    )
}

context(context: BytecodePatchContext)
private fun patchAiDisclosureAccessors(accessors: AiDisclosureAccessors) {
    val filterClass = context.mutableClassDefBy(TIMELINE_FILTER_DESCRIPTOR)

    /**
     * Resolves the single NewX timeline helper [name] and prepares it for injection: a helper that
     * needs register headroom is cloned with [additionalRegisters] registers added on top of its
     * parameter registers, and [replaceBody] empties the release stub before the injected block.
     */
    fun resolveHelper(
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
                "Expected one NewX timeline helper $name($parameters)$returnType, found " +
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
                    ?: throw PatchException("NewX helper $name has no implementation")
            while (implementation.instructions.isNotEmpty()) {
                implementation.removeInstruction(implementation.instructions.lastIndex)
            }
        }
        return method
    }

    // Timeline post results are a sealed union; tombstones have no disclosure. The stub body is
    // replaced wholesale, so the block runs in the two locals the extra registers keep below the
    // parameter registers.
    val contentDisclosureHelper =
        resolveHelper(
            name = CONTENT_DISCLOSURE_HELPER,
            parameters = OBJECT_DESCRIPTOR,
            returnType = OBJECT_DESCRIPTOR,
            additionalRegisters = 1,
            replaceBody = true,
        )
    contentDisclosureHelper.insertHook(0, relocateBranchTargets = false) {
        val workRegister = 0
        val typeCheckRegister = 1
        move(workRegister, contentDisclosureHelper.p0Register, OBJECT_DESCRIPTOR)
        checkCast(workRegister, accessors.timelinePostDescriptor)
        readModelAccessor(accessors.timelinePostResult, workRegister)
        instanceOf(typeCheckRegister, workRegister, accessors.contextualPostDescriptor)
        ifEqz(typeCheckRegister, Target.Local(NO_CONTEXTUAL_POST_RESULT_LABEL))
        checkCast(workRegister, accessors.contextualPostDescriptor)
        readModelAccessor(accessors.contextualCanonicalPost, workRegister)
        readModelAccessor(accessors.canonicalContentDisclosure, workRegister)
        returnObject(workRegister)
        label(NO_CONTEXTUAL_POST_RESULT_LABEL)
        constInt(workRegister, 0)
        returnObject(workRegister)
    }

    val hasAiDisclosureHelper =
        resolveHelper(
            name = HAS_AI_DISCLOSURE_HELPER,
            parameters = OBJECT_DESCRIPTOR,
            returnType = "Z",
        )
    hasAiDisclosureHelper.insertHook(0, relocateBranchTargets = false) {
        val parameterRegister = hasAiDisclosureHelper.p0Register
        checkCast(parameterRegister, accessors.contentDisclosureDescriptor)
        readModelAccessor(accessors.hasAiDisclosure, parameterRegister)
        returnValue(parameterRegister)
    }

    val sourceHelper =
        resolveHelper(
            name = SOURCE_HELPER,
            parameters = OBJECT_DESCRIPTOR,
            returnType = OBJECT_DESCRIPTOR,
        )
    sourceHelper.insertHook(0, relocateBranchTargets = false) {
        val parameterRegister = sourceHelper.p0Register
        checkCast(parameterRegister, accessors.contentDisclosureDescriptor)
        readModelAccessor(accessors.source, parameterRegister)
        returnObject(parameterRegister)
    }
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

private fun Match.instanceFieldsRead(type: String): List<FieldReference> =
    method.instructions.mapNotNull { instruction ->
        instruction.getReference<FieldReference>()?.takeIf { field ->
            field.definingClass == originalMethod.definingClass && field.type == type
        }
    }.distinctBy(FieldReference::toString)

private fun <T> requireExactlyOne(target: String, matches: List<T>): T {
    if (matches.size == 1) return matches.single()
    throw PatchException("Expected one $target, found ${matches.size}: ${matches.joinToString()}")
}
