/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.timeline.explore

import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.isAssignableTo
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.valueReachesRegister
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.models.fieldForOutlinedToStringLabel
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
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val EXPLORE_PACKAGE_PREFIX = "Lcom/x/explore/"
private const val URP_COMPONENT_PACKAGE_PREFIX = "Lcom/x/urp/"
private const val URP_REPOSITORY_PACKAGE_PREFIX = "Lcom/x/repositories/urp/"

private const val URP_TIMELINE_ANCHOR = "UrpTimeline(title="
private const val SCRIBE_CONFIG_ANCHOR = "ScribeConfig(page="
private const val EXPLORE_COMPONENT_ANCHOR = "android_rich_tabs_explore_tab_enabled"
private const val DECOMPOSE_PAGES_ANCHOR = "com.arkivanov.decompose.router.pages.Pages"

private const val REGISTER_EXPLORE_REPO =
    "$EXPLORE_TAB_FILTER_DESCRIPTOR->registerExploreRepository($OBJECT_DESCRIPTOR)V"
private const val FILTER_FOR_COMPONENT =
    "$EXPLORE_TAB_FILTER_DESCRIPTOR->filterForComponent($OBJECT_DESCRIPTOR$LIST_DESCRIPTOR)$LIST_DESCRIPTOR"
private const val CLAMP_SELECTED_INDEX =
    "$EXPLORE_TAB_FILTER_DESCRIPTOR->clampSelectedIndex(I$LIST_DESCRIPTOR)I"

private val STATIC_INVOKES = setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)
private val DIRECT_INVOKES = setOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE)

private data class UrpModel(
    val timelineType: String,
    val titleField: FieldReference,
    val segmentedIdField: FieldReference,
    val scribeConfigField: FieldReference,
    val scribeSectionField: FieldReference,
)

private data class ExploreHandoff(
    val exploreCtor: MutableMethod,
    val factoryCallIndex: Int,
    val repoArgRegister: Int,
    val urpComponentType: String,
    val urpRepoField: FieldReference,
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
            val model = resolveUrpModel()
            val handoff = resolveExploreHandoff()
            val pagesType = resolveDecomposePagesType()

            patchFieldBridges(model, handoff)
            handoff.exploreCtor.insertHook(handoff.factoryCallIndex, relocateBranchTargets = true) {
                invokeStatic(methodReference(REGISTER_EXPLORE_REPO), handoff.repoArgRegister)
            }
            patchUrpPagesUpdate(model, handoff.urpComponentType, pagesType)
        }
    }

private fun patchFieldBridges(model: UrpModel, handoff: ExploreHandoff) {
    mutableClassDefBy(handoff.urpComponentType).requirePublicFields(listOf(handoff.urpRepoField))
    mutableClassDefBy(model.timelineType).requirePublicFields(
        listOf(model.titleField, model.segmentedIdField, model.scribeConfigField),
    )
    mutableClassDefBy(model.scribeConfigField.type).requirePublicFields(listOf(model.scribeSectionField))

    val filter = mutableClassDefBy(EXPLORE_TAB_FILTER_DESCRIPTOR)
    filter.patchObjectFieldGetter("repositoryOf", handoff.urpComponentType, handoff.urpRepoField, OBJECT_DESCRIPTOR)
    filter.patchObjectFieldGetter("titleOf", model.timelineType, model.titleField, STRING_DESCRIPTOR)
    filter.patchObjectFieldGetter("segmentedTimelineIdOf", model.timelineType, model.segmentedIdField, STRING_DESCRIPTOR)
    filter.patchObjectFieldGetter("scribeConfigOf", model.timelineType, model.scribeConfigField, OBJECT_DESCRIPTOR)
    filter.patchObjectFieldGetter("scribeSectionOf", model.scribeConfigField.type, model.scribeSectionField, STRING_DESCRIPTOR)
}

private fun resolveUrpModel(): UrpModel {
    val timelineToString =
        requireExactlyOne(
            "NewX UrpTimeline toString",
            classDefByStrings(URP_TIMELINE_ANCHOR).flatMap { cls ->
                cls.methods.filter { it.hasString(URP_TIMELINE_ANCHOR) }
            },
        ) { it.toString() }

    val titleField = timelineToString.fieldForOutlinedToStringLabel(URP_TIMELINE_ANCHOR)
    val segmentedIdField = timelineToString.fieldForOutlinedToStringLabel(", segmentedTimelineId=")
    val scribeConfigField = timelineToString.fieldForOutlinedToStringLabel(", scribeConfig=")

    val scribeClass =
        classDefByOrNull(scribeConfigField.type)
            ?: throw PatchException("NewX ScribeConfig class not found: ${scribeConfigField.type}")
    val scribeToString =
        requireExactlyOne(
            "NewX ScribeConfig toString in ${scribeClass.type}",
            scribeClass.methods.filter { it.hasString(SCRIBE_CONFIG_ANCHOR) },
        ) { it.toString() }
    val scribeSectionField = scribeToString.fieldForOutlinedToStringLabel(", section=")

    for ((name, field) in listOf(
        "UrpTimeline.title" to titleField,
        "UrpTimeline.segmentedTimelineId" to segmentedIdField,
        "ScribeConfig.section" to scribeSectionField,
    )) {
        if (field.type != STRING_DESCRIPTOR) {
            throw PatchException("Expected NewX $name ($field) to be $STRING_DESCRIPTOR, found ${field.type}")
        }
    }

    return UrpModel(
        timelineType = timelineToString.definingClass,
        titleField = titleField,
        segmentedIdField = segmentedIdField,
        scribeConfigField = scribeConfigField,
        scribeSectionField = scribeSectionField,
    )
}

