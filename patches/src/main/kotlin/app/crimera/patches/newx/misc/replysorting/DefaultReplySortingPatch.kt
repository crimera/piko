/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.replysorting

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
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
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

/**
 * Replaces the resolved `Relevance` seed in [register] with the configured default ranking mode
 * after [sgetIndex]. The register keeps its original type, so the consuming instruction range is
 * unchanged.
 */
private fun MutableMethod.insertReplySortingDefault(
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
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is StringReference && reference.string == "timelineRepository"
        } == true
    },
    filters =
        listOf(
            string("defaultUrtTimelineComponent"),
        ),
)

/**
 * Targets the Compose state initializer that seeds the reply-sorting sheet with
 * `mutableStateOf(TimelineRankingMode.Relevance)`.
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
        ),
    custom = { method, _ ->
        val instructions = method.implementation?.instructions?.toList()
        instructions?.withIndex()?.any { (index, _) ->
            isComposeStateInitializer(instructions, index)
        } == true
    },
)

/**
 * Targets the post-detail conversation prefetch seed added in 12.29. The ViewModel constructor
 * issues the initial conversation request with `TimelineRankingMode.Relevance` before the timeline
 * repository exists, so the repository seed is not authoritative on the first load. The capability
 * is identified by its feature-switch name rather than by an obfuscated owner.
 */
private object NewXComposeReplySortingPrefetchFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters =
        listOf(
            string("x_android_conversation_prefetch_enabled"),
        ),
)

private val OBJECT_MOVE_OPCODES =
    setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)

/**
 * The 12.30 prefetch request runs through a coroutine worker lambda: the seed enum is copied into
 * a parameter register with `move-object/from16` before the range constructor call, so the request
 * invoke no longer consumes the seed register directly. Propagate object moves from the seed and
 * accept a request that consumes any propagated register.
 */
private fun seedFeedsRequest(
    method: MutableMethod,
    seedIndex: Int,
    seedRegister: Int,
    enumClass: String,
): Boolean {
    val seededRegisters = mutableSetOf(seedRegister)
    method.instructions.drop(seedIndex + 1).forEach { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        if (reference != null &&
            reference.parameterTypes.any { it.toString() == enumClass } &&
            instruction.registersUsed.any { register -> register in seededRegisters }
        ) {
            return true
        }
        if (instruction.opcode in OBJECT_MOVE_OPCODES) {
            val move = instruction as? TwoRegisterInstruction
            if (move != null && move.registerB in seededRegisters) {
                seededRegisters += move.registerA
            }
        }
    }
    return false
}

/**
 * A validated selection: the check-cast that narrows the selected callback parameter to the
 * resolved ranking enum, and the field store of that same value. [register] is the store's value
 * register, which holds the chosen enum on every incoming path to [storeIndex].
 */
