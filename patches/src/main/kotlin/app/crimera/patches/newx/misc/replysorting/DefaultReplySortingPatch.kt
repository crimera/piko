/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.replysorting

import app.crimera.patches.newx.misc.inlineactions.argumentRegister
import app.crimera.patches.newx.misc.inlineactions.normalControlFlowSuccessors
import app.crimera.patches.newx.misc.inlineactions.provesParameterValue
import app.crimera.patches.newx.misc.inlineactions.provesValueFromInstruction
import app.crimera.patches.newx.misc.inlineactions.reachableIndicesFrom
import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.settings.choice
import app.crimera.patches.settings.group
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.settings.singleChoice
import app.crimera.patches.settings.toggle
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.REPLY_SORTING_RESOLVER_DESCRIPTOR
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private fun isRelevanceSget(instruction: Instruction): Boolean {
    if (instruction.opcode != Opcode.SGET_OBJECT) return false
    val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: return false
    return field.name == "Relevance" && field.type == field.definingClass
}

private fun isComposeStateCall(instruction: Instruction, inputRegister: Int): Boolean {
    if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
        return false
    }
    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return reference.definingClass.startsWith("Landroidx/compose/runtime/") &&
        reference.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;") &&
        reference.returnType.startsWith("Landroidx/compose/runtime/") &&
        instruction.registersUsed == listOf(inputRegister)
}

private fun isComposeStateInitializer(instructions: List<Instruction>, sgetIndex: Int): Boolean {
    if (sgetIndex < 0 || sgetIndex + 3 >= instructions.size) return false
    val sget = instructions[sgetIndex] as? OneRegisterInstruction ?: return false
    if (!isRelevanceSget(sget)) return false

    val inputRegister = sget.registerA
    if (!isComposeStateCall(instructions[sgetIndex + 1], inputRegister)) return false

    val moveResult = instructions[sgetIndex + 2] as? OneRegisterInstruction ?: return false
    if (moveResult.opcode != Opcode.MOVE_RESULT_OBJECT || moveResult.registerA != inputRegister) {
        return false
    }

    val returnInstruction = instructions[sgetIndex + 3] as? OneRegisterInstruction ?: return false
    return returnInstruction.opcode == Opcode.RETURN_OBJECT &&
        returnInstruction.registerA == inputRegister
}

private const val ENUM_DESCRIPTOR = "Ljava/lang/Enum;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"

/** Kotlin coroutine StateFlow factories; the PostDetail InstanceKeeper stores the flow built here. */
private const val FLOW_PACKAGE = "Lkotlinx/coroutines/flow/"

/** A `Relevance` read of the resolved enum, with the register that holds its value after the read. */
internal data class ReplySortingSeed(val index: Int, val register: Int)

/** A resolved insertion to run after every resolver has finished. Applied in descending [index]. */
private class PlannedReplySortingHook(val index: Int, val applyHook: () -> Unit)

/**
 * Replaces the resolved `Relevance` seed in [register] with the configured default ranking mode
 * after [sgetIndex]. The register keeps its original type, so the consuming instruction range is
 * unchanged.
 */
internal fun MutableMethod.insertReplySortingDefault(
    sgetIndex: Int,
    register: Int,
    enumClass: String,
) {
    insertHook(
        index = sgetIndex + 1,
        // The old plain insertion left any incoming label on the instruction behind the seed, so a
        // branch that targeted it kept skipping the rewrite. Keep that policy rather than making
        // those paths run the hook.
        relocateBranchTargets = false,
    ) {
        constClass(register, enumClass)
        invokeStatic(
            methodReference(
                "$REPLY_SORTING_RESOLVER_DESCRIPTOR->getEnumDefault(Ljava/lang/Class;)Ljava/lang/Object;",
            ),
            register,
        )
        moveResult(register, "Ljava/lang/Object;")
        checkCast(register, enumClass)
    }
}