private fun resolveExploreHandoff(): ExploreHandoff {
    val ctor =
        requireExactlyOne(
            "NewX ExploreComponent constructor",
            classDefByStrings(EXPLORE_COMPONENT_ANCHOR)
                .filter { it.type.startsWith(EXPLORE_PACKAGE_PREFIX) }
                .flatMap { cls -> cls.methods.filter { it.name == "<init>" && it.hasString(EXPLORE_COMPONENT_ANCHOR) } },
        ) { it.toString() }
    val mutableCtor =
        requireExactlyOne(
            "NewX mutable ExploreComponent constructor",
            mutableClassDefBy(ctor.definingClass).methods.filter { it.toString() == ctor.toString() },
        ) { it.toString() }

    val factoryCalls =
        mutableCtor.instructions.withIndex().mapNotNull { (index, insn) ->
            if (insn.opcode !in STATIC_INVOKES) return@mapNotNull null
            val ref = insn.getReference<MethodReference>() ?: return@mapNotNull null
            if (!ref.returnType.startsWith(URP_COMPONENT_PACKAGE_PREFIX)) return@mapNotNull null
            val repoParams =
                ref.parameterTypes.withIndex().filter { (_, type) ->
                    type.toString().startsWith(URP_REPOSITORY_PACKAGE_PREFIX)
                }
            if (repoParams.size != 1) return@mapNotNull null
            val repoParam = repoParams[0]
            val argReg =
                insn.registersUsed.getOrNull(repoParam.index)
                    ?: throw PatchException("Missing UrpRepository register in $mutableCtor @ $index")
            Triple(index, ref.returnType, repoParam.value.toString() to argReg)
        }
    val (callIndex, urpComponentType, repoInfo) =
        requireExactlyOne("NewX ExploreComponent UrpComponent factory call", factoryCalls) { (idx, ret, _) ->
            "$mutableCtor @ $idx -> $ret"
        }
    val (urpRepoType, repoArgRegister) = repoInfo
    val urpComponentClass =
        classDefByOrNull(urpComponentType)
            ?: throw PatchException("NewX UrpComponent class not found: $urpComponentType")
    val repoField = urpComponentClass.requireSingleInstanceField(urpRepoType, "UrpComponent repository field")

    return ExploreHandoff(mutableCtor, callIndex, repoArgRegister, urpComponentType, repoField)
}

private fun resolveDecomposePagesType(): String =
    requireExactlyOne(
        "Decompose Pages class",
        classDefByStrings(DECOMPOSE_PAGES_ANCHOR).filter { cls ->
            cls.methods.any { it.name == "<clinit>" && it.hasString(DECOMPOSE_PAGES_ANCHOR) } &&
                cls.methods.any { it.isPagesCtor() }
        },
    ) { it.type }.type

