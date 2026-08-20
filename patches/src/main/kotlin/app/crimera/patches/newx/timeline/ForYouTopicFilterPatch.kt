package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.customScreen
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.FOR_YOU_TOPIC_FILTER_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.FOR_YOU_TOPIC_FILTER_FRAGMENT_DESCRIPTOR
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val ARRAY_LIST_DESCRIPTOR = "Ljava/util/ArrayList;"
private const val TIMELINE_TYPE_DESCRIPTOR = "Lcom/x/models/timelines/TimelineType;"
private const val HOME_TIMELINE_PACKAGE = "Lcom/x/android/main/"
private const val HOME_MODELS_PACKAGE = "Lcom/x/models/"
private const val HOME_FILTER_GROUP_FILTER_TYPE_LABEL = "HomeFilterGroup(filterType="
private const val HOME_FILTER_GROUP_OPTIONS_LABEL = ", options="

private object HomeFilterGroupFingerprint : Fingerprint(
    definingClass = HOME_MODELS_PACKAGE,
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string(HOME_FILTER_GROUP_FILTER_TYPE_LABEL)),
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
val newXForYouTopicFilterPatch =
    bytecodePatch(
        name = "NewX: Filter For You by topic",
        description = "Restricts the NewX For You timeline to selected topics.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        newXSettings {
            category(Categories.TIMELINE) {
                customScreen(
                    id = "newx.content.topic_filtering.manage",
                    strings = settingStrings("piko_newx_topic_filtering"),
                    order = 100,
                    iconResourceName = "ic_vector_filter",
                    fragmentClassDescriptor = FOR_YOU_TOPIC_FILTER_FRAGMENT_DESCRIPTOR,
                )
            }
        }

        execute {
            patchHomeFilterGroupConstructor()

            val target = resolveForYouRequestTarget()
            val matches = target.requestFingerprint.scopedMatchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX For You topic request builder, found ${matches.size}: " +
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
                        "NewX For You topic request constructor reference is missing in $method",
                    )
            if (!constructorReference.matches(target.queryConstructor)) {
                throw PatchException(
                    "NewX For You topic request constructor changed: $constructorReference",
                )
            }

            val topicRegister = constructorInstruction.topicArgumentRegister(
                target.queryConstructor,
                target.topicParameterIndex,
            )
            method.addInstructions(
                constructorIndex,
                """
                    invoke-static/range {v$topicRegister .. v$topicRegister}, $FOR_YOU_TOPIC_FILTER_DESCRIPTOR->resolveForYouTopicIds(Ljava/util/List;)Ljava/util/List;
                    move-result-object v$topicRegister
                """.trimIndent(),
            )
        }
    }