/**
 * True when the value that [seedRegister] holds after [seedIndex] is proven, on every path, to be
 * the argument of an invoke that [accepts] at that parameter. Branch joins, clobbers and copies made
 * on only some paths fail the proof, so a moved or shadowed register cannot stand in for the seed.
 */
private fun Method.seedFlowsIntoInvoke(
    seedIndex: Int,
    seedRegister: Int,
    accepts: (MethodReference, Int) -> Boolean,
): Boolean {
    val code = implementation?.instructions?.toList() ?: return false
    return code.withIndex().any { (index, instruction) ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            ?: return@any false
        reference.parameterTypes.indices.any { parameterIndex ->
            if (!accepts(reference, parameterIndex)) return@any false
            val register = instruction.argumentRegister(reference, parameterIndex) ?: return@any false
            provesValueFromInstruction(seedIndex, seedRegister, index, register)
        }
    }
}

/**
 * Every `Relevance` read of [enumClass] in this method whose value is proven to reach an argument
 * accepted by [accepts]. Returns the seeds in instruction order; callers decide the cardinality.
 */
internal fun Method.enumSeeds(
    enumClass: String,
    accepts: (MethodReference, Int) -> Boolean,
): List<ReplySortingSeed> {
    val code = implementation?.instructions?.toList().orEmpty()
    return code.withIndex().mapNotNull { (index, instruction) ->
        if (!isRelevanceSget(instruction) ||
            instruction.getReference<FieldReference>()?.definingClass != enumClass
        ) {
            return@mapNotNull null
        }
        val register = (instruction as OneRegisterInstruction).registerA
        ReplySortingSeed(index, register).takeIf { seedFlowsIntoInvoke(index, register, accepts) }
    }
}

/** Stable identity for a method across the distinct wrappers a fingerprint match can return. */
private fun Method.signatureKey(): String =
    "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

/**
 * Targets the NewX Compose post-detail timeline repository initialization that seeds
 * TimelineRankingMode.Relevance before the repository factory call.
 */
private object NewXComposeReplySortingFingerprint : Fingerprint(
    // The repository lookup can precede or follow the Relevance seed after R8 scheduling.
    strings = listOf("timelineRepository"),
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    isStatic = false,
    superclass = "Lkotlin/coroutines/jvm/internal/SuspendLambda;",
    interfaces = listOf("Lkotlin/jvm/functions/Function2;"),
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                name = "Relevance",
            ),
        ),
)

/**
 * Targets the synthetic FunctionReference that handles a reply-sorting choice from the sheet.
 * The callback owner and package are release-specific; resolve it from the stable Kotlin
 * function-reference shape and the semantic branch strings instead. The method name is not part
 * of the contract either: 12.33 alpha.02 un-merged the callback's `invoke` switch into private
 * per-case methods (`B(Object)`) while keeping the same body.
 */
private object NewXComposeReplySortingSelectionFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    isStatic = false,
    superclass = "Lkotlin/jvm/internal/FunctionReferenceImpl;",
    interfaces = listOf("Lkotlin/jvm/functions/Function1;"),
    filters =
        listOf(
            string("timelineRepository"),
            string("defaultUrtTimelineComponent"),
        ),
)

// R8 can extract the selection body from the erased callback into a typed post-detail method.
private fun replySortingSelectionMethodFingerprint(enumClass: String) = Fingerprint(
    definingClass = "Lcom/x/postdetail/",
    returnType = "V",
    parameters = listOf(enumClass),
    isStatic = false,
    strings = listOf("timelineRepository", "defaultUrtTimelineComponent"),
)

/**
 * Targets the PostDetail constructor that owns the reply-sorting UI state on 12.45.1+. The state is
 * an InstanceKeeper entry keyed by `post_detail_reply_ranking_mode` and holds a StateFlow seeded
 * from `TimelineRankingMode.Relevance`. Absent on 12.41 and 12.43, where the Compose state path
 * below is used instead.
 */
private object NewXPostDetailReplyRankingModeFingerprint : Fingerprint(
    definingClass = "Lcom/x/postdetail/",
    name = "<init>",
    returnType = "V",
    filters =
        listOf(
            string("post_detail_reply_ranking_mode"),
        ),
)