private fun patchUrpPagesUpdate(model: UrpModel, urpComponentType: String, pagesType: String) {
    val candidates = mutableListOf<Method>()
    classDefForEach { cls ->
        for (method in cls.methods) {
            val insns = method.implementation?.instructions ?: continue
            var hasComp = false
            var hasSegmented = false
            var hasPagesCtor = false
            for (insn in insns) {
                when (insn.opcode) {
                    Opcode.CHECK_CAST -> if (insn.getReference<TypeReference>()?.type == urpComponentType) hasComp = true
                    Opcode.IGET_OBJECT -> if (insn.getReference<FieldReference>()?.toString() == model.segmentedIdField.toString()) hasSegmented = true
                    Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE -> {
                        val ref = insn.getReference<MethodReference>()
                        if (ref != null && ref.definingClass == pagesType && ref.isPagesCtorRef()) hasPagesCtor = true
                    }
                    else -> Unit
                }
            }
            if (hasComp && hasSegmented && hasPagesCtor) candidates += method
        }
    }
    val target = requireExactlyOne("NewX UrpComponent Pages update lambda", candidates) { it.toString() }
    val method =
        requireExactlyOne(
            "NewX mutable UrpComponent Pages update lambda",
            mutableClassDefBy(target.definingClass).methods.filter { it.toString() == target.toString() },
        ) { it.toString() }

    val insns = method.instructions
    val compCast =
        requireExactlyOne(
            "UrpComponent check-cast in $method",
            insns.withIndex().filter { (_, insn) ->
                insn.opcode == Opcode.CHECK_CAST && insn.getReference<TypeReference>()?.type == urpComponentType
            },
        ) { (idx, _) -> "$method @ $idx" }
    val compCastIndex = compCast.index
    val compReg = (compCast.value as OneRegisterInstruction).registerA
    val armEnd =
        insns.indices.drop(compCastIndex + 1).firstOrNull { insns[it].opcode == Opcode.RETURN_OBJECT }
            ?: throw PatchException("Missing return-object in UrpComponent Pages update arm: $method")

    val (pagesCtorIndex, pagesCtorInsn) =
        requireExactlyOne(
            "Decompose Pages.<init>(int, List) in $method",
            (compCastIndex until armEnd).mapNotNull { idx ->
                val insn = insns[idx]
                val ref = insn.getReference<MethodReference>()
                if (insn.opcode in DIRECT_INVOKES && ref?.definingClass == pagesType && ref.isPagesCtorRef()) {
                    idx to insn
                } else {
                    null
                }
            },
        ) { (idx, _) -> "$method @ $idx" }
    val ctorArgs = pagesCtorInsn.registersUsed
    if (ctorArgs.size != 3) throw PatchException("Unexpected Pages.<init> args $ctorArgs in $method")
    val (pagesReceiverReg, selectedIndexReg, itemsReg) = Triple(ctorArgs[0], ctorArgs[1], ctorArgs[2])

    val pagesNewIndex =
        (pagesCtorIndex - 1 downTo compCastIndex).firstOrNull { idx ->
            val insn = insns[idx]
            insn.opcode == Opcode.NEW_INSTANCE &&
                insn.getReference<TypeReference>()?.type == pagesType &&
                (insn as? OneRegisterInstruction)?.registerA == pagesReceiverReg
        } ?: throw PatchException("Missing new-instance for Pages in $method")

    val listCasts =
        (compCastIndex + 1 until pagesNewIndex).mapNotNull { idx ->
            val insn = insns[idx]
            val type = insn.getReference<TypeReference>()?.type
            if (insn.opcode == Opcode.CHECK_CAST && type != null && isAssignableTo(type, LIST_DESCRIPTOR)) {
                idx to (insn as OneRegisterInstruction).registerA
            } else {
                null
            }
        }
    if (listCasts.size !in 1..2) {
        throw PatchException("Expected 1..2 List casts in UrpComponent Pages arm ($method), found ${listCasts.size}")
    }
    val (listCastIndex, listReg) = listCasts[0]
    if (!insns.valueReachesRegister(listCastIndex, listReg, pagesNewIndex, itemsReg) ||
        !insns.valueReachesRegister(compCastIndex, compReg, listCastIndex + 1, compReg)
    ) {
        throw PatchException("UrpComponent/list registers do not survive to Pages hook points in $method")
    }

    // Insert in descending index order so earlier indices stay valid.
    method.insertHook(pagesNewIndex, relocateBranchTargets = true) {
        invokeStatic(methodReference(CLAMP_SELECTED_INDEX), selectedIndexReg, itemsReg)
        moveResult(selectedIndexReg, "I")
    }
    if (listCasts.size == 2) {
        val (searchCastIndex, searchReg) = listCasts[1]
        method.insertHook(searchCastIndex + 1, relocateBranchTargets = false) {
            move(searchReg, listReg, LIST_DESCRIPTOR)
        }
    }
    method.insertHook(listCastIndex + 1, relocateBranchTargets = false) {
        invokeStatic(methodReference(FILTER_FOR_COMPONENT), compReg, listReg)
        moveResult(listReg, LIST_DESCRIPTOR)
    }
}

private fun Method.isPagesCtor(): Boolean =
    name == "<init>" && parameterTypes.size == 2 &&
        parameterTypes[0].toString() == "I" && isAssignableTo(parameterTypes[1].toString(), LIST_DESCRIPTOR)

private fun MethodReference.isPagesCtorRef(): Boolean =
    name == "<init>" && parameterTypes.size == 2 &&
        parameterTypes[0].toString() == "I" && isAssignableTo(parameterTypes[1].toString(), LIST_DESCRIPTOR)

private fun Method.hasString(literal: String): Boolean =
    implementation?.instructions?.any { insn ->
        (insn.opcode == Opcode.CONST_STRING || insn.opcode == Opcode.CONST_STRING_JUMBO) &&
            insn.getReference<StringReference>()?.string == literal
    } == true
