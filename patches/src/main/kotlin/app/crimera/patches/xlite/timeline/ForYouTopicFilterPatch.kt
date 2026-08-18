package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.Groups
import app.crimera.patches.xlite.settings.SettingReadRegisterConstraint
import app.crimera.patches.xlite.settings.choice
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.injectRead
import app.crimera.patches.xlite.settings.multiChoice
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.toggle
import app.crimera.patches.xlite.settings.xLiteSettings
import app.crimera.patches.xlite.models.fieldForToStringLabel
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.FOR_YOU_TOPIC_FILTER_DESCRIPTOR
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val SET_DESCRIPTOR = "Ljava/util/Set;"
private const val TIMELINE_TYPE_DESCRIPTOR = "Lcom/x/models/timelines/TimelineType;"
private const val HOME_TIMELINE_PACKAGE = "Lcom/x/android/main/"
private const val TOPIC_FILTER_ENABLED_ID = "xlite.content.topic_filtering.enabled"
private const val TOPIC_FILTER_TOPICS_ID = "xlite.content.topic_filtering.topics"

private val HOME_TOPIC_OPTIONS =
    listOf(
        choice("1925952771733262336", "piko_xlite_topic_filter_politics"),
        choice("1000000000000000034", "piko_xlite_topic_filter_sports"),
        choice("1000000000000000033", "piko_xlite_topic_filter_business"),
        choice("1000000000000000031", "piko_xlite_topic_filter_science"),
        choice("1000000000000000032", "piko_xlite_topic_filter_entertainment"),
        choice("1925953013547450368", "piko_xlite_topic_filter_ai"),
        choice("1925949766673797120", "piko_xlite_topic_filter_gaming"),
        choice("1925949693290295298", "piko_xlite_topic_filter_crypto"),
        choice("42", "piko_xlite_topic_filter_videos"),
    )

private object HomeTimelineQueryFingerprint : Fingerprint(
    definingClass = HOME_TIMELINE_PACKAGE,
    name = "name",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("HomeTimeline")),
)

private data class ResolvedForYouRequestTarget(
    val requestFingerprint: Fingerprint,
    val queryConstructor: Method,
    val topicParameterIndex: Int,
)

@Suppress("unused")
val xLiteForYouTopicFilterPatch =
    bytecodePatch(
        name = "X-Lite: Filter For You by topic",
        description = "Restricts the X-Lite For You timeline to selected topics.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        val (topicFilterEnabled, topicFilterTopics) =
            xLiteSettings {
                category(Categories.CONTENT) {
                    group(Groups.TOPIC_FILTERING) {
                        toggle(
                            id = TOPIC_FILTER_ENABLED_ID,
                            strings = settingStrings("piko_xlite_topic_filtering_enabled"),
                            order = 100,
                            defaultValue = false,
                            rebootApp = true,
                        ) to
                            multiChoice(
                                id = TOPIC_FILTER_TOPICS_ID,
                                strings = settingStrings("piko_xlite_topic_filtering_topics"),
                                order = 200,
                                defaultValue = emptySet(),
                                options = HOME_TOPIC_OPTIONS,
                            )
                    }
                }
            }

        execute {
            val target = resolveForYouRequestTarget()
            val matches = target.requestFingerprint.scopedMatchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite For You topic request builder, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            val match = matches.single()
            val method = match.method
            val constructorIndex = match.instructionMatches.last().index
            val constructorInstruction = method.instructions[constructorIndex]
            val constructorReference =
                constructorInstruction.getReference<MethodReference>()
                    ?: throw PatchException(
                        "X-Lite For You topic request constructor reference is missing in $method",
                    )
            if (!constructorReference.matches(target.queryConstructor)) {
                throw PatchException(
                    "X-Lite For You topic request constructor changed: $constructorReference",
                )
            }

            val topicRegister = constructorInstruction.topicArgumentRegister(
                target.queryConstructor,
                target.topicParameterIndex,
            )
            if (topicRegister !in 0..15) {
                throw PatchException(
                    "X-Lite For You topic list uses unsupported register v$topicRegister; " +
                        "expected a four-bit register",
                )
            }

            val enabledRead =
                topicFilterEnabled.injectRead(
                    method = method,
                    index = constructorIndex,
                    excludedRegisters = listOf(topicRegister),
                    registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                )
            val topicsRead =
                topicFilterTopics.injectRead(
                    method = method,
                    index = enabledRead.nextIndex,
                    excludedRegisters = listOf(topicRegister, enabledRead.register),
                    registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                )
            method.addInstructions(
                topicsRead.nextIndex,
                """
                    invoke-static {v$topicRegister, v${enabledRead.register}, v${topicsRead.register}}, $FOR_YOU_TOPIC_FILTER_DESCRIPTOR->resolveForYouTopicIds(Ljava/util/List;ZLjava/util/Set;)Ljava/util/List;
                    move-result-object v$topicRegister
                """.trimIndent(),
            )
        }
    }