/**
 * Targets the Compose state initializer that seeds the reply-sorting sheet with
 * `mutableStateOf(TimelineRankingMode.Relevance)`. Used only when the InstanceKeeper-backed
 * PostDetail state is absent, since the Compose toast/sheet shape also matches unrelated initializers.
 */
private object NewXComposeReplySortingUiStateFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    interfaces = listOf("Lkotlin/jvm/functions/Function0;"),
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                name = "Relevance",
            ),
            // mutableStateOf(Relevance)
            methodCall(
                definingClass = "Landroidx/compose/runtime/",
                parameters = listOf("Ljava/lang/Object;"),
                returnType = "Landroidx/compose/runtime/",
                location = MatchAfterImmediately(),
            ),
            opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
            opcode(Opcode.RETURN_OBJECT, location = MatchAfterImmediately()),
        ),
)

/**
 * Targets the legacy post-detail conversation prefetch seed added in 12.29 and gated by a rollout
 * flag. The flag was retired in 12.41, so this only matches older releases.
 */
private object NewXComposeReplySortingLegacyPrefetchFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters =
        listOf(
            string("x_android_conversation_prefetch_enabled"),
        ),
)

/**
 * Targets the unconditional PostDetail constructor prefetch that replaced the flagged path. Its
 * request is built from the `TimelineRankingMode.Relevance` seed and logged with these two strings,
 * so they identify the inlined capability without relying on a rollout flag or an obfuscated owner.
 */
private object NewXComposeReplySortingPrefetchFingerprint : Fingerprint(
    definingClass = "Lcom/x/postdetail/",
    name = "<init>",
    returnType = "V",
    // Filters match in instruction order: the supersede log precedes the request it builds.
    filters =
        listOf(
            string("superseded by post "),
            string("PrefetchedConversationRequest"),
        ),
)

/**
 * Accepts the coroutine StateFlow factory (`(Object) -> StateFlow`) that wraps the InstanceKeeper
 * UI state. Proving the seed reaches this argument separates it from the prefetch request seed.
 */
internal fun isStateFlowFactoryArgument(reference: MethodReference, parameterIndex: Int): Boolean =
    reference.definingClass.startsWith(FLOW_PACKAGE) &&
        reference.returnType.startsWith(FLOW_PACKAGE) &&
        reference.parameterTypes[parameterIndex].toString() == OBJECT_DESCRIPTOR

/**
 * Validated selection: the check-cast that narrows the selected callback parameter to the resolved
 * ranking enum, and the field store of that same value. [register] is the store's value register,
 * which holds the chosen enum on every incoming path to [storeIndex].
 */
internal data class ReplySortingSelectionSite(
    val castIndex: Int?,
    val storeIndex: Int,
    val register: Int,
)

/**
 * Resolves the single cast-to-store tuple that persists the reply-sorting choice. The callback
 * parameter is located from the instance-method register layout, and the cast and store are proven
 * through the shared all-path provenance helpers, so branch-local moves and clobbers are handled
 * without assuming adjacency or fixed registers.
 */
