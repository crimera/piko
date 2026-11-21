/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.timeline.explore

import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.isAssignableTo
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.valueReachesRegister
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.models.patchObjectFieldGetter
import app.crimera.patches.newx.models.requirePublicFields
import app.crimera.patches.newx.models.requireSingleInstanceField
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.EXPLORE_TAB_FILTER_DESCRIPTOR
import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.settings.choice
import app.crimera.patches.settings.multiChoice
import app.crimera.patches.settings.settingStrings
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefByOrNull
import app.morphe.patcher.patch.classDefByStrings
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val STRING_BUILDER_DESCRIPTOR = "Ljava/lang/StringBuilder;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val EXPLORE_PACKAGE_PREFIX = "Lcom/x/explore/"
private const val URP_COMPONENT_PACKAGE_PREFIX = "Lcom/x/urp/"
private const val URP_REPOSITORY_PACKAGE_PREFIX = "Lcom/x/repositories/urp/"

private const val URP_TIMELINE_TOSTRING_ANCHOR = "UrpTimeline(title="
private const val SCRIBE_CONFIG_TOSTRING_ANCHOR = "ScribeConfig(page="
private const val EXPLORE_COMPONENT_ANCHOR = "android_rich_tabs_explore_tab_enabled"
private const val DECOMPOSE_PAGES_ANCHOR = "com.arkivanov.decompose.router.pages.Pages"

private const val REGISTER_EXPLORE_REPO_METHOD =
    "$EXPLORE_TAB_FILTER_DESCRIPTOR->registerExploreRepository($OBJECT_DESCRIPTOR)V"
private const val FILTER_FOR_COMPONENT_METHOD =
    "$EXPLORE_TAB_FILTER_DESCRIPTOR->filterForComponent($OBJECT_DESCRIPTOR$LIST_DESCRIPTOR)$LIST_DESCRIPTOR"
private const val CLAMP_SELECTED_INDEX_METHOD =
    "$EXPLORE_TAB_FILTER_DESCRIPTOR->clampSelectedIndex(I$LIST_DESCRIPTOR)I"

private val STATIC_INVOKE_OPCODES =
    setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)
private val DIRECT_INVOKE_OPCODES =
    setOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE)
private val VIRTUAL_INVOKE_OPCODES =
    setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE)

private data class UrpTimelineModel(
    val timelineType: String,
    val titleField: FieldReference,
    val timelineIdField: FieldReference,
    val segmentedTimelineIdField: FieldReference,
    val scribeConfigField: FieldReference,
    val scribeConfigType: String,
    val scribeSectionField: FieldReference,
)

private data class ExploreUrpHandoff(
    val exploreConstructor: MutableMethod,
    val urpFactoryCallIndex: Int,
    val repoArgRegister: Int,
    val urpComponentType: String,
    val urpComponentRepoField: FieldReference,
)

internal data class UrpPagesUpdateHooks(
    val componentRegister: Int,
    val listCastIndex: Int,
    val listRegister: Int,
    val searchListCastIndex: Int?,
    val searchListRegister: Int?,
    val pagesNewInstanceIndex: Int,
    val selectedIndexRegister: Int,
    val itemsRegister: Int,
)

@Suppress("unused")
val customizeNewXExploreTabsPatch =
    patch(
        name = "NewX: Customize explore tabs",
        description =
            "Lets you hide tabs (For You, Trending, News, Sports, Entertainment) " +
                "from the NewX Explore screen.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)
        category("NewX patches")
        dependsOn(newXExtensionPatch)

        newXSettings {
            category(Categories.TIMELINE) {
                multiChoice(
                    id = "newx.explore.hidden_tabs",
                    strings = settingStrings("piko_newx_explore_tabs"),
                    order = 170,
                    defaultValue = emptySet(),
                    rebootApp = true,
                    options =
                        listOf(
                            choice("for_you", "piko_newx_explore_tab_for_you"),
                            choice("trending", "piko_newx_explore_tab_trending"),
                            choice("news", "piko_newx_explore_tab_news"),
                            choice("sports", "piko_newx_explore_tab_sports"),
                            choice("entertainment", "piko_newx_explore_tab_entertainment"),
                        ),
                )
            }
        }

        execute {
            val model = resolveUrpTimelineModel()
            val handoff = resolveExploreUrpHandoff()
            val pagesType = resolveDecomposePagesType()
            val (pagesUpdateMethod, pagesHooks) =
                resolveUrpPagesUpdateTarget(
                    model = model,
                    urpComponentType = handoff.urpComponentType,
                    pagesType = pagesType,
                )

            patchExploreFilterBridges(model, handoff)
            injectExploreRepositoryRegistration(
                method = handoff.exploreConstructor,
                urpFactoryCallIndex = handoff.urpFactoryCallIndex,
                repoArgRegister = handoff.repoArgRegister,
            )
            injectUrpPagesFilterHooks(pagesUpdateMethod, pagesHooks)
        }
    }

