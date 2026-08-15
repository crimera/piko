package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.Groups
import app.crimera.patches.xlite.settings.SettingReadRegisterConstraint
import app.crimera.patches.xlite.settings.choice
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.injectRead
import app.crimera.patches.xlite.settings.multiChoice
import app.crimera.patches.xlite.models.resolvedXLitePostModels
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
import app.morphe.util.getReference
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
    val timelinePostResultField: String,
    val contextualPostDescriptor: String,
    val contextualCanonicalPostField: String,
    val canonicalContentDisclosureField: String,
    val contentDisclosureDescriptor: String,
    val hasAiDisclosureField: String,
    val sourceField: String,
)

context(context: BytecodePatchContext)
private fun resolveAiDisclosureAccessors(): AiDisclosureAccessors {
    val contentDisclosureMatches =
        ContentDisclosureModelFingerprint.scopedMatchAll()
    if (contentDisclosureMatches.size != 1) {
        throw PatchException(
            "Expected one X-Lite content disclosure model, found ${contentDisclosureMatches.size}: " +
                contentDisclosureMatches.joinToString { it.originalMethod.toString() },
        )
    }
    val contentDisclosureMatch = contentDisclosureMatches.single()
    val contentDisclosureClass = contentDisclosureMatch.classDef
    val contentDisclosureDescriptor = contentDisclosureClass.type
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
    contentDisclosureClass.makePublic(hasAiDisclosureField)
    contentDisclosureClass.makePublic(sourceField)

    val timelineModels = resolvedXLiteTimelineModels()
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

    contextualPostClass.makePublic(postModels.contextualCanonicalPostField)
    canonicalPostClass.makePublic(canonicalContentDisclosureField)
    if (!sourceField.type.startsWith("L") || !sourceField.type.endsWith(";")) {
        throw PatchException("X-Lite content disclosure source is not an object: $sourceField")
    }
    if (hasAiDisclosureField.type != "Z") {
        throw PatchException("X-Lite AI disclosure field is not a boolean: $hasAiDisclosureField")
    }
    return AiDisclosureAccessors(
        timelinePostDescriptor = timelineModels.postDescriptor,
        timelinePostResultField = timelineModels.postResultField.toString(),
        contextualPostDescriptor = postModels.contextualPostDescriptor,
        contextualCanonicalPostField = postModels.contextualCanonicalPostField.toString(),
        canonicalContentDisclosureField = canonicalContentDisclosureField.toString(),
        contentDisclosureDescriptor = contentDisclosureDescriptor,
        hasAiDisclosureField = hasAiDisclosureField.toString(),
        sourceField = sourceField.toString(),
    )
}

context(context: BytecodePatchContext)
private fun patchAiDisclosureAccessors(accessors: AiDisclosureAccessors) {
    val filterClass = context.mutableClassDefBy(TIMELINE_FILTER_DESCRIPTOR)

    fun patchHelper(
        name: String,
        parameters: String,
        returnType: String,
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
        matches.single().addInstructions(0, instructions.trimIndent())
    }

    patchHelper(
        name = CONTENT_DISCLOSURE_HELPER,
        parameters = OBJECT_DESCRIPTOR,
        returnType = OBJECT_DESCRIPTOR,
        instructions =
            """
                check-cast p0, ${accessors.timelinePostDescriptor}
                iget-object p0, p0, ${accessors.timelinePostResultField}
                check-cast p0, ${accessors.contextualPostDescriptor}
                iget-object p0, p0, ${accessors.contextualCanonicalPostField}
                iget-object p0, p0, ${accessors.canonicalContentDisclosureField}
                return-object p0
            """.trimIndent(),
    )
    patchHelper(
        name = HAS_AI_DISCLOSURE_HELPER,
        parameters = OBJECT_DESCRIPTOR,
        returnType = "Z",
        instructions =
            """
                check-cast p0, ${accessors.contentDisclosureDescriptor}
                iget-boolean p0, p0, ${accessors.hasAiDisclosureField}
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
                iget-object p0, p0, ${accessors.sourceField}
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

private fun app.morphe.patcher.util.proxy.mutableTypes.MutableClass.makePublic(field: FieldReference) {
    val definition = fields.singleOrNull { candidate -> candidate.toString() == field.toString() }
        ?: throw PatchException("X-Lite content disclosure field definition was not found: $field")
    val nonPublicFlags = AccessFlags.PRIVATE.value or AccessFlags.PROTECTED.value
    definition.accessFlags =
        (definition.accessFlags and nonPublicFlags.inv()) or AccessFlags.PUBLIC.value
}

private fun <T> requireExactlyOne(target: String, matches: List<T>): T {
    if (matches.size == 1) return matches.single()
    throw PatchException("Expected one $target, found ${matches.size}: ${matches.joinToString()}")
}
