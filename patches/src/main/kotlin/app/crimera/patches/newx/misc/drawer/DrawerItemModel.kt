package app.crimera.patches.newx.misc.drawer

import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resource.resourceId
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Format
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

/*
 * ITEM-MODEL DRAWER CONTRACT
 *
 * Title-row releases call one renderer per drawer row and pass a resource-backed title literal at
 * the call site (see DrawerRowAnalysis.kt). Item-model releases instead pass a list of drawer item
 * objects and resolve every row through two shared mappers in one owner class:
 *
 *   title mapper:  static (Item, Z, Composer) -> String, containing drawer_profile_title
 *   icon mapper:   static (Item) -> Lcom/x/icons/...;  in the title mapper's class
 *
 * Both mappers dispatch on the item with `sget-object X->a` + Intrinsics.areEqual (singleton items)
 * or `instance-of X` (data items). Parsing those branches yields the item class -> title resources
 * and item class -> icon field tables that replace the title-row call-site literals.
 *
 * Rows are then found by data flow, never by obfuscated names or positions:
 *   - titled renderers: static calls whose String argument is the title mapper's result;
 *   - item renderer:    the titled renderer that forwards one of its parameters to the icon mapper
 *                       (the item) and its title parameter to a row renderer (with the click);
 *   - anchor rows:      calls to a title/icon/click renderer whose icon argument is the icon
 *                       mapper's result, so per-item runtime gating can select one item's row.
 *
 * The model is absent on title-row releases; callers keep the title-row path in that case.
 */

private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val ICONS_DESCRIPTOR_PREFIX = "Lcom/x/icons/"
private const val INTRINSICS_ARE_EQUAL =
    "Lkotlin/jvm/internal/Intrinsics;->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z"
private const val PROFILE_TITLE_RESOURCE = "drawer_profile_title"

/** One item class dispatched by the title and icon mappers. */
internal data class DrawerItemBranch(
    val itemType: String,
    /** String resources the title mapper loads directly for this item. */
    val titleResourceIds: List<Int>,
    /** String resources loaded through a nested mapper (the item wraps another model). */
    val nestedTitleResourceIds: List<Int>,
    /** Static icon field returned by the icon mapper, or null for icon-less/dynamic icons. */
    val iconField: FieldReference?,
)

/** A call to a single-title, single-icon, single-click renderer whose icon is an item's icon. */
internal data class DrawerItemRowCall(
    val method: MethodReference,
    /** Ordinal of this call among calls to [renderer] in [method]; stable across insertions. */
    val rendererCallOrdinal: Int,
    val renderer: MethodReference,
)

internal data class DrawerItemModel(
    val ownerClass: String,
    val branches: List<DrawerItemBranch>,
    /** Per-item renderer of the menu list: receives the item, the mapped title, and the click. */
    val itemRenderer: MethodReference,
    val itemParameterIndex: Int,
    val clickParameterIndex: Int,
    /** Renderers that receive a mapped item title as their only String parameter. */
    val titledRenderers: List<MethodReference>,
    /** Title/icon/click renderer calls whose icon is the icon mapper's result. */
    val anchorRowCalls: List<DrawerItemRowCall>,
) {
    /** All item branches that load [resourceId] directly. */
    fun branchesWithTitle(resourceId: Int): List<DrawerItemBranch> =
        branches.filter { resourceId in it.titleResourceIds }

    fun branchesWithNestedTitle(resourceId: Int): List<DrawerItemBranch> =
        branches.filter { resourceId in it.nestedTitleResourceIds }

    /** The icon field of the single item whose title mapper branch loads [resourceId]. */
    fun uniqueIconFieldForTitle(resourceId: Int, label: String): FieldReference {
        val branch =
            branchesWithTitle(resourceId).singleOrNull()
                ?: throw PatchException(
                    "Expected one NewX drawer item for $label, found " +
                        branchesWithTitle(resourceId).joinToString { it.itemType },
                )
        val iconField =
            branch.iconField
                ?: throw PatchException("NewX drawer item ${branch.itemType} for $label has no static icon")
        requireUniqueIconBranch(iconField, label)
        return iconField
    }

    /** Runtime icon gating is only sound when exactly one item maps to the icon field. */
    fun requireUniqueIconBranch(iconField: FieldReference, label: String) {
        val owners = branches.filter { it.iconField?.toString() == iconField.toString() }
        if (owners.size != 1) {
            throw PatchException(
                "NewX drawer icon $iconField for $label must belong to exactly one item, found " +
                    owners.joinToString { it.itemType }.ifEmpty { "none" },
            )
        }
    }
}

