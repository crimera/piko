package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.Groups
import app.crimera.patches.xlite.settings.SettingReadRegisterConstraint
import app.crimera.patches.xlite.settings.choice
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.injectRead
import app.crimera.patches.xlite.settings.multiChoice
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteSettings
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

@Suppress("unused")
val xLiteHideAiGeneratedPostsPatch =
    bytecodePatch(
        name = "X-Lite: Hide AI-generated posts",
        description = "Hides posts disclosed as AI-generated from X-Lite timelines. You can choose " +
            "whether to filter user-marked posts, automatically-detected posts, or both.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)
        dependsOn(xLiteTimelineModelAdapterPatch)

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
                                ),
                        )
                    }
                }
            }

        execute {
            val accessors = resolveAiDisclosureAccessors()
            patchAiDisclosureAccessors(accessors)

            val matches = XLiteTimelineSuccessFingerprint.matchAll()
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

private object ContextualPostModelFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    strings = listOf("ContextualPost(canonicalPost=", ", quotedPost="),
)

private object CanonicalPostModelFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    strings = listOf("CanonicalPost(id=", ", contentDisclosure="),
)

private object ContentDisclosureModelFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    strings =
        listOf(
            "ContentDisclosure(hasPaidPromotionDisclosure=",
            ", hasAIGeneratedDisclosure=",
            ", aiGeneratedDetectionSource=",
        ),
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
    val contentDisclosureMatches = ContentDisclosureModelFingerprint.matchAll()
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

    val timelinePostMatch = resolveTimelinePostModelMatch()
    val timelinePostClass = timelinePostMatch.classDef
    val timelinePostResultField =
        timelinePostMatch.fieldForToStringLabel("UrtTimelinePost(postResult=")

    val contextualPostMatch = ContextualPostModelFingerprint.requireSingle("contextual post model")
    val contextualPostClass = contextualPostMatch.classDef
    val canonicalPostMatch = CanonicalPostModelFingerprint.requireSingle("canonical post model")
    val canonicalPostClass = canonicalPostMatch.classDef
    val contextualCanonicalPostField =
        requireExactlyOne(
            "X-Lite contextual canonical-post field",
            contextualPostClass.fields.filter { field ->
                field.type == canonicalPostClass.type && !AccessFlags.STATIC.isSet(field.accessFlags)
            },
        )
    val canonicalContentDisclosureField =
        requireExactlyOne(
            "X-Lite canonical-post content-disclosure field",
            canonicalPostClass.fields.filter { field ->
                field.type == contentDisclosureDescriptor && !AccessFlags.STATIC.isSet(field.accessFlags)
            },
        )

    timelinePostClass.makePublic(timelinePostResultField)
    contextualPostClass.makePublic(contextualCanonicalPostField)
    canonicalPostClass.makePublic(canonicalContentDisclosureField)
    if (!sourceField.type.startsWith("L") || !sourceField.type.endsWith(";")) {
        throw PatchException("X-Lite content disclosure source is not an object: $sourceField")
    }
    if (hasAiDisclosureField.type != "Z") {
        throw PatchException("X-Lite AI disclosure field is not a boolean: $hasAiDisclosureField")
    }
    return AiDisclosureAccessors(
        timelinePostDescriptor = timelinePostClass.type,
        timelinePostResultField = timelinePostResultField.toString(),
        contextualPostDescriptor = contextualPostClass.type,
        contextualCanonicalPostField = contextualCanonicalPostField.toString(),
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

context(_: BytecodePatchContext)
private fun Fingerprint.requireSingle(target: String): Match {
    val matches = matchAll()
    if (matches.size == 1) return matches.single()
    throw PatchException(
        "Expected one X-Lite $target, found ${matches.size}: " +
            matches.joinToString { it.originalMethod.toString() },
    )
}

private fun <T> requireExactlyOne(target: String, matches: List<T>): T {
    if (matches.size == 1) return matches.single()
    throw PatchException("Expected one $target, found ${matches.size}: ${matches.joinToString()}")
}
