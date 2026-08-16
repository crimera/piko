package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.Groups
import app.crimera.patches.xlite.settings.SettingReadRegisterConstraint
import app.crimera.patches.xlite.settings.choice
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.injectRead
import app.crimera.patches.xlite.settings.multiChoice
import app.crimera.patches.xlite.models.readBoolean
import app.crimera.patches.xlite.models.readObject
import app.crimera.patches.xlite.models.resolvedXLitePostModels
import app.crimera.patches.xlite.models.resolveFieldAccessor
import app.crimera.patches.xlite.models.resolvedXLiteTimelineModels
import app.crimera.patches.xlite.models.xLitePostModelResolutionPatch
import app.crimera.patches.xlite.models.xLiteTimelineModelAdapterPatch
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteSettings
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.numberOfParameterRegistersLogical
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

@Suppress("unused")
val xLiteHideAiGeneratedPostsPatch =
    bytecodePatch(
        name = "X-Lite: Hide AI-generated posts",
        description = "Hides selected AI-generated posts from X-Lite timelines.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)
        dependsOn(xLiteTimelineModelAdapterPatch, xLitePostModelResolutionPatch)

        val aiSourcesToHide =
            xLiteSettings {
                category(Categories.CONTENT) {
                    group(Groups.CONTENT_FILTERING) {
                        multiChoice(
                            id = "xlite.content.hide_ai_generated_posts",
                            strings = settingStrings("piko_xlite_hide_ai_generated_posts"),
                            order = 300,
                            defaultValue = emptySet(),
                            options =
                                listOf(
                                    choice("UserMarked", "piko_xlite_hide_ai_generated_posts_user_marked"),
                                    choice("AutoDetected", "piko_xlite_hide_ai_generated_posts_auto_detected"),
                                    choice("SourceNotIdentified", "piko_xlite_hide_ai_generated_posts_source_not_identified"),
                                ),
                        )
                    }
                }
            }

        execute {
            val accessors = resolveAiDisclosureAccessors()
            patchAiDisclosureAccessors(accessors)

            val matches = XLiteTimelineSuccessFingerprint.scopedMatchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite timeline success constructor, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            matches.single().method.apply {
                val read =
                    aiSourcesToHide.injectRead(
                        method = this,
                        index = 0,
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )
                addInstructions(
                    read.nextIndex,
                    """
                        invoke-static {p2, v${read.register}}, $TIMELINE_FILTER_DESCRIPTOR->filterAiGeneratedPosts(Ljava/lang/Object;Ljava/util/Set;)Ljava/lang/Object;
                        move-result-object p2
                    """.trimIndent(),
                )
            }
        }
    }

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val CONTENT_DISCLOSURE_HELPER = "getContentDisclosure"
private const val HAS_AI_DISCLOSURE_HELPER = "hasAiGeneratedDisclosure"
private const val SOURCE_HELPER = "getAiDetectionSource"

private object ContentDisclosureModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(string("ContentDisclosure(hasPaidPromotionDisclosure=")),
)

private data class AiDisclosureAccessors(
    val timelinePostDescriptor: String,
    val timelinePostResultRead: String,
    val contextualPostDescriptor: String,
    val contextualCanonicalPostRead: String,
    val canonicalContentDisclosureRead: String,
    val contentDisclosureDescriptor: String,
    val hasAiDisclosureRead: String,
    val sourceRead: String,
)