/** Returns null on title-row releases, which have no item title mapper. */
internal fun resolveDrawerItemModelOrNull(): DrawerItemModel? {
    val profileTitleId = resourceId(ResourceType.STRING, PROFILE_TITLE_RESOURCE).toInt()
    val drawerClasses = buildList { classDefForEach { if (it.type.startsWith(DRAWER_SCOPE)) add(it) } }
    return resolveDrawerItemModel(drawerClasses, profileTitleId)
}

/**
 * Pure resolver over the drawer-scoped classes; [profileTitleId] is the drawer_profile_title
 * string id. Exposed for fixtures.
 */
internal fun resolveDrawerItemModel(
    drawerClasses: List<com.android.tools.smali.dexlib2.iface.ClassDef>,
    profileTitleId: Int,
): DrawerItemModel? {
    val stringNamespace = profileTitleId ushr 16
    val drawerMethods = drawerClasses.flatMap { classDef -> classDef.methods.map { classDef to it } }
    val methodsByDescriptor = drawerMethods.map { it.second }.associateBy { it.descriptor() }

    val titleMappers =
        drawerMethods.filter { (_, method) ->
            method.isStaticMethod() &&
                method.returnType == STRING_DESCRIPTOR &&
                method.isItemTitleMapperShape() &&
                method.code().any { it.narrowLiteralOrNull() == profileTitleId }
        }
    val (ownerClassDef, titleMapper) =
        requireAtMostOne("NewX drawer item title mapper", titleMappers) { it.second.descriptor() } ?: return null
    val itemBaseType = titleMapper.parameterTypes.first().toString()

    val iconMappers =
        ownerClassDef.methods.filter { method ->
            method.isStaticMethod() &&
                method.returnType.startsWith(ICONS_DESCRIPTOR_PREFIX) &&
                method.parameterTypes.map(CharSequence::toString) == listOf(itemBaseType)
        }
    val iconMapper = requireExactlyOne("NewX drawer item icon mapper in ${ownerClassDef.type}", iconMappers)

    val titleBranches = parseItemBranches(titleMapper)
    val iconBranches = parseItemBranches(iconMapper)
    val nestedMappers =
        ownerClassDef.methods.filter { method ->
            method.isStaticMethod() &&
                method.returnType == STRING_DESCRIPTOR &&
                method.parameterTypes.any { it.toString() == COMPOSER_DESCRIPTOR } &&
                method.descriptor() != titleMapper.descriptor()
        }.associateBy { it.descriptor() }

    val itemTypes = (titleBranches.keys + iconBranches.keys).distinct()
    val branches =
        itemTypes.map { itemType ->
            val titleCode = titleBranches[itemType].orEmpty()
            val direct =
                titleCode.mapNotNull { it.narrowLiteralOrNull() }
                    .filter { it ushr 16 == stringNamespace }
                    .distinct()
            val nested =
                titleCode.mapNotNull { instruction ->
                    if (!instruction.isStaticInvoke()) return@mapNotNull null
                    nestedMappers[instruction.getReference<MethodReference>()?.descriptor()]
                }.flatMap { nestedMapper ->
                    nestedMapper.code().mapNotNull { it.narrowLiteralOrNull() }
                        .filter { it ushr 16 == stringNamespace }
                }.distinct()
            val icons =
                iconBranches[itemType].orEmpty().mapNotNull { instruction ->
                    if (instruction.opcode != Opcode.SGET_OBJECT) return@mapNotNull null
                    instruction.getReference<FieldReference>()
                        ?.takeIf { it.type.startsWith(ICONS_DESCRIPTOR_PREFIX) }
                }.distinctBy(FieldReference::toString)
            if (icons.size > 1) {
                throw PatchException(
                    "NewX drawer item $itemType maps to several icons: ${icons.joinToString()}",
                )
            }
            DrawerItemBranch(itemType, direct, nested, icons.singleOrNull())
        }
    if (branches.none { profileTitleId in it.titleResourceIds }) {
        throw PatchException("NewX drawer item title mapper has no branch loading $PROFILE_TITLE_RESOURCE")
    }

    val titleMapperRef = titleMapper.reference()
    val iconMapperRef = iconMapper.reference()

    // TITLED RENDERERS: every static call that receives the mapped title as a String argument.
    val titledCalls =
        drawerMethods.flatMap { (_, method) ->
            val code = method.code()
            code.indices.flatMap { index ->
                val instruction = code[index]
                if (!instruction.isStaticInvoke()) return@flatMap emptyList()
                if (instruction.getReference<MethodReference>()?.descriptor() != titleMapperRef.descriptor()) {
                    return@flatMap emptyList()
                }
                val result = code.getOrNull(index + 1)
                    ?.takeIf { it.opcode == Opcode.MOVE_RESULT_OBJECT } as? OneRegisterInstruction
                    ?: return@flatMap emptyList()
                code.forwardStringArgumentCallees(index + 1, result.registerA)
            }
        }.distinctBy { it.descriptor() }
            .filter { callee ->
                callee.definingClass.startsWith(DRAWER_SCOPE) &&
                    callee.returnType == "V" &&
                    callee.parameterTypes.count { it.toString() == STRING_DESCRIPTOR } == 1
            }
    if (titledCalls.isEmpty()) {
        throw PatchException("NewX drawer item title mapper result reaches no row renderer")
    }

    // ITEM RENDERER: forwards one parameter to the icon mapper and its title to a row renderer.
    data class ItemRendererCandidate(
        val method: MethodReference,
        val itemParameterIndex: Int,
        val clickParameterIndex: Int,
        val innerRenderers: List<MethodReference>,
    )
    val itemRendererCandidates =
        titledCalls.mapNotNull { callee ->
            val method = methodsByDescriptor[callee.descriptor()] ?: return@mapNotNull null
            val code = method.code()
            val titleParameterIndex =
                callee.parameterTypes.indexOfFirst { it.toString() == STRING_DESCRIPTOR }
            val itemParameterIndices =
                code.indices.mapNotNull { index ->
                    val instruction = code[index]
                    if (!instruction.isStaticInvoke()) return@mapNotNull null
                    if (instruction.getReference<MethodReference>()?.descriptor() != iconMapperRef.descriptor()) {
                        return@mapNotNull null
                    }
                    val source = code.traceObjectSource(index, instruction.registersUsed.single(), method)
                    (source as? ObjectSource.Parameter)?.index
                }.distinct()
            val itemParameterIndex =
                requireAtMostOne("NewX drawer item parameter for $callee", itemParameterIndices) ?: return@mapNotNull null

            val innerCalls =
                code.indices.mapNotNull { index ->
                    val instruction = code[index]
                    if (!instruction.isStaticInvoke()) return@mapNotNull null
                    val renderer = instruction.getReference<MethodReference>() ?: return@mapNotNull null
                    if (!renderer.definingClass.startsWith(DRAWER_SCOPE) || renderer.returnType != "V") {
                        return@mapNotNull null
                    }
                    val titleSlot = renderer.parameterTypes.indexOfFirst { it == STRING_DESCRIPTOR }
                    if (titleSlot < 0 || renderer.parameterTypes.count { it == STRING_DESCRIPTOR } != 1) {
                        return@mapNotNull null
                    }
                    val argumentRegisters = instruction.argumentRegisters(renderer) ?: return@mapNotNull null
                    val titleSource =
                        code.traceObjectSource(index, argumentRegisters[titleSlot], method)
                    if ((titleSource as? ObjectSource.Parameter)?.index != titleParameterIndex) {
                        return@mapNotNull null
                    }
                    Triple(index, renderer, argumentRegisters)
                }
            if (innerCalls.isEmpty()) return@mapNotNull null
            val clickParameterIndices =
                innerCalls.mapNotNull { (index, renderer, argumentRegisters) ->
                    val clickSlot = renderer.parameterTypes.indexOfFirst { it == FUNCTION0_DESCRIPTOR }
                    if (clickSlot < 0 || renderer.parameterTypes.count { it == FUNCTION0_DESCRIPTOR } != 1) {
                        return@mapNotNull null
                    }
                    (code.traceObjectSource(index, argumentRegisters[clickSlot], method) as? ObjectSource.Parameter)
                        ?.index
                }.distinct()
            val clickParameterIndex =
                requireAtMostOne("NewX drawer click parameter for $callee", clickParameterIndices) ?: return@mapNotNull null
            ItemRendererCandidate(
                method = callee,
                itemParameterIndex = itemParameterIndex,
                clickParameterIndex = clickParameterIndex,
                innerRenderers = innerCalls.map { it.second }.distinctBy { it.descriptor() },
            )
        }
    val itemRenderer =
        itemRendererCandidates.singleOrNull()
            ?: throw PatchException(
                "Expected one NewX drawer item renderer, found " +
                    itemRendererCandidates.joinToString { it.method.descriptor() }.ifEmpty { "none" } +
                    "; titled renderers: " + titledCalls.joinToString { it.descriptor() },
            )
    if (callerParameterType(itemRenderer.method, itemRenderer.itemParameterIndex)
            .let { it == STRING_DESCRIPTOR || it == FUNCTION0_DESCRIPTOR || it == COMPOSER_DESCRIPTOR }
    ) {
        throw PatchException("NewX drawer item renderer item parameter has an unexpected type: ${itemRenderer.method}")
    }
    if (callerParameterType(itemRenderer.method, itemRenderer.clickParameterIndex) != FUNCTION0_DESCRIPTOR) {
        throw PatchException("NewX drawer item renderer click parameter is not a Function0: ${itemRenderer.method}")
    }

    // The item renderer itself is not guarded: navigation captures its click at entry, and its
    // inner row renderers receive the same title and are guarded instead.
    val titledRenderers =
        (titledCalls.filter { it.descriptor() != itemRenderer.method.descriptor() } + itemRenderer.innerRenderers)
            .distinctBy { it.descriptor() }

    // ANCHOR ROWS: title/icon/click renderer calls whose icon is the icon mapper's result.
    val anchorRowCalls =
        drawerMethods.flatMap { (_, method) ->
            val code = method.code()
            val ordinals = mutableMapOf<String, Int>()
            code.indices.mapNotNull { index ->
                val instruction = code[index]
                if (!instruction.isStaticInvoke()) return@mapNotNull null
                val renderer = instruction.getReference<MethodReference>() ?: return@mapNotNull null
                val ordinal = ordinals.getOrDefault(renderer.descriptor(), 0)
                ordinals[renderer.descriptor()] = ordinal + 1
                if (!renderer.isSnapshotRowRenderer()) return@mapNotNull null
                if (instruction !is Instruction3rc || instruction.registerCount != renderer.parameterTypes.size) {
                    return@mapNotNull null
                }
                val iconSlot = renderer.parameterTypes.indexOfFirst { it.startsWith(ICONS_DESCRIPTOR_PREFIX) }
                val source = code.traceObjectSource(index, instruction.startRegister + iconSlot, method)
                val sourceCall = (source as? ObjectSource.Result)?.let { code[it.invokeIndex] }
                    ?: return@mapNotNull null
                if (sourceCall.getReference<MethodReference>()?.descriptor() != iconMapperRef.descriptor()) {
                    return@mapNotNull null
                }
                DrawerItemRowCall(method.reference(), ordinal, renderer.toImmutableReference())
            }
        }
    if (anchorRowCalls.isEmpty()) {
        throw PatchException("NewX drawer has no title/icon/click row fed by the item icon mapper")
    }

    return DrawerItemModel(
        ownerClass = ownerClassDef.type,
        branches = branches,
        itemRenderer = itemRenderer.method.toImmutableReference(),
        itemParameterIndex = itemRenderer.itemParameterIndex,
        clickParameterIndex = itemRenderer.clickParameterIndex,
        titledRenderers = titledRenderers.map { it.toImmutableReference() },
        anchorRowCalls = anchorRowCalls,
    )
}