context(context: BytecodePatchContext)
private fun resolveForYouRequestTarget(): ResolvedForYouRequestTarget {
    val queryMatches = HomeTimelineQueryFingerprint.scopedMatchAll()
    if (queryMatches.size != 1) {
        throw PatchException(
            "Expected one X-Lite HomeTimeline query class, found ${queryMatches.size}: " +
                queryMatches.joinToString { it.originalClassDef.type },
        )
    }

    val queryClass = queryMatches.single().originalClassDef
    val queryToString = queryClass.methods.singleOrNull { method ->
        method.name == "toString" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == STRING_DESCRIPTOR
    } ?: throw PatchException("X-Lite HomeTimeline query has no toString(): $queryClass")
    val topicField = queryToString.fieldForToStringLabel(", topic_ids=")
    val constructorCandidates =
        queryClass.methods.mapNotNull { constructor ->
            if (constructor.name != "<init>" || constructor.returnType != "V") return@mapNotNull null
            val topicParameterIndex = constructor.parameterIndexForField(topicField) ?: return@mapNotNull null
            constructor to topicParameterIndex
        }

    if (constructorCandidates.size != 1) {
        throw PatchException(
            "Expected one X-Lite HomeTimeline constructor carrying topic_ids, found " +
                "${constructorCandidates.size}: ${constructorCandidates.joinToString { it.first.toString() }}",
        )
    }

    val (constructor, topicParameterIndex) = constructorCandidates.single()
    val constructorParameters = constructor.parameterTypes.map(CharSequence::toString)
    val requestFingerprint =
        Fingerprint(
            parameters = listOf(OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR),
            returnType = OBJECT_DESCRIPTOR,
            filters =
                listOf(
                    string("requestType"),
                    fieldAccess(
                        opcode = Opcode.SGET_OBJECT,
                        definingClass = TIMELINE_TYPE_DESCRIPTOR,
                        name = "FOR_YOU",
                        type = TIMELINE_TYPE_DESCRIPTOR,
                    ),
                    methodCall(
                        definingClass = queryClass.type,
                        name = "<init>",
                        parameters = constructorParameters,
                        returnType = "V",
                    ),
                ),
        )
    return ResolvedForYouRequestTarget(
        requestFingerprint = requestFingerprint,
        queryConstructor = constructor,
        topicParameterIndex = topicParameterIndex,
    )
}

private fun Method.parameterIndexForField(field: FieldReference): Int? {
    val implementation = implementation ?: return null
    val instructions = implementation.instructions.toList()
    val parameterRegisterCount = parameterTypes.sumOf { type -> type.toString().registerWidth() }
    val thisRegister = implementation.registerCount - parameterRegisterCount - 1
    val writes =
        instructions.withIndex().filter { (_, instruction) ->
            instruction.opcode == Opcode.IPUT_OBJECT &&
                instruction.getReference<FieldReference>()?.toString() == field.toString()
        }
    if (writes.size != 1) return null

    val write = writes.single().value as? TwoRegisterInstruction ?: return null
    if (write.registerB != thisRegister) return null
    val sourceRegister = instructions.resolveObjectOriginRegister(writes.single().index, write.registerA)
    val parameterIndex = sourceRegister - thisRegister - 1
    if (parameterIndex !in parameterTypes.indices) return null
    if (parameterTypes[parameterIndex].toString() != LIST_DESCRIPTOR) return null
    return parameterIndex
}

private val MOVE_OBJECT_OPCODES =
    setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)

private fun List<Instruction>.resolveObjectOriginRegister(
    writeIndex: Int,
    destinationRegister: Int,
): Int {
    var register = destinationRegister
    var searchEnd = writeIndex
    val visitedRegisters = mutableSetOf<Int>()
    while (visitedRegisters.add(register)) {
        val move =
            (searchEnd - 1 downTo 0).firstOrNull { index ->
                val instruction = this[index]
                if (instruction.opcode !in MOVE_OBJECT_OPCODES) return@firstOrNull false
                val moveInstruction = instruction as? TwoRegisterInstruction ?: return@firstOrNull false
                moveInstruction.registerA == register
            } ?: return register
        val moveInstruction = this[move] as TwoRegisterInstruction
        register = moveInstruction.registerB
        searchEnd = move
    }
    return register
}

private fun Instruction.topicArgumentRegister(
    constructor: Method,
    topicParameterIndex: Int,
): Int {
    val argumentRegisters = registersUsed
    val parameterTypes = constructor.parameterTypes.map(CharSequence::toString)
    val expectedRegisterCount = 1 + parameterTypes.sumOf { type -> type.registerWidth() }
    if (argumentRegisters.size != expectedRegisterCount) {
        throw PatchException(
            "Unexpected X-Lite HomeTimeline constructor register span: " +
                "expected $expectedRegisterCount, found ${argumentRegisters.size}",
        )
    }
    val argumentOffset =
        1 + parameterTypes
            .take(topicParameterIndex)
            .sumOf { type -> type.registerWidth() }
    return argumentRegisters[argumentOffset]
}

private fun String.registerWidth(): Int = if (this == "J" || this == "D") 2 else 1

private fun MethodReference.matches(method: Method): Boolean =
    definingClass == method.definingClass &&
        name == method.name &&
        returnType == method.returnType &&
        parameterTypes.map(CharSequence::toString) == method.parameterTypes.map(CharSequence::toString)
