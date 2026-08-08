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
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val xLiteHideAiGeneratedPostsPatch =
    bytecodePatch(
        name = "X-Lite: Hide AI-generated posts",
        description = "Hides posts disclosed as AI-generated from X-Lite timelines. You can choose " +
            "whether to filter user-marked posts, automatically-detected posts, or both.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

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
                hasAiDisclosureGetter = accessors.hasAiDisclosureGetter,
                sourceGetter = accessors.sourceGetter,
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

private const val CONTENT_DISCLOSURE_DESCRIPTOR = "Lcom/x/models/ContentDisclosure;"
private const val TIMELINE_POST_DESCRIPTOR = "Lcom/x/models/timelines/items/UrtTimelinePost;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val SOURCE_GETTER_NAME = "getAiGeneratedDetectionSource"
private const val HAS_AI_GETTER_NAME = "getHasAIGeneratedDisclosure"
private const val POST_DISCLOSURE_GETTER_NAME = "getContentDisclosure"
private const val CONTENT_DISCLOSURE_HELPER = "getContentDisclosure"
private const val HAS_AI_DISCLOSURE_HELPER = "hasAiGeneratedDisclosure"
private const val SOURCE_HELPER = "getAiDetectionSource"

private data class AiDisclosureAccessors(
    val contentDisclosureDescriptor: String,
    val contentDisclosureGetter: String,
    val hasAiDisclosureGetter: String,
    val sourceGetter: String,
)

context(context: BytecodePatchContext)
private fun resolveAiDisclosureAccessors(): AiDisclosureAccessors {
    val contentDisclosureClass = context.mutableClassDefBy(CONTENT_DISCLOSURE_DESCRIPTOR)
    val sourceGetter =
        requireExactlyOne(
            "X-Lite content disclosure source getter",
            contentDisclosureClass.methods.filter { method ->
                method.name == SOURCE_GETTER_NAME && method.parameterTypes.isEmpty()
            },
        )
    val hasAiDisclosureGetter =
        requireExactlyOne(
            "X-Lite AI disclosure getter",
            contentDisclosureClass.methods.filter { method ->
                method.name == HAS_AI_GETTER_NAME && method.parameterTypes.isEmpty()
            },
        )
    val timelinePostClass = context.mutableClassDefBy(TIMELINE_POST_DESCRIPTOR)
    val contentDisclosureGetter =
        requireExactlyOne(
            "X-Lite timeline post content disclosure getter",
            timelinePostClass.methods.filter { method ->
                method.name == POST_DISCLOSURE_GETTER_NAME && method.parameterTypes.isEmpty()
            },
        )

    if (!sourceGetter.returnType.startsWith("L") || !sourceGetter.returnType.endsWith(";")) {
        throw PatchException(
            "X-Lite content disclosure source getter does not return an object: $sourceGetter",
        )
    }
    if (hasAiDisclosureGetter.returnType != "Z") {
        throw PatchException(
            "X-Lite AI disclosure getter does not return a boolean: $hasAiDisclosureGetter",
        )
    }
    if (contentDisclosureGetter.returnType != CONTENT_DISCLOSURE_DESCRIPTOR) {
        throw PatchException(
            "X-Lite timeline post disclosure type mismatch: $contentDisclosureGetter",
        )
    }

    return AiDisclosureAccessors(
        contentDisclosureDescriptor = CONTENT_DISCLOSURE_DESCRIPTOR,
        contentDisclosureGetter = contentDisclosureGetter.smaliReference(),
        hasAiDisclosureGetter = hasAiDisclosureGetter.smaliReference(),
        sourceGetter = sourceGetter.smaliReference(),
    )
}

context(context: BytecodePatchContext)
private fun patchAiDisclosureAccessors(
    contentDisclosureDescriptor: String,
    contentDisclosureGetter: String,
    hasAiDisclosureGetter: String,
    sourceGetter: String,
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
        parameters = TIMELINE_POST_DESCRIPTOR,
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
                invoke-virtual {p0}, $hasAiDisclosureGetter
                move-result p0
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
                invoke-virtual {p0}, $sourceGetter
                move-result-object p0
                return-object p0
            """.trimIndent(),
    )
}

private fun MethodReference.smaliReference(): String =
    "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

private fun <T> requireExactlyOne(target: String, matches: List<T>): T {
    if (matches.size == 1) return matches.single()
    throw PatchException("Expected one $target, found ${matches.size}: ${matches.joinToString()}")
}