private fun patchExploreFilterBridges(
    model: UrpTimelineModel,
    handoff: ExploreUrpHandoff,
) {
    mutableClassDefBy(handoff.urpComponentType).requirePublicFields(listOf(handoff.urpComponentRepoField))
    mutableClassDefBy(model.timelineType).requirePublicFields(
        listOf(
            model.titleField,
            model.timelineIdField,
            model.segmentedTimelineIdField,
            model.scribeConfigField,
        ),
    )
    mutableClassDefBy(model.scribeConfigType).requirePublicFields(listOf(model.scribeSectionField))

    val filterClass = mutableClassDefBy(EXPLORE_TAB_FILTER_DESCRIPTOR)
    filterClass.patchObjectFieldGetter(
        name = "repositoryOf",
        ownerDescriptor = handoff.urpComponentType,
        field = handoff.urpComponentRepoField,
        returnType = OBJECT_DESCRIPTOR,
    )
    filterClass.patchObjectFieldGetter(
        name = "titleOf",
        ownerDescriptor = model.timelineType,
        field = model.titleField,
        returnType = STRING_DESCRIPTOR,
    )
    filterClass.patchObjectFieldGetter(
        name = "timelineIdOf",
        ownerDescriptor = model.timelineType,
        field = model.timelineIdField,
        returnType = STRING_DESCRIPTOR,
    )
    filterClass.patchObjectFieldGetter(
        name = "segmentedTimelineIdOf",
        ownerDescriptor = model.timelineType,
        field = model.segmentedTimelineIdField,
        returnType = STRING_DESCRIPTOR,
    )
    filterClass.patchObjectFieldGetter(
        name = "scribeConfigOf",
        ownerDescriptor = model.timelineType,
        field = model.scribeConfigField,
        returnType = OBJECT_DESCRIPTOR,
    )
    filterClass.patchObjectFieldGetter(
        name = "scribeSectionOf",
        ownerDescriptor = model.scribeConfigType,
        field = model.scribeSectionField,
        returnType = STRING_DESCRIPTOR,
    )
}

internal fun injectExploreRepositoryRegistration(
    method: MutableMethod,
    urpFactoryCallIndex: Int,
    repoArgRegister: Int,
) {
    method.insertHook(
        index = urpFactoryCallIndex,
        relocateBranchTargets = true,
    ) {
        invokeStatic(methodReference(REGISTER_EXPLORE_REPO_METHOD), repoArgRegister)
    }
}

/**
 * Patches the URP `Pages` update arm in descending index order so earlier indices stay valid:
 * 1. Clamps `selectedIndex` right before `new Pages(selectedIndex, items)` so shrinking the list
 *    mid-session never violates Decompose's index range check.
 * 2. Keeps the `initialTabId` search list in sync with the filtered list when the compiler emits
 *    a second `Result.Success.data` read for `indexOfFirst`.
 * 3. Filters the `UrpTimeline` list immediately after `Result.Success.data` is cast to `List`,
 *    gated on whether the owning `UrpComponent` uses Explore's repository.
 */