internal fun resolveReplySortingSelectionSite(
    method: Method,
    enumClass: String,
    defaultUrtIndex: Int,
): ReplySortingSelectionSite {
    val implementation = method.implementation
        ?: throw PatchException("Reply sorting selection handler has no implementation")
    val parameterType = method.parameterTypes.singleOrNull()?.toString()
    if (AccessFlags.STATIC.isSet(method.accessFlags) ||
        parameterType != OBJECT_DESCRIPTOR && parameterType != enumClass
    ) {
        throw PatchException(
            "Unexpected reply sorting selection handler parameters: ${method.parameterTypes}",
        )
    }
    // The receiver and the single reference argument occupy the last two registers.
    val selectedParameterRegister = implementation.registerCount - 1
    if (selectedParameterRegister < 1) {
        throw PatchException("Invalid reply sorting selection handler register layout")
    }

    val instructions = implementation.instructions.toList()
    val anchorInstruction = instructions.getOrNull(defaultUrtIndex)
    if (anchorInstruction?.getReference<StringReference>()?.string != "defaultUrtTimelineComponent") {
        throw PatchException(
            "Reply sorting selection boundary index $defaultUrtIndex is not the defaultUrtTimelineComponent anchor in $method",
        )
    }
    val sources: List<Pair<Int?, Int>> =
        if (parameterType == enumClass) {
            listOf(null to selectedParameterRegister)
        } else {
            instructions.withIndex().mapNotNull { (index, instruction) ->
                if (index >= defaultUrtIndex || instruction.opcode != Opcode.CHECK_CAST) return@mapNotNull null
                val cast = instruction as? OneRegisterInstruction ?: return@mapNotNull null
                if (instruction.getReference<TypeReference>()?.type != enumClass) return@mapNotNull null
                if (!method.provesParameterValue(index, cast.registerA, selectedParameterRegister)) {
                    return@mapNotNull null
                }
                index to cast.registerA
            }
        }

    val successors = method.normalControlFlowSuccessors()
        ?: throw PatchException("Reply sorting selection handler has unsupported control flow: $method")
    val sites = sources.flatMap { (castIndex, sourceRegister) ->
        instructions.withIndex().mapNotNull { (storeIndex, instruction) ->
            if (storeIndex >= defaultUrtIndex || castIndex != null && storeIndex <= castIndex) {
                return@mapNotNull null
            }
            if (instruction.opcode != Opcode.IPUT_OBJECT) return@mapNotNull null
            val store = instruction as? TwoRegisterInstruction ?: return@mapNotNull null
            val fieldType = instruction.getReference<FieldReference>()?.type
            if (fieldType != OBJECT_DESCRIPTOR && fieldType != enumClass) return@mapNotNull null
            // A store in an unrelated returning arm cannot feed the boundary, so it is not a selection.
            if (defaultUrtIndex !in successors.reachableIndicesFrom(storeIndex).orEmpty()) {
                return@mapNotNull null
            }
            val storesSelection = if (castIndex == null) {
                method.provesParameterValue(storeIndex, store.registerA, sourceRegister)
            } else {
                method.provesValueFromInstruction(castIndex, sourceRegister, storeIndex, store.registerA)
            }
            ReplySortingSelectionSite(castIndex, storeIndex, store.registerA).takeIf { storesSelection }
        }
    }

    return requireExactlyOne(
        label = "reply sorting selection cast-to-store site for $enumClass",
        candidates = sites,
        describe = { site ->
            "$method cast@${site.castIndex} store@${site.storeIndex} " +
                "field=${instructions[site.storeIndex].getReference<FieldReference>()} v${site.register}"
        },
    )
}

/**
 * Remembers the chosen ranking mode immediately before the validated store. Branch targets are
 * relocated so every incoming path to the store runs the remember call, and the original store and
 * its later refresh stay intact.
 */
internal fun MutableMethod.installReplySortingSelectionHook(site: ReplySortingSelectionSite) {
    insertHook(
        index = site.storeIndex,
        relocateBranchTargets = true,
    ) {
        invokeStatic(
            methodReference("$REPLY_SORTING_RESOLVER_DESCRIPTOR->remember(Ljava/lang/Object;)V"),
            site.register,
        )
    }
}