/** Resolves the mutable method for a reference resolved from read-only class definitions. */
internal fun MethodReference.mutableMethod(): MutableMethod {
    val candidates =
        mutableClassDefBy(definingClass).methods.filter { it.descriptor() == descriptor() }
    return candidates.singleOrNull()
        ?: throw PatchException("Expected one mutable NewX drawer method ${descriptor()}, found ${candidates.size}")
}

/** Index of the [DrawerItemRowCall] in the current (possibly already patched) instructions. */
internal fun MutableMethod.currentIndexOf(call: DrawerItemRowCall): Int {
    val indices =
        instructions.withIndex().filter { (_, instruction) ->
            instruction.isStaticInvoke() &&
                instruction.getReference<MethodReference>()?.descriptor() == call.renderer.descriptor()
        }.map { it.index }
    return indices.getOrNull(call.rendererCallOrdinal)
        ?: throw PatchException("NewX drawer anchor row call disappeared: ${call.method.descriptor()}")
}

internal fun MethodReference.descriptor(): String =
    "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

/** Class name as returned by Class.getName() for a type descriptor. */
internal fun String.toJavaClassName(): String = removePrefix("L").removeSuffix(";").replace('/', '.')

/** Single title, icon, click and Composer renderer without wide parameters. */
internal fun MethodReference.isSnapshotRowRenderer(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return returnType == "V" &&
        definingClass.startsWith(DRAWER_SCOPE) &&
        parameters.count { it == STRING_DESCRIPTOR } == 1 &&
        parameters.count { it.startsWith(ICONS_DESCRIPTOR_PREFIX) } == 1 &&
        parameters.count { it == FUNCTION0_DESCRIPTOR } == 1 &&
        parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
        parameters.none { it == "J" || it == "D" }
}