context(context: BytecodePatchContext)
private fun patchHomeFilterGroupConstructor() {
    val matches = HomeFilterGroupFingerprint.scopedMatchAll()
    if (matches.size != 1) {
        throw PatchException(
            "Expected one NewX HomeTimelineFilters group model, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
    }

    val match = matches.single()
    val filterTypeField = match.fieldForToStringLabel(HOME_FILTER_GROUP_FILTER_TYPE_LABEL)
    val optionsField = match.fieldForToStringLabel(HOME_FILTER_GROUP_OPTIONS_LABEL)
    val parameterTypes = { method: Method -> method.parameterTypes.map(CharSequence::toString) }
    val constructors = match.classDef.methods.filter { method ->
        val parameters = parameterTypes(method)
        method.name == "<init>" &&
            method.returnType == "V" &&
            parameters.size == 4 &&
            parameters[0].startsWith(HOME_MODELS_PACKAGE) &&
            parameters[1] == STRING_DESCRIPTOR &&
            parameters[2] == "Z" &&
            parameters[3] in listOf(LIST_DESCRIPTOR, ARRAY_LIST_DESCRIPTOR)
    }
    if (constructors.size != 1) {
        throw PatchException(
            "Expected one NewX HomeTimelineFilters group constructor, found " +
                "${constructors.size}: ${constructors.joinToString()}",
        )
    }

    val constructor = constructors.single() as? MutableMethod
        ?: throw PatchException("NewX HomeTimelineFilters group constructor is not mutable")
    val filterTypeParameterIndex = constructor.parameterIndexForField(filterTypeField)
    val optionsParameterIndex = constructor.parameterIndexForField(
        optionsField,
        setOf(LIST_DESCRIPTOR, ARRAY_LIST_DESCRIPTOR),
    )
    if (filterTypeParameterIndex != 0 || optionsParameterIndex != 3) {
        throw PatchException(
            "NewX HomeFilterGroup constructor parameter mapping changed: " +
                "filterType=$filterTypeParameterIndex, options=$optionsParameterIndex",
        )
    }

    val superIndex = constructor.instructions.withIndex().firstOrNull { (_, instruction) ->
        if (instruction.opcode != Opcode.INVOKE_DIRECT) return@firstOrNull false
        val reference = instruction.getReference<MethodReference>() ?: return@firstOrNull false
        reference.name == "<init>" && reference.definingClass != constructor.definingClass
    }?.index ?: throw PatchException(
        "NewX HomeTimelineFilters group constructor has no super call: $constructor",
    )

    constructor.addInstructions(
        superIndex + 1,
        "invoke-static {p1, p4}, $FOR_YOU_TOPIC_FILTER_DESCRIPTOR->captureTopicOptions(Ljava/lang/Object;Ljava/lang/Object;)V",
    )
}

context(context: BytecodePatchContext)
private fun resolveForYouRequestTarget(): ResolvedForYouRequestTarget {
    val queryMatches = HomeTimelineQueryFingerprint.scopedMatchAll()
    if (queryMatches.size != 1) {
        throw PatchException(
            "Expected one NewX HomeTimeline query class, found ${queryMatches.size}: " +
                queryMatches.joinToString { it.originalClassDef.type },
        )
    }

    val queryClass = queryMatches.single().originalClassDef
    val queryToString = queryClass.methods.singleOrNull { method ->
        method.name == "toString" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == STRING_DESCRIPTOR
    } ?: throw PatchException("NewX HomeTimeline query has no toString(): $queryClass")
    val topicField = queryToString.fieldForToStringLabel(", topic_ids=")
    val queryDocuments = queryClass.methods.filter { method ->
        method.returnType == STRING_DESCRIPTOR && method.containsStringFragment("query HomeTimeline(")
    }
    val topicWriters = queryClass.methods.filter { method ->
        method.returnType == "V" && method.containsStringFragment("topic_ids")
    }
    if (queryDocuments.size != 1 ||
        !queryDocuments.single().containsStringFragment("home_timeline_urt") ||
        !queryDocuments.single().containsStringFragment("topic_ids:") ||
        topicWriters.size != 1 ||
        !topicWriters.single().containsFieldReference(topicField)) {
        throw PatchException(
            "NewX HomeTimeline query does not serialize the expected topic_ids field: " +
                "documents=${queryDocuments.joinToString { it.toString() }}, " +
                "writers=${topicWriters.joinToString { it.toString() }}",
        )
    }
    val constructorCandidates =
        queryClass.methods.mapNotNull { constructor ->
            if (constructor.name != "<init>" || constructor.returnType != "V") return@mapNotNull null
            val topicParameterIndex = constructor.parameterIndexForField(
                topicField,
                setOf(LIST_DESCRIPTOR),
            ) ?: return@mapNotNull null
            constructor to topicParameterIndex
        }

    if (constructorCandidates.size != 1) {
        throw PatchException(
            "Expected one NewX HomeTimeline constructor carrying topic_ids, found " +
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

private fun Method.parameterIndexForField(
    field: FieldReference,
    expectedParameterTypes: Set<String> = emptySet(),
): Int? {
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
    if (expectedParameterTypes.isNotEmpty() &&
        parameterTypes[parameterIndex].toString() !in expectedParameterTypes
    ) {
        return null
    }
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
            "Unexpected NewX HomeTimeline constructor register span: " +
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

private fun Method.containsStringFragment(fragment: String): Boolean =
    implementation?.instructions?.any { instruction ->
        instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.StringReference>()
            ?.string
            ?.contains(fragment) == true
    } == true

private fun Method.containsFieldReference(field: FieldReference): Boolean =
    implementation?.instructions?.any { instruction ->
        instruction.getReference<FieldReference>()?.toString() == field.toString()
    } == true

private fun MethodReference.matches(method: Method): Boolean =
    definingClass == method.definingClass &&
        name == method.name &&
        returnType == method.returnType &&
        parameterTypes.map(CharSequence::toString) == method.parameterTypes.map(CharSequence::toString)