internal data class ReplySortingSelectionSite(
    val castIndex: Int,
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
    if (AccessFlags.STATIC.isSet(method.accessFlags) ||
        method.parameterTypes.map(CharSequence::toString) != listOf("Ljava/lang/Object;")
    ) {
        throw PatchException(
            "Unexpected reply sorting selection handler parameters: ${method.parameterTypes}",
        )
    }
    // The receiver and the single Object argument occupy the last two registers.
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
    val castSites = instructions.withIndex().mapNotNull { (index, instruction) ->
        if (index >= defaultUrtIndex || instruction.opcode != Opcode.CHECK_CAST) return@mapNotNull null
        val cast = instruction as? OneRegisterInstruction ?: return@mapNotNull null
        if (instruction.getReference<TypeReference>()?.type != enumClass) return@mapNotNull null
        if (!method.provesParameterValue(index, cast.registerA, selectedParameterRegister)) {
            return@mapNotNull null
        }
        index to cast.registerA
    }

    val successors = method.normalControlFlowSuccessors()
        ?: throw PatchException("Reply sorting selection handler has unsupported control flow: $method")
    val sites = castSites.flatMap { (castIndex, castRegister) ->
        instructions.withIndex().mapNotNull { (storeIndex, instruction) ->
            if (storeIndex <= castIndex || storeIndex >= defaultUrtIndex) return@mapNotNull null
            if (instruction.opcode != Opcode.IPUT_OBJECT) return@mapNotNull null
            val store = instruction as? TwoRegisterInstruction ?: return@mapNotNull null
            val fieldType = instruction.getReference<FieldReference>()?.type
            if (fieldType != "Ljava/lang/Object;" && fieldType != enumClass) return@mapNotNull null
            // A store in an unrelated returning arm cannot feed the boundary, so it is not a selection.
            if (defaultUrtIndex !in successors.reachableIndicesFrom(storeIndex).orEmpty()) {
                return@mapNotNull null
            }
            if (!method.provesValueFromInstruction(castIndex, castRegister, storeIndex, store.registerA)) {
                return@mapNotNull null
            }
            ReplySortingSelectionSite(castIndex, storeIndex, store.registerA)
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
    bytecodePatch(
        name = "NewX: Set default reply sorting",
        description = "Lets you choose the default reply sorting order for NewX post detail.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)

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
            // Patch the Compose post-detail timeline repository initialization.
            val match =
                requireExactlyOne(
                    label = "NewX Compose reply sorting initializer",
                    candidates = NewXComposeReplySortingFingerprint.matchAllOrNull().orEmpty(),
                )
            val method = match.method
            val targetSgetCandidates = match.instructionMatches.filter { matchedInstruction ->
                isRelevanceSget(matchedInstruction.instruction)
            }
            val targetSget =
                requireExactlyOne(
                    label = "reply sorting initializer Relevance sget-object",
                    candidates = targetSgetCandidates,
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

            method.insertReplySortingDefault(targetSgetIndex, sortRegister, enumClass)

            // Patch the 12.29 conversation prefetch seed when the capability is present. Older
            // releases issue the initial conversation request through the repository seed alone.
            val prefetchMatch =
                requireAtMostOne(
                    label = "NewX Compose reply sorting conversation prefetch method",
                    candidates = NewXComposeReplySortingPrefetchFingerprint
                        .scopedMatchAllOrNull()
                        .orEmpty(),
                )
            if (prefetchMatch != null) {
                val prefetchMethod = prefetchMatch.method
                val prefetchSeeds =
                    prefetchMethod.instructions.withIndex().filter { (_, instruction) ->
                        val field = instruction.getReference<FieldReference>()
                        field?.definingClass == enumClass && isRelevanceSget(instruction)
                    }
                val prefetchSeed =
                    requireExactlyOne(
                        label = "NewX Compose reply sorting conversation prefetch seed for $enumClass",
                        candidates = prefetchSeeds,
                    )
                val prefetchSget = prefetchSeed.value as? OneRegisterInstruction
                    ?: throw PatchException("Reply sorting conversation prefetch seed has no register")
                val prefetchRegister = prefetchSget.registerA
                val feedsPrefetchRequest =
                    seedFeedsRequest(
                        method = prefetchMethod,
                        seedIndex = prefetchSeed.index,
                        seedRegister = prefetchRegister,
                        enumClass = enumClass,
                    )
                if (!feedsPrefetchRequest) {
                    throw PatchException(
                        "Reply sorting conversation prefetch seed v$prefetchRegister does not feed a " +
                            "request whose parameters include $enumClass",
                    )
                }

                prefetchMethod.insertReplySortingDefault(
                    prefetchSeed.index,
                    prefetchRegister,
                    enumClass,
                )
            }

            // Patch the Compose reply-sorting selection handler to remember the last choice.
            val selectionMatch =
                requireExactlyOne(
                    label = "NewX Compose reply sorting selection handler",
                    candidates = NewXComposeReplySortingSelectionFingerprint
                        .matchAllOrNull()
                        .orEmpty(),
                )
            val selectionMethod = selectionMatch.method
            // Resolve the anchor from the live method rather than the cached match indexes, so earlier
            // mutations to this method cannot leave the boundary stale.
            val defaultUrtCandidates =
                selectionMethod.instructions.withIndex().filter { (_, instruction) ->
                    instruction.getReference<StringReference>()?.string == "defaultUrtTimelineComponent"
                }
            val defaultUrtAnchor =
                requireExactlyOne(
                    label = "selection handler defaultUrtTimelineComponent anchor",
                    candidates = defaultUrtCandidates,
                )
            val selectionSite =
                resolveReplySortingSelectionSite(
                    method = selectionMethod,
                    enumClass = enumClass,
                    defaultUrtIndex = defaultUrtAnchor.index,
                )
            selectionMethod.installReplySortingSelectionHook(selectionSite)

            // Patch the Compose reply sorting UI state initializer so the button label
            // and sheet selection reflect the configured default instead of Relevance.
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
            val uiStateMatch = uiStateCandidate.first
            val uiStateMethod = uiStateMatch.method
            val uiStateIndex = uiStateCandidate.second.index
            val uiStateInstruction = uiStateCandidate.second.instruction as? OneRegisterInstruction
                ?: throw PatchException("Reply sorting UI state Relevance sget-object has no register")
            val uiStateRegister = uiStateInstruction.registerA
            val uiStateField = uiStateInstruction.getReference<FieldReference>()
                ?: throw PatchException("Missing field reference in reply sorting UI state sget-object")
            if (uiStateField.definingClass != enumClass || !isRelevanceSget(uiStateInstruction)) {
                throw PatchException(
                    "Reply sorting UI state field ${uiStateField} does not match resolved " +
                        "reply ranking enum $enumClass",
                )
            }

            uiStateMethod.insertReplySortingDefault(uiStateIndex, uiStateRegister, enumClass)
        }
    }