private sealed interface ObjectSource {
    data class Parameter(val index: Int) : ObjectSource
    data class Result(val invokeIndex: Int) : ObjectSource
    data object Other : ObjectSource
}

private fun Method.isStaticMethod(): Boolean = AccessFlags.STATIC.isSet(accessFlags)

private fun Method.isItemTitleMapperShape(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return parameters.size == 3 &&
        parameters[0].startsWith("L") &&
        parameters[0] != COMPOSER_DESCRIPTOR &&
        parameters[1] == "Z" &&
        parameters[2] == COMPOSER_DESCRIPTOR
}

private fun Method.code(): List<Instruction> = implementation?.instructions?.toList().orEmpty()

private fun Method.reference(): MethodReference =
    ImmutableMethodReference(definingClass, name, parameterTypes.map(CharSequence::toString), returnType)

private fun MethodReference.toImmutableReference(): MethodReference =
    ImmutableMethodReference(definingClass, name, parameterTypes.map(CharSequence::toString), returnType)

private fun MethodReference.parameterIndices(type: String): List<Int> =
    parameterTypes.indices.filter { parameterTypes[it].toString() == type }

private fun callerParameterType(method: MethodReference, index: Int): String =
    method.parameterTypes[index].toString()

private fun Instruction.isStaticInvoke(): Boolean =
    opcode == Opcode.INVOKE_STATIC || opcode == Opcode.INVOKE_STATIC_RANGE