@Suppress("unused")
val newXDefaultReplySortingPatch =
    patch(
        name = "NewX: Set default reply sorting",
        description = "Lets you choose the default reply sorting order for NewX post detail.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)
        category("NewX patches")

        newXSettings {
            category(Categories.POST_ACTIONS_MEDIA) {
                group(Groups.REPLY_SORTING) {
                    singleChoice(
                        id = "newx.timeline.default_reply_sorting",
                        strings = settingStrings("piko_newx_default_reply_sorting"),
                        order = 100,
                        defaultValue = "Relevance",
                        options =
                            listOf(
                                choice("Relevance", "piko_newx_reply_sort_relevance"),
                                choice("Recency", "piko_newx_reply_sort_recency"),
                                choice("Likes", "piko_newx_reply_sort_likes"),
                            ),
                    )

                    toggle(
                        id = "newx.timeline.remember_reply_sorting",
                        strings = settingStrings("piko_newx_remember_reply_sorting"),
                        order = 200,
                        defaultValue = false,
                    )
                }
            }
        }

        execute {
            // Resolve every seed before mutating anything. Several seeds can live in one method
            // (12.45.1's PostDetail constructor holds both the UI state and the prefetch), so each
            // hook is planned against the untouched instruction indices and applied last.
            val hooks = mutableListOf<PlannedReplySortingHook>()

            // Repository seed: the timeline repository initialization in invokeSuspend.
            val match =
                requireExactlyOne(
                    label = "NewX Compose reply sorting initializer",
                    candidates = NewXComposeReplySortingFingerprint.matchAllOrNull().orEmpty(),
                )
            val repositoryMethod = match.method
            val targetSget =
                requireExactlyOne(
                    label = "reply sorting initializer Relevance sget-object",
                    candidates = match.instructionMatches.filter { matchedInstruction ->
                        isRelevanceSget(matchedInstruction.instruction)
                    },
                )
            val targetSgetIndex = targetSget.index
            val sgetInstruction = targetSget.instruction as? OneRegisterInstruction
                ?: throw PatchException("Reply sorting initializer Relevance sget-object has no register")
            val sortRegister = sgetInstruction.registerA
            val fieldRef = sgetInstruction.getReference<FieldReference>()
                ?: throw PatchException("Missing field reference in reply sorting sget-object")
            val enumClass = fieldRef.definingClass
            val enumDefinition =
                runCatching { mutableClassDefBy(enumClass) }.getOrNull()
                    ?: throw PatchException("Resolved reply sorting enum is missing: $enumClass")
            if (enumDefinition.superclass != ENUM_DESCRIPTOR) {
                throw PatchException(
                    "Resolved reply sorting type $enumClass is not an enum: " +
                        "superclass=${enumDefinition.superclass}",
                )
            }
            hooks += PlannedReplySortingHook(targetSgetIndex) {
                repositoryMethod.insertReplySortingDefault(targetSgetIndex, sortRegister, enumClass)
            }

            // UI state. 12.45.1+ keeps the sheet state in a PostDetail InstanceKeeper StateFlow; the
            // Compose mutableStateOf initializer is the 12.41/12.43 shape and is never used when the
            // InstanceKeeper state exists (its Compose match then lands on unrelated code).
            val instanceKeeperMatch =
                requireAtMostOne(
                    label = "NewX PostDetail reply ranking mode InstanceKeeper constructor",
                    candidates = NewXPostDetailReplyRankingModeFingerprint.scopedMatchAllOrNull().orEmpty(),
                )
            if (instanceKeeperMatch != null) {
                val instanceKeeperMethod = instanceKeeperMatch.method
                val instanceKeeperSeed =
                    requireExactlyOne(
                        label = "NewX PostDetail reply ranking mode StateFlow seed for $enumClass",
                        candidates = instanceKeeperMethod.enumSeeds(enumClass, ::isStateFlowFactoryArgument),
                    )
                hooks += PlannedReplySortingHook(instanceKeeperSeed.index) {
                    instanceKeeperMethod.insertReplySortingDefault(
                        instanceKeeperSeed.index,
                        instanceKeeperSeed.register,
                        enumClass,
                    )
                }
            } else {
                val uiStateMatches = NewXComposeReplySortingUiStateFingerprint
                    .matchAllOrNull()
                    .orEmpty()
                val uiStateCandidates =
                    uiStateMatches.flatMap { candidate ->
                        candidate.instructionMatches
                            .filter { matchedInstruction ->
                                val field = matchedInstruction.instruction.getReference<FieldReference>()
                                field?.definingClass == enumClass &&
                                    isComposeStateInitializer(
                                        candidate.method.instructions,
                                        matchedInstruction.index,
                                    )
                            }
                            .map { matchedInstruction -> candidate to matchedInstruction }
                    }
                val uiStateCandidate =
                    requireExactlyOne(
                        label = "NewX Compose reply sorting UI state Relevance initializer for $enumClass",
                        candidates = uiStateCandidates,
                    )
                val uiStateMethod = uiStateCandidate.first.method
                val uiStateIndex = uiStateCandidate.second.index
                val uiStateInstruction = uiStateCandidate.second.instruction as? OneRegisterInstruction
                    ?: throw PatchException("Reply sorting UI state Relevance sget-object has no register")
                val uiStateRegister = uiStateInstruction.registerA
                val uiStateField = uiStateInstruction.getReference<FieldReference>()
                    ?: throw PatchException("Missing field reference in reply sorting UI state sget-object")
                if (uiStateField.definingClass != enumClass || !isRelevanceSget(uiStateInstruction)) {
                    throw PatchException(
                        "Reply sorting UI state field $uiStateField does not match resolved " +
                            "reply ranking enum $enumClass",
                    )
                }
                hooks += PlannedReplySortingHook(uiStateIndex) {
                    uiStateMethod.insertReplySortingDefault(uiStateIndex, uiStateRegister, enumClass)
                }
            }

            // Conversation prefetch. The legacy flag path (12.29–12.40) and the unconditional
            // constructor path (12.41+) are both accepted; whichever is present must resolve to one
            // method. The seed must reach an enum-typed request argument.
            val prefetchMatch =
                requireAtMostOne(
                    label = "NewX Compose reply sorting conversation prefetch method",
                    candidates = (
                        NewXComposeReplySortingLegacyPrefetchFingerprint.scopedMatchAllOrNull().orEmpty() +
                            NewXComposeReplySortingPrefetchFingerprint.scopedMatchAllOrNull().orEmpty()
                        ).distinctBy { it.method.signatureKey() },
                    describe = { it.method.signatureKey() },
                )
            if (prefetchMatch != null) {
                val prefetchMethod = prefetchMatch.method
                val prefetchSeed =
                    requireExactlyOne(
                        label = "NewX Compose reply sorting conversation prefetch seed for $enumClass",
                        candidates = prefetchMethod.enumSeeds(enumClass) { reference, parameterIndex ->
                            reference.parameterTypes[parameterIndex].toString() == enumClass
                        },
                    )
                hooks += PlannedReplySortingHook(prefetchSeed.index) {
                    prefetchMethod.insertReplySortingDefault(
                        prefetchSeed.index,
                        prefetchSeed.register,
                        enumClass,
                    )
                }
            }

            // Selection: remember the last choice. Resolved against the untouched selection method.
            val selectionMatch =
                requireExactlyOne(
                    label = "NewX Compose reply sorting selection handler",
                    candidates = NewXComposeReplySortingSelectionFingerprint.matchAllOrNull().orEmpty() +
                        replySortingSelectionMethodFingerprint(enumClass).scopedMatchAllOrNull().orEmpty(),
                )
            val selectionMethod = selectionMatch.method
            val defaultUrtAnchor =
                requireExactlyOne(
                    label = "selection handler defaultUrtTimelineComponent anchor",
                    candidates = selectionMethod.instructions.withIndex().filter { (_, instruction) ->
                        instruction.getReference<StringReference>()?.string == "defaultUrtTimelineComponent"
                    },
                )
            val selectionSite =
                resolveReplySortingSelectionSite(
                    method = selectionMethod,
                    enumClass = enumClass,
                    defaultUrtIndex = defaultUrtAnchor.index,
                )
            hooks += PlannedReplySortingHook(selectionSite.storeIndex) {
                selectionMethod.installReplySortingSelectionHook(selectionSite)
            }

            // Descending order keeps every planned index valid while earlier insertions shift the
            // instructions after them, including when two hooks share one method.
            hooks.sortedByDescending { it.index }.forEach { it.applyHook() }
        }
    }