internal fun injectUrpPagesFilterHooks(
    method: MutableMethod,
    hooks: UrpPagesUpdateHooks,
) {
    method.insertHook(
        index = hooks.pagesNewInstanceIndex,
        relocateBranchTargets = true,
    ) {
        invokeStatic(
            methodReference(CLAMP_SELECTED_INDEX_METHOD),
            hooks.selectedIndexRegister,
            hooks.itemsRegister,
        )
        moveResult(hooks.selectedIndexRegister, "I")
    }

    if (hooks.searchListCastIndex != null && hooks.searchListRegister != null) {
        method.insertHook(
            index = hooks.searchListCastIndex + 1,
            relocateBranchTargets = false,
        ) {
            move(hooks.searchListRegister, hooks.listRegister, LIST_DESCRIPTOR)
        }
    }

    method.insertHook(
        index = hooks.listCastIndex + 1,
        relocateBranchTargets = false,
    ) {
        invokeStatic(
            methodReference(FILTER_FOR_COMPONENT_METHOD),
            hooks.componentRegister,
            hooks.listRegister,
        )
        moveResult(hooks.listRegister, LIST_DESCRIPTOR)
    }
}

private fun resolveUrpTimelineModel(): UrpTimelineModel {
    val timelineToStrings =
        classDefByStrings(URP_TIMELINE_TOSTRING_ANCHOR).flatMap { classDef ->
            classDef.methods.filter { method ->
                method.containsStringLiteral(URP_TIMELINE_TOSTRING_ANCHOR)
            }
        }
    val timelineToString =
        requireExactlyOne("NewX UrpTimeline toString", timelineToStrings) { it.toString() }

    val titleField = timelineToString.resolveToStringField(URP_TIMELINE_TOSTRING_ANCHOR)
    val timelineIdField = timelineToString.resolveToStringField(", timelineId=")
    val segmentedTimelineIdField = timelineToString.resolveToStringField(", segmentedTimelineId=")
    val scribeConfigField = timelineToString.resolveToStringField(", scribeConfig=")

    requireFieldType(titleField, STRING_DESCRIPTOR, "UrpTimeline.title")
    requireFieldType(timelineIdField, STRING_DESCRIPTOR, "UrpTimeline.timelineId")
    requireFieldType(segmentedTimelineIdField, STRING_DESCRIPTOR, "UrpTimeline.segmentedTimelineId")

    val scribeConfigClass =
        classDefByOrNull(scribeConfigField.type)
            ?: throw PatchException("NewX UrpTimeline ScribeConfig class not found: ${scribeConfigField.type}")
    val scribeToString =
        requireExactlyOne(
            "NewX ScribeConfig toString in ${scribeConfigClass.type}",
            scribeConfigClass.methods.filter { method ->
                method.containsStringLiteral(SCRIBE_CONFIG_TOSTRING_ANCHOR)
            },
        ) { it.toString() }
    val scribeSectionField = scribeToString.resolveToStringField(", section=")
    requireFieldType(scribeSectionField, STRING_DESCRIPTOR, "ScribeConfig.section")

    return UrpTimelineModel(
        timelineType = timelineToString.definingClass,
        titleField = titleField,
        timelineIdField = timelineIdField,
        segmentedTimelineIdField = segmentedTimelineIdField,
        scribeConfigField = scribeConfigField,
        scribeConfigType = scribeConfigClass.type,
        scribeSectionField = scribeSectionField,
    )
}