private fun Instruction.narrowLiteralOrNull(): Int? =
    when (opcode) {
        Opcode.CONST, Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST_HIGH16 ->
            (this as? NarrowLiteralInstruction)?.narrowLiteral
        else -> null
    }

/** Argument registers per parameter slot; null if a wide parameter makes the mapping ambiguous. */
private fun Instruction.argumentRegisters(reference: MethodReference): List<Int>? {
    if (reference.parameterTypes.any { it.toString() == "J" || it.toString() == "D" }) return null
    val registers = registersUsed
    return registers.takeIf { it.size == reference.parameterTypes.size }
}

private fun Instruction.writtenRegisters(): List<Int> {
    if (!opcode.setsRegister()) return emptyList()
    val destination = (this as? OneRegisterInstruction)?.registerA ?: return emptyList()
    return if (opcode.setsWideRegister()) listOf(destination, destination + 1) else listOf(destination)
}

private val OBJECT_MOVES = setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)

private fun List<Instruction>.branchTargetIndex(index: Int): Int? {
    val instruction = this[index] as? OffsetInstruction ?: return null
    var address = 0
    val addresses = IntArray(size)
    forEachIndexed { i, it ->
        addresses[i] = address
        address += it.codeUnits
    }
    val target = addresses[index] + instruction.codeOffset
    return addresses.indexOfFirst { it == target }.takeIf { it >= 0 }
}