context(context: BytecodePatchContext)
private fun resolveAiDisclosureAccessors(): AiDisclosureAccessors {
    // ALPHA PATH uses public disclosure fields; BETA PATH uses generated model getters.
    // TODO: Remove the field fallback when alpha compatibility is deprecated.
    val contentDisclosureMatches =
        ContentDisclosureModelFingerprint.scopedMatchAll()
    if (contentDisclosureMatches.size != 1) {
        throw PatchException(
            "Expected one X-Lite content disclosure model, found ${contentDisclosureMatches.size}: " +
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
            "Expected three ordered X-Lite content-disclosure booleans, found " +
                "${disclosureBooleanFields.size}: ${disclosureBooleanFields.joinToString()}",
        )
    }
    val hasAiDisclosureField = disclosureBooleanFields[1]
    val sourceField =
        requireExactlyOne(
            "X-Lite content-disclosure source field",
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

    val timelineModels = resolvedXLiteTimelineModels()
    val timelinePostClass = context.mutableClassDefBy(timelineModels.postDescriptor)
    val timelinePostResultAccessor =
        timelinePostClass.resolveFieldAccessor(timelineModels.postResultField, "timeline post result")
    val postModels = resolvedXLitePostModels()

    val contextualPostClass = context.mutableClassDefBy(postModels.contextualPostDescriptor)
    val canonicalPostClass = context.mutableClassDefBy(postModels.canonicalPostDescriptor)
    val canonicalContentDisclosureField =
        requireExactlyOne(
            "X-Lite canonical-post content-disclosure field",
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
        throw PatchException("X-Lite content disclosure source is not an object: $sourceField")
    }
    if (hasAiDisclosureField.type != "Z") {
        throw PatchException("X-Lite AI disclosure field is not a boolean: $hasAiDisclosureField")
    }
    return AiDisclosureAccessors(
        timelinePostDescriptor = timelineModels.postDescriptor,
        timelinePostResultRead = timelinePostResultAccessor.readObject("v0"),
        contextualPostDescriptor = postModels.contextualPostDescriptor,
        contextualCanonicalPostRead = contextualCanonicalPostAccessor.readObject("v0"),
        canonicalContentDisclosureRead = canonicalContentDisclosureAccessor.readObject("v0"),
        contentDisclosureDescriptor = contentDisclosureDescriptor,
        hasAiDisclosureRead = hasAiDisclosureAccessor.readBoolean("p0"),
        sourceRead = sourceAccessor.readObject("p0"),
    )
}

context(context: BytecodePatchContext)
private fun patchAiDisclosureAccessors(accessors: AiDisclosureAccessors) {
    val filterClass = context.mutableClassDefBy(TIMELINE_FILTER_DESCRIPTOR)

    fun patchHelper(
        name: String,
        parameters: String,
        returnType: String,
        additionalRegisters: Int = 0,
        replaceBody: Boolean = false,
        instructions: String,
    ) {
        val matches =
            filterClass.methods.filter { method ->
                method.name == name &&
                    method.parameterTypes.joinToString("") == parameters &&
                    method.returnType == returnType
            }
        if (matches.size != 1) {
            throw PatchException(
                "Expected one X-Lite timeline helper $name($parameters)$returnType, found " +
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
                    ?: throw PatchException("X-Lite helper $name has no implementation")
            while (implementation.instructions.isNotEmpty()) {
                implementation.removeInstruction(implementation.instructions.lastIndex)
            }
            method.addInstructions(0, instructions.trimIndent())
            return
        }

        val insertionIndex =
            if (additionalRegisters == 0) 0 else originalMethod.numberOfParameterRegistersLogical
        method.addInstructions(insertionIndex, instructions.trimIndent())
    }

    patchHelper(
        name = CONTENT_DISCLOSURE_HELPER,
        parameters = OBJECT_DESCRIPTOR,
        returnType = OBJECT_DESCRIPTOR,
        additionalRegisters = 1,
        replaceBody = true,
        instructions =
            """
                # Timeline post results are a sealed union; tombstones have no disclosure.
                move-object/from16 v0, p0
                check-cast v0, ${accessors.timelinePostDescriptor}
                ${accessors.timelinePostResultRead}
                instance-of v1, v0, ${accessors.contextualPostDescriptor}
                if-eqz v1, :piko_xlite_no_contextual_post_result
                check-cast v0, ${accessors.contextualPostDescriptor}
                ${accessors.contextualCanonicalPostRead}
                ${accessors.canonicalContentDisclosureRead}
                return-object v0
                :piko_xlite_no_contextual_post_result
                const/4 v0, 0x0
                return-object v0
            """.trimIndent(),
    )
    patchHelper(
        name = HAS_AI_DISCLOSURE_HELPER,
        parameters = OBJECT_DESCRIPTOR,
        returnType = "Z",
        instructions =
            """
                check-cast p0, ${accessors.contentDisclosureDescriptor}
                ${accessors.hasAiDisclosureRead}
                return p0
            """.trimIndent(),
    )
    patchHelper(
        name = SOURCE_HELPER,
        parameters = OBJECT_DESCRIPTOR,
        returnType = OBJECT_DESCRIPTOR,
        instructions =
            """
                check-cast p0, ${accessors.contentDisclosureDescriptor}
                ${accessors.sourceRead}
                return-object p0
            """.trimIndent(),
    )
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