private fun resolveExploreUrpHandoff(): ExploreUrpHandoff {
    val exploreCtors =
        classDefByStrings(EXPLORE_COMPONENT_ANCHOR)
            .filter { classDef -> classDef.type.startsWith(EXPLORE_PACKAGE_PREFIX) }
            .flatMap { classDef ->
                classDef.methods.filter { method ->
                    method.name == "<init>" && method.containsStringLiteral(EXPLORE_COMPONENT_ANCHOR)
                }
            }
    val exploreCtor =
        requireExactlyOne("NewX ExploreComponent constructor", exploreCtors) { it.toString() }
    val mutableCtor =
        requireExactlyOne(
            "NewX mutable ExploreComponent constructor",
            mutableClassDefBy(exploreCtor.definingClass).methods.filter { candidate ->
                candidate.toString() == exploreCtor.toString()
            },
        ) { it.toString() }

    val urpFactoryCalls =
        mutableCtor.instructions.withIndex().mapNotNull { (index, instruction) ->
            if (instruction.opcode !in STATIC_INVOKE_OPCODES) return@mapNotNull null
            val reference = instruction.getReference<MethodReference>() ?: return@mapNotNull null
            if (!reference.returnType.startsWith(URP_COMPONENT_PACKAGE_PREFIX)) return@mapNotNull null
            val repoParamIndices =
                reference.parameterTypes.withIndex().filter { (_, paramType) ->
                    paramType.toString().startsWith(URP_REPOSITORY_PACKAGE_PREFIX)
                }
            if (repoParamIndices.size != 1) return@mapNotNull null
            val repoParam = repoParamIndices[0]
            val argRegister =
                instruction.registersUsed.getOrNull(repoParam.index)
                    ?: throw PatchException(
                        "Missing UrpRepository argument register at param ${repoParam.index} in $mutableCtor",
                    )
            Triple(index, reference, repoParam.value.toString() to argRegister)
        }
    val (callIndex, factoryRef, repoInfo) =
        requireExactlyOne("NewX ExploreComponent UrpComponent factory call", urpFactoryCalls) { (idx, ref, _) ->
            "$mutableCtor @ $idx -> $ref"
        }
    val (urpRepoType, repoArgRegister) = repoInfo
    val urpComponentClass =
        classDefByOrNull(factoryRef.returnType)
            ?: throw PatchException("NewX UrpComponent class not found: ${factoryRef.returnType}")
    val repoField =
        urpComponentClass.requireSingleInstanceField(
            type = urpRepoType,
            semanticName = "UrpComponent repository field",
        )

    return ExploreUrpHandoff(
        exploreConstructor = mutableCtor,
        urpFactoryCallIndex = callIndex,
        repoArgRegister = repoArgRegister,
        urpComponentType = urpComponentClass.type,
        urpComponentRepoField = repoField,
    )
}

private fun resolveDecomposePagesType(): String {
    val candidates =
        classDefByStrings(DECOMPOSE_PAGES_ANCHOR).filter { classDef ->
            classDef.methods.any { method ->
                method.name == "<clinit>" && method.containsStringLiteral(DECOMPOSE_PAGES_ANCHOR)
            } && classDef.methods.any { method -> method.isPagesListConstructor() }
        }
    return requireExactlyOne("Decompose Pages class", candidates) { it.type }.type
}

private fun resolveUrpPagesUpdateTarget(
    model: UrpTimelineModel,
    urpComponentType: String,
    pagesType: String,
): Pair<MutableMethod, UrpPagesUpdateHooks> {
    val candidates = mutableListOf<Method>()
    classDefForEach { classDef ->
        for (method in classDef.methods) {
            val instructions = method.implementation?.instructions?.toList() ?: continue
            if (instructions.matchesUrpPagesUpdate(model, urpComponentType, pagesType)) {
                candidates += method
            }
        }
    }
    val target = requireExactlyOne("NewX UrpComponent Pages update lambda", candidates) { it.toString() }
    val mutableMethod =
        requireExactlyOne(
            "NewX mutable UrpComponent Pages update lambda",
            mutableClassDefBy(target.definingClass).methods.filter { candidate ->
                candidate.toString() == target.toString()
            },
        ) { it.toString() }

    val hooks =
        analyzeUrpPagesUpdateHooks(
            instructions = mutableMethod.instructions,
            urpComponentType = urpComponentType,
            segmentedTimelineIdField = model.segmentedTimelineIdField,
            pagesType = pagesType,
            ownerDescription = mutableMethod.toString(),
        )
    return mutableMethod to hooks
}