/**
 * Collects static callees that receive [register] (or a moved copy) as a String argument on the
 * fallthrough/goto path. Conditional branches are not followed; gotos are, once.
 */
private fun List<Instruction>.forwardStringArgumentCallees(startIndex: Int, register: Int): List<MethodReference> {
    val aliases = mutableSetOf(register)
    val callees = mutableListOf<MethodReference>()
    val visited = mutableSetOf<Int>()
    var index = startIndex + 1
    while (index in indices && aliases.isNotEmpty() && visited.add(index)) {
        val instruction = this[index]
        if (instruction.isStaticInvoke()) {
            val reference = instruction.getReference<MethodReference>()
            val arguments = reference?.let { instruction.argumentRegisters(it) }
            if (reference != null && arguments != null &&
                reference.parameterIndices(STRING_DESCRIPTOR).any { arguments[it] in aliases }
            ) {
                callees += reference
            }
        }
        when {
            instruction.opcode in OBJECT_MOVES -> {
                val move = instruction as TwoRegisterInstruction
                if (move.registerB in aliases) aliases += move.registerA else aliases -= move.registerA
            }
            else -> aliases -= instruction.writtenRegisters().toSet()
        }
        when {
            instruction.opcode == Opcode.GOTO || instruction.opcode == Opcode.GOTO_16 ||
                instruction.opcode == Opcode.GOTO_32 ->
                index = branchTargetIndex(index) ?: return callees
            !instruction.opcode.canContinue() ->
                return callees
            else -> index++
        }
    }
    return callees
}

/**
 * Traces an object register backward through object moves. A `move-result-object r` followed by
 * `if-nez r` and a static fallback `sget-object r` is treated as the invoke result (null fallback).
 */
private fun List<Instruction>.traceObjectSource(callIndex: Int, register: Int, method: Method): ObjectSource {
    var current = register
    for (index in callIndex - 1 downTo 0) {
        val instruction = this[index]
        if (current !in instruction.writtenRegisters()) continue
        when {
            // check-cast narrows the static type of the same value.
            instruction.opcode == Opcode.CHECK_CAST -> continue
            instruction.opcode in OBJECT_MOVES -> current = (instruction as TwoRegisterInstruction).registerB
            instruction.opcode == Opcode.MOVE_RESULT_OBJECT ->
                return if (index > 0) ObjectSource.Result(index - 1) else ObjectSource.Other
            instruction.opcode == Opcode.SGET_OBJECT -> {
                val nullCheck = getOrNull(index - 1)
                val result = getOrNull(index - 2)
                val isNullFallback =
                    nullCheck?.opcode == Opcode.IF_NEZ &&
                        (nullCheck as OneRegisterInstruction).registerA == current &&
                        result?.opcode == Opcode.MOVE_RESULT_OBJECT &&
                        (result as OneRegisterInstruction).registerA == current &&
                        branchTargetIndex(index - 1) == index + 1
                return if (isNullFallback && index >= 3) ObjectSource.Result(index - 3) else ObjectSource.Other
            }
            else -> return ObjectSource.Other
        }
    }
    val implementation = method.implementation ?: return ObjectSource.Other
    val parameterWidths = method.parameterTypes.map { if (it == "J" || it == "D") 2 else 1 }
    var parameterRegister = implementation.registerCount - parameterWidths.sum()
    if (current < parameterRegister) return ObjectSource.Other
    parameterWidths.forEachIndexed { parameterIndex, width ->
        if (current == parameterRegister) return ObjectSource.Parameter(parameterIndex)
        parameterRegister += width
    }
    return ObjectSource.Other
}

/**
 * Splits a mapper into item class -> reachable branch instructions. Each dispatch is either
 * `sget-object X->f:X` + Intrinsics.areEqual(item, X) or `instance-of item, X`, followed by
 * if-eqz (branch falls through) or if-nez (branch jumps). Branch bodies are walked along every
 * edge until a return or throw.
 */
