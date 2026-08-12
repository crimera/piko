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
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

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
            patchAiDisclosureAccessors(
                contentDisclosureDescriptor = accessors.contentDisclosureDescriptor,
                contentDisclosureGetter = accessors.contentDisclosureGetter,
                hasAiDisclosureField = accessors.hasAiDisclosureField,
                sourceField = accessors.sourceField,
            )

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
private const val POST_DISCLOSURE_GETTER_NAME = "getContentDisclosure"
private const val CONTENT_DISCLOSURE_HELPER = "getContentDisclosure"
private const val HAS_AI_DISCLOSURE_HELPER = "hasAiGeneratedDisclosure"
private const val SOURCE_HELPER = "getAiDetectionSource"

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
    val contentDisclosureDescriptor: String,
    val contentDisclosureGetter: String,
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
    val hasAiDisclosureField =
        contentDisclosureMatch.fieldForToStringLabel(", hasAIGeneratedDisclosure=")
    val sourceField =
        contentDisclosureMatch.fieldForToStringLabel(", aiGeneratedDetectionSource=")
    contentDisclosureClass.makePublic(hasAiDisclosureField)
    contentDisclosureClass.makePublic(sourceField)

    val timelinePostClass = resolveTimelinePostModelClass()
    val postGetterCandidates =
        timelinePostClass.methods.filter { method ->
            AccessFlags.PUBLIC.isSet(method.accessFlags) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == contentDisclosureDescriptor
        }
    val contentDisclosureGetter =
        postGetterCandidates.singleOrNull { it.name == POST_DISCLOSURE_GETTER_NAME }
            ?: requireExactlyOne(
                "X-Lite timeline post content disclosure accessor",
                postGetterCandidates,
            )
    if (!sourceField.type.startsWith("L") || !sourceField.type.endsWith(";")) {
        throw PatchException("X-Lite content disclosure source is not an object: $sourceField")
    }
    if (hasAiDisclosureField.type != "Z") {
        throw PatchException("X-Lite AI disclosure field is not a boolean: $hasAiDisclosureField")
    }
    return AiDisclosureAccessors(
        contentDisclosureDescriptor = contentDisclosureDescriptor,
        contentDisclosureGetter = contentDisclosureGetter.smaliReference(),
        hasAiDisclosureField = hasAiDisclosureField.toString(),
        sourceField = sourceField.toString(),
    )
}

context(context: BytecodePatchContext)
private fun patchAiDisclosureAccessors(
    contentDisclosureDescriptor: String,
    contentDisclosureGetter: String,
    hasAiDisclosureField: String,
    sourceField: String,
) {
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
                invoke-virtual {p0}, $contentDisclosureGetter
                move-result-object p0
                return-object p0
            """.trimIndent(),
    )
    patchHelper(
        name = HAS_AI_DISCLOSURE_HELPER,
        parameters = OBJECT_DESCRIPTOR,
        returnType = "Z",
        instructions =
            """
                check-cast p0, $contentDisclosureDescriptor
                iget-boolean p0, p0, $hasAiDisclosureField
                return p0
            """.trimIndent(),
    )
    patchHelper(
        name = SOURCE_HELPER,
        parameters = OBJECT_DESCRIPTOR,
        returnType = OBJECT_DESCRIPTOR,
        instructions =
            """
                check-cast p0, $contentDisclosureDescriptor
                iget-object p0, p0, $sourceField
                return-object p0
            """.trimIndent(),
    )
}

private fun app.morphe.patcher.util.proxy.mutableTypes.MutableClass.makePublic(field: FieldReference) {
    val definition = fields.singleOrNull { candidate -> candidate.toString() == field.toString() }
        ?: throw PatchException("X-Lite content disclosure field definition was not found: $field")
    val nonPublicFlags = AccessFlags.PRIVATE.value or AccessFlags.PROTECTED.value
    definition.accessFlags =
        (definition.accessFlags and nonPublicFlags.inv()) or AccessFlags.PUBLIC.value
}

private fun MethodReference.smaliReference(): String =
    "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

private fun <T> requireExactlyOne(target: String, matches: List<T>): T {
    if (matches.size == 1) return matches.single()
    throw PatchException("Expected one $target, found ${matches.size}: ${matches.joinToString()}")
}