internal fun analyzeUrpPagesUpdateHooks(
    instructions: List<Instruction>,
    urpComponentType: String,
    segmentedTimelineIdField: FieldReference,
    pagesType: String,
    ownerDescription: String,
): UrpPagesUpdateHooks {
    val compCastCandidates =
        instructions.withIndex().filter { (_, instruction) ->
            instruction.opcode == Opcode.CHECK_CAST &&
                instruction.getReference<TypeReference>()?.type == urpComponentType
        }
    val compCast =
        requireExactlyOne("UrpComponent check-cast in $ownerDescription", compCastCandidates) { (idx, _) ->
            "$ownerDescription @ $idx"
        }
    val compCastIndex = compCast.index
    val compRegister = (compCast.value as OneRegisterInstruction).registerA

    val armEndIndex =
        instructions.indices
            .drop(compCastIndex + 1)
            .firstOrNull { index -> instructions[index].opcode == Opcode.RETURN_OBJECT }
            ?: throw PatchException("Missing return-object for UrpComponent Pages update arm in $ownerDescription")

    val segmentedReadFound =
        (compCastIndex until armEndIndex).any { index ->
            val instruction = instructions[index]
            instruction.opcode == Opcode.IGET_OBJECT &&
                instruction.getReference<FieldReference>()?.toString() == segmentedTimelineIdField.toString()
        }
    if (!segmentedReadFound) {
        throw PatchException(
            "Expected UrpTimeline.segmentedTimelineId read in UrpComponent Pages update arm: $ownerDescription",
        )
    }

    val pagesCtorCandidates =
        (compCastIndex until armEndIndex).mapNotNull { index ->
            val instruction = instructions[index]
            if (instruction.opcode !in DIRECT_INVOKE_OPCODES) return@mapNotNull null
            val ref = instruction.getReference<MethodReference>() ?: return@mapNotNull null
            if (ref.definingClass != pagesType || !ref.isPagesListConstructorReference()) {
                return@mapNotNull null
            }
            index to instruction
        }
    val (pagesCtorIndex, pagesCtorInsn) =
        requireExactlyOne("Decompose Pages.<init>(int, List) call in $ownerDescription", pagesCtorCandidates) { (idx, _) ->
            "$ownerDescription @ $idx"
        }
    val ctorArgs = pagesCtorInsn.registersUsed
    if (ctorArgs.size != 3) {
        throw PatchException(
            "Expected 3 registers (receiver, selectedIndex, items) for Pages.<init> in $ownerDescription, found $ctorArgs",
        )
    }
    val pagesReceiverReg = ctorArgs[0]
    val selectedIndexRegister = ctorArgs[1]
    val itemsRegister = ctorArgs[2]

    val pagesNewInstanceIndex =
        (pagesCtorIndex - 1 downTo compCastIndex).firstOrNull { index ->
            val insn = instructions[index]
            insn.opcode == Opcode.NEW_INSTANCE &&
                insn.getReference<TypeReference>()?.type == pagesType &&
                (insn as? OneRegisterInstruction)?.registerA == pagesReceiverReg
        } ?: throw PatchException(
            "Missing new-instance for Decompose Pages before constructor call in $ownerDescription",
        )

    val listCasts =
        (compCastIndex + 1 until pagesNewInstanceIndex).mapNotNull { index ->
            val insn = instructions[index]
            if (insn.opcode != Opcode.CHECK_CAST) return@mapNotNull null
            val type = insn.getReference<TypeReference>()?.type ?: return@mapNotNull null
            if (!isAssignableTo(type, LIST_DESCRIPTOR)) return@mapNotNull null
            index to (insn as OneRegisterInstruction).registerA
        }
    if (listCasts.size !in 1..2) {
        throw PatchException(
            "Expected 1..2 List check-casts in UrpComponent Pages update arm in $ownerDescription, found ${listCasts.size}: $listCasts",
        )
    }
    val (listCastIndex, listRegister) = listCasts[0]
    if (!instructions.valueReachesRegister(listCastIndex, listRegister, pagesNewInstanceIndex, itemsRegister)) {
        throw PatchException(
            "Filtered list register v$listRegister does not reach Pages.<init> items register v$itemsRegister in $ownerDescription",
        )
    }
    if (!instructions.valueReachesRegister(compCastIndex, compRegister, listCastIndex + 1, compRegister)) {
        throw PatchException(
            "UrpComponent register v$compRegister is overwritten before list filter point in $ownerDescription",
        )
    }
    val (searchListCastIndex, searchListRegister) =
        if (listCasts.size == 2) {
            listCasts[1].first to listCasts[1].second
        } else {
            null to null
        }

    return UrpPagesUpdateHooks(
        componentRegister = compRegister,
        listCastIndex = listCastIndex,
        listRegister = listRegister,
        searchListCastIndex = searchListCastIndex,
        searchListRegister = searchListRegister,
        pagesNewInstanceIndex = pagesNewInstanceIndex,
        selectedIndexRegister = selectedIndexRegister,
        itemsRegister = itemsRegister,
    )
}