internal fun parseItemBranches(mapper: Method): Map<String, List<Instruction>> {
    val code = mapper.code()
    val implementation = mapper.implementation ?: return emptyMap()
    val parameterWidth = mapper.parameterTypes.sumOf { type -> if (type == "J" || type == "D") 2 else 1 }
    val itemRegister = implementation.registerCount - parameterWidth
    val starts = linkedMapOf<String, MutableList<Int>>()
    code.indices.forEach { index ->
        val instruction = code[index]
        val dispatch: Pair<String, Int> =
            when (instruction.opcode) {
                // instance-of vB, item, X
                Opcode.INSTANCE_OF -> {
                    val check = instruction as TwoRegisterInstruction
                    if (check.registerB != itemRegister) return@forEach
                    val type = instruction.getReference<TypeReference>()?.type ?: return@forEach
                    type to check.registerA
                }
                // sget-object vS, X->f:X ; invoke-static {item, vS}, Intrinsics.areEqual ; move-result vB
                Opcode.INVOKE_STATIC -> {
                    if (instruction.getReference<MethodReference>()?.descriptor() != INTRINSICS_ARE_EQUAL) return@forEach
                    val operands = instruction.registersUsed
                    if (operands.size != 2 || itemRegister !in operands) return@forEach
                    val singletonRegister = operands.first { it != itemRegister }
                    val writer =
                        (index - 1 downTo 0).firstOrNull { previous ->
                            singletonRegister in code[previous].writtenRegisters()
                        } ?: return@forEach
                    val field =
                        code[writer].takeIf { it.opcode == Opcode.SGET_OBJECT }
                            ?.getReference<FieldReference>()
                            ?.takeIf { it.definingClass == it.type }
                            ?: return@forEach
                    val result = code.getOrNull(index + 1)
                        ?.takeIf { it.opcode == Opcode.MOVE_RESULT } as? OneRegisterInstruction
                        ?: return@forEach
                    field.type to result.registerA
                }
                else -> return@forEach
            }
        val (itemType, conditionRegister) = dispatch
        // The compiler may hoist unrelated constants between the test and its branch.
        val conditionIndex =
            ((index + 1)..minOf(index + 4, code.lastIndex)).firstOrNull { candidate ->
                val condition = code[candidate]
                (condition.opcode == Opcode.IF_EQZ || condition.opcode == Opcode.IF_NEZ) &&
                    (condition as OneRegisterInstruction).registerA == conditionRegister
            } ?: return@forEach
        val interrupted =
            ((index + 1) until conditionIndex).any { between ->
                val candidate = code[between]
                val isResult = candidate.opcode == Opcode.MOVE_RESULT && between == index + 1
                !isResult && (conditionRegister in candidate.writtenRegisters() ||
                    candidate is OffsetInstruction || candidate.getReference<MethodReference>() != null)
            }
        if (interrupted) return@forEach
        val start =
            when (code[conditionIndex].opcode) {
                Opcode.IF_EQZ -> conditionIndex + 1
                else -> code.branchTargetIndex(conditionIndex) ?: return@forEach
            }
        starts.getOrPut(itemType) { mutableListOf() } += start
    }
    return starts.mapValues { (_, branchStarts) ->
        val reachable = sortedSetOf<Int>()
        val pending = ArrayDeque(branchStarts)
        while (pending.isNotEmpty()) {
            val index = pending.removeFirst()
            if (index !in code.indices || !reachable.add(index)) continue
            val instruction = code[index]
            val opcode = instruction.opcode
            when {
                opcode == Opcode.GOTO || opcode == Opcode.GOTO_16 || opcode == Opcode.GOTO_32 ->
                    code.branchTargetIndex(index)?.let(pending::add)
                opcode.format == Format.Format21t || opcode.format == Format.Format22t -> {
                    pending += index + 1
                    code.branchTargetIndex(index)?.let(pending::add)
                }
                instruction is OffsetInstruction ->
                    throw PatchException("Unsupported NewX drawer item mapper branch $opcode in $mapper")
                opcode.canContinue() -> pending += index + 1
            }
        }
        reachable.map(code::get)
    }
}