private fun List<Instruction>.matchesUrpPagesUpdate(
    model: UrpTimelineModel,
    urpComponentType: String,
    pagesType: String,
): Boolean {
    var hasCompCast = false
    var hasSegmentedRead = false
    var hasPagesCtor = false
    for (instruction in this) {
        when (instruction.opcode) {
            Opcode.CHECK_CAST -> {
                if (instruction.getReference<TypeReference>()?.type == urpComponentType) {
                    hasCompCast = true
                }
            }
            Opcode.IGET_OBJECT -> {
                if (instruction.getReference<FieldReference>()?.toString() == model.segmentedTimelineIdField.toString()) {
                    hasSegmentedRead = true
                }
            }
            Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE -> {
                val ref = instruction.getReference<MethodReference>()
                if (ref != null && ref.definingClass == pagesType && ref.isPagesListConstructorReference()) {
                    hasPagesCtor = true
                }
            }
            else -> Unit
        }
    }
    return hasCompCast && hasSegmentedRead && hasPagesCtor
}

private fun Method.isPagesListConstructor(): Boolean =
    name == "<init>" &&
        parameterTypes.size == 2 &&
        parameterTypes[0].toString() == "I" &&
        isAssignableTo(parameterTypes[1].toString(), LIST_DESCRIPTOR)

private fun MethodReference.isPagesListConstructorReference(): Boolean =
    name == "<init>" &&
        parameterTypes.size == 2 &&
        parameterTypes[0].toString() == "I" &&
        isAssignableTo(parameterTypes[1].toString(), LIST_DESCRIPTOR)

private fun Method.containsStringLiteral(literal: String): Boolean =
    implementation?.instructions?.any { instruction ->
        (instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO) &&
            instruction.getReference<StringReference>()?.string == literal
    } == true

private fun Method.resolveToStringField(label: String): FieldReference =
    outlinedToStringField(label) ?: fieldForToStringLabel(label)

private fun Method.outlinedToStringField(label: String): FieldReference? {
    val methodInstructions = implementation?.instructions?.toList() ?: return null
    fun definedAt(register: Int, callIndex: Int, matches: (Int) -> Boolean): List<Int> =
        methodInstructions.indices.filter { index ->
            index < callIndex && matches(index) &&
                methodInstructions.valueReachesRegister(
                    index,
                    (methodInstructions[index] as OneRegisterInstruction).registerA,
                    callIndex,
                    register,
                )
        }

    val candidates =
        methodInstructions.withIndex().flatMap { (callIndex, call) ->
            if (call.opcode !in STATIC_INVOKE_OPCODES) return@flatMap emptyList()
            val helper = call.getReference<MethodReference>() ?: return@flatMap emptyList()
            val helperMethod =
                classDefByOrNull(helper.definingClass)?.let { owner ->
                    requireAtMostOne(
                        "NewX outlined toString helper $helper",
                        owner.methods.filter { candidate ->
                            candidate.definingClass == helper.definingClass &&
                                candidate.name == helper.name &&
                                candidate.returnType == helper.returnType &&
                                candidate.parameterTypes.map(CharSequence::toString) ==
                                    helper.parameterTypes.map(CharSequence::toString)
                        },
                    ) { it.toString() }
                } ?: return@flatMap emptyList()
            val appendOrder = helperMethod.stringBuilderParameterOrder() ?: return@flatMap emptyList()
            val arguments = call.registersUsed
            val labelSlots =
                arguments.indices.filter { slot ->
                    definedAt(arguments[slot], callIndex) { index ->
                        val insn = methodInstructions[index]
                        (insn.opcode == Opcode.CONST_STRING || insn.opcode == Opcode.CONST_STRING_JUMBO) &&
                            insn.getReference<StringReference>()?.string == label
                    }.isNotEmpty()
                }
            labelSlots.flatMap { labelSlot ->
                val labelPos = appendOrder.indexOf(labelSlot)
                if (labelPos < 0) return@flatMap emptyList()
                val fieldSlot = appendOrder.getOrNull(labelPos + 1)
                if (fieldSlot != null) {
                    val register = arguments.getOrNull(fieldSlot) ?: return@flatMap emptyList()
                    definedAt(register, callIndex) { index ->
                        methodInstructions[index].opcode == Opcode.IGET_OBJECT &&
                            methodInstructions[index].getReference<FieldReference>()?.definingClass == definingClass
                    }.mapNotNull { index -> methodInstructions[index].getReference<FieldReference>() }
                } else if (helper.returnType == STRING_BUILDER_DESCRIPTOR) {
                    // When the label is the final argument of an outlined StringBuilder helper,
                    // the matching field is appended to the returned StringBuilder right after.
                    val moveResult = methodInstructions.getOrNull(callIndex + 1)
                    if (moveResult?.opcode != Opcode.MOVE_RESULT_OBJECT) return@flatMap emptyList()
                    val sbRegister = (moveResult as OneRegisterInstruction).registerA
                    val nextAppendIndex =
                        ((callIndex + 2) until methodInstructions.size).firstOrNull { index ->
                            val insn = methodInstructions[index]
                            if (insn.opcode !in VIRTUAL_INVOKE_OPCODES) return@firstOrNull false
                            val ref = insn.getReference<MethodReference>() ?: return@firstOrNull false
                            ref.definingClass == STRING_BUILDER_DESCRIPTOR &&
                                ref.name == "append" &&
                                insn.registersUsed.firstOrNull()?.let { receiver ->
                                    methodInstructions.valueReachesRegister(callIndex + 1, sbRegister, index, receiver)
                                } == true
                        } ?: return@flatMap emptyList()
                    val valueRegister =
                        methodInstructions[nextAppendIndex].registersUsed.getOrNull(1)
                            ?: return@flatMap emptyList()
                    definedAt(valueRegister, nextAppendIndex) { index ->
                        methodInstructions[index].opcode == Opcode.IGET_OBJECT &&
                            methodInstructions[index].getReference<FieldReference>()?.definingClass == definingClass
                    }.mapNotNull { index -> methodInstructions[index].getReference<FieldReference>() }
                } else {
                    emptyList()
                }
            }
        }.distinctBy { it.toString() }

    return requireAtMostOne("NewX outlined toString field for '$label' in $this", candidates) { it.toString() }
}

private fun Method.stringBuilderParameterOrder(): List<Int>? {
    if (!AccessFlags.STATIC.isSet(accessFlags)) return null
    val impl = implementation ?: return null
    val paramStart =
        impl.registerCount -
            parameterTypes.sumOf { type ->
                if (type.toString() == "J" || type.toString() == "D") 2 else 1
            }
    val paramCount = parameterTypes.size
    if (paramCount == 0) return null

    val methodInstructions = impl.instructions.toList()
    val order = mutableListOf<Int>()
    for ((index, instruction) in methodInstructions.withIndex()) {
        if (instruction.opcode !in VIRTUAL_INVOKE_OPCODES && instruction.opcode !in DIRECT_INVOKE_OPCODES) {
            continue
        }
        val ref = instruction.getReference<MethodReference>() ?: continue
        if (ref.definingClass != STRING_BUILDER_DESCRIPTOR) continue
        val isWrite =
            (ref.name == "<init>" && ref.parameterTypes.size == 1) ||
                ref.name == "append"
        if (!isWrite) continue
        val argRegister = instruction.registersUsed.getOrNull(1) ?: return null
        val slot = argRegister - paramStart
        if (slot in 0 until paramCount) {
            order += slot
        } else {
            val matchingSlot =
                (0 until paramCount).firstOrNull { candidateSlot ->
                    methodInstructions.valueReachesRegister(0, paramStart + candidateSlot, index, argRegister)
                } ?: return null
            order += matchingSlot
        }
    }
    return order.takeIf { it.isNotEmpty() }
}

private fun requireFieldType(
    field: FieldReference,
    expectedType: String,
    semanticName: String,
) {
    if (field.type != expectedType) {
        throw PatchException(
            "Expected NewX $semanticName ($field) to have type $expectedType, found ${field.type}",
        )
    }
}
