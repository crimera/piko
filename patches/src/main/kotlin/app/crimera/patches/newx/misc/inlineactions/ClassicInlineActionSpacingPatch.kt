package app.crimera.patches.newx.misc.inlineactions

import app.crimera.patches.newx.models.NewXInlineActionKindShape
import app.crimera.patches.newx.models.ResolvedNewXInlineActionBarLayout
import app.crimera.patches.newx.models.INLINE_ACTION_BAR_SCOPE
import app.crimera.patches.newx.models.resolvedNewXInlineActionBarLayout
import app.crimera.patches.newx.models.newXInlineActionModelResolutionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.EXTENSION_PACKAGE
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21s
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val INLINE_ACTION_BAR_SPACING_DESCRIPTOR =
    "$EXTENSION_PACKAGE/misc/InlineActionBarSpacing;"
private const val CLASSIC_GAP_DP_DESCRIPTOR =
    "$INLINE_ACTION_BAR_SPACING_DESCRIPTOR->classicGapDp()F"
private const val APPLY_CLASSIC_SPACING_DESCRIPTOR =
    "$INLINE_ACTION_BAR_SPACING_DESCRIPTOR->applyClassicSpacing(Ljava/util/List;IIII)V"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"

/** Setting that keeps the app's own packed-slot spacing instead of the classic spacing. */
internal const val NATIVE_INLINE_ACTION_SPACING_SETTING =
    "newx.appearance.inline_action_native_spacing"

/** Scratch registers reserved below the parameter block for the injected call. */
private const val SPACING_SCRATCH_REGISTERS = 8

/**
 * Lays the post action bar out with the spacing NewX used before 12.28.0-alpha.04: counted actions
 * each take an equal share of the row width and the icon-only actions keep their intrinsic size,
 * stepped by a fixed 8dp. The packed-slot bar that replaced it distributes the leftover width into
 * every gap, so hiding an action opens a hole and drifts the trailing icon-only group to the right
 * edge.
 *
 * The measure builds its per-child slot list before the fitting loop; this patch rewrites that list
 * in place right before the loop runs, using the measure's own counted slot floor to tell counted
 * actions from icon-only ones. Targets that already ship the classic weighted layout
 * (12.27.0-prod.01, 12.28.0-alpha.01) are left untouched.
 */
@Suppress("unused")
val classicInlineActionSpacingPatch =
    bytecodePatch(
        name = "NewX: Classic inline action spacing",
        description =
            "Lays out post action bars with the spacing NewX used before 12.28.0-alpha.04: " +
                "counted actions share the row width and icon-only actions keep their intrinsic " +
                "size, so hiding an action never leaves a gap.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXInlineActionModelResolutionPatch)

        val nativeSpacing =
            newXSettings {
                category(Categories.POST_ACTIONS_MEDIA) {
                    toggle(
                        id = NATIVE_INLINE_ACTION_SPACING_SETTING,
                        strings = settingStrings("piko_newx_native_inline_action_spacing"),
                        order = 110,
                        defaultValue = false,
                    )
                }
            }

        execute {
            // Contributed even when the target already ships the classic layout, so the setting
            // exists on every supported release.
            nativeSpacing

            val layout = resolvedNewXInlineActionBarLayout()
            if (layout.shape == NewXInlineActionKindShape.BOOLEAN_CLASSIC) {
                // Validated classic target: the app already renders this spacing.
                return@execute
            }
            patchClassicInlineActionSpacing(layout)
        }
    }

context(context: BytecodePatchContext)
private fun patchClassicInlineActionSpacing(layout: ResolvedNewXInlineActionBarLayout) {
    val measure = requireExactlyOne(
        "NewX inline-action bar measure policy",
        Fingerprint(
            definingClass = INLINE_ACTION_BAR_SCOPE,
            filters = listOf(methodCall(smali = "Ljava/util/LinkedHashSet;-><init>()V")),
            custom = { method, _ ->
                method.parameterTypes.size == 3 &&
                    method.parameterTypes[1].toString() == "Ljava/util/List;" &&
                    method.parameterTypes[2].toString() == "J" &&
                    method.returnType.toString().startsWith("L")
            },
        ).scopedMatchAll(),
    ).originalMethod

    val originalRegisterCount =
        measure.implementation?.registerCount
            ?: throw PatchException("NewX inline-action bar measure has no implementation: $measure")
    val scratchBase = originalRegisterCount
    if (scratchBase + SPACING_SCRATCH_REGISTERS - 1 > 255) {
        throw PatchException(
            "NewX inline-action bar measure needs scratch registers above v255: " +
                "registers=$originalRegisterCount, ${measure}",
        )
    }

    // The clone materializes the parameter block above the measured local registers and adds the
    // scratch locals, so every instruction index below is derived from the cloned method.
    // Parameters occupy the highest registers, so the scratch locals only exist below them once
    // the parameter block itself has room above the added registers.
    val method =
        measure.cloneMutable(
            additionalRegisters =
                measure.numberOfParameterRegisters + SPACING_SCRATCH_REGISTERS,
        )
    val declaringClass = context.mutableClassDefBy(measure.definingClass)
    declaringClass.methods.remove(measure)
    declaringClass.methods.add(method)
    if (com.android.tools.smali.dexlib2.AccessFlags.STATIC.isSet(method.accessFlags)) {
        throw PatchException("NewX inline-action bar measure is unexpectedly static: $method")
    }

    val instructions = method.instructions.toList()
    val linkedHashSetIndex = requireExactlyOne(
        "NewX inline-action bar measure LinkedHashSet allocation",
        instructions.mapIndexedNotNull { index, instruction ->
            index.takeIf {
                instruction.opcode == Opcode.INVOKE_DIRECT &&
                    instruction.getReference<MethodReference>()?.let { reference ->
                        reference.definingClass == "Ljava/util/LinkedHashSet;" &&
                            reference.name == "<init>"
                    } == true
            }
        },
    )

    val availableRegister =
        requireExactlyOne(
            "NewX inline-action bar measure available-width computation",
            instructions.mapIndexedNotNull { index, instruction ->
                index.takeIf {
                    instruction.opcode == Opcode.INVOKE_STATIC &&
                        instruction.getReference<MethodReference>()?.let { reference ->
                            reference.definingClass == "Lkotlin/ranges/RangesKt;" &&
                                reference.name == "coerceAtLeast" &&
                                reference.parameterTypes.map(CharSequence::toString) ==
                                listOf("I", "I")
                        } == true
                }
            },
        ).let { index ->
            val result = instructions.getOrNull(index + 1)
            if (result?.opcode != Opcode.MOVE_RESULT) {
                throw PatchException(
                    "NewX inline-action bar measure available width has no result: ${method}",
                )
            }
            (result as OneRegisterInstruction).registerA
        }

    // The measure's counted slot is `max(content, floor)`; icon-only slots are a smaller constant
    // or a half-width, so the floor separates the two groups.
    val countedFloorIndex = requireExactlyOne(
        "NewX inline-action bar measure counted slot floor",
        instructions.take(linkedHashSetIndex).mapIndexedNotNull { index, instruction ->
            index.takeIf {
                instruction.opcode == Opcode.INVOKE_STATIC &&
                    instruction.getReference<MethodReference>()?.let { reference ->
                        reference.definingClass == "Ljava/lang/Math;" &&
                            reference.name == "max" &&
                            reference.parameterTypes.map(CharSequence::toString) == listOf("I", "I")
                    } == true
            }
        },
    )
    val countedFloorOperands = instructions[countedFloorIndex].registersUsed
    if (countedFloorOperands.size != 2) {
        throw PatchException("NewX inline-action bar counted slot floor has odd shape: $method")
    }
    val countedFloorRegister = countedFloorOperands[1]

    val (minGapRegister, densityCall) = readMinGapAndDensity(method, instructions)

    val (slotsListRegister, slotListAppendIndex) = readSlotListRegister(
        instructions = instructions,
        countedFloorIndex = countedFloorIndex,
        linkedHashSetIndex = linkedHashSetIndex,
        countedFloorRegister = countedFloorRegister,
    )
    // The floor register is reused for the accumulated width once the slot loop ends, so its value
    // is lifted inside the loop, right after the slot list append.
    val countedFloorRead = slotListAppendIndex + 1

    val injectionIndex = readFittingLoopHead(instructions, linkedHashSetIndex)
    val loopHead = instructions[injectionIndex]
    if (loopHead.opcode != Opcode.INVOKE_INTERFACE ||
        loopHead.getReference<MethodReference>()?.name != "hasNext"
    ) {
        throw PatchException(
            "NewX inline-action fitting loop head is not an iterator check: ${method}",
        )
    }

    val countedFloorScratch = scratchBase + 6
    // Insertions run from the highest index down so every index still refers to the instruction
    // list they were derived from. The floor local is seeded at the method head because it has to
    // be initialized on every path into the fitting loop.
    //
    // The hook sits on the fitting loop head, so the loop's back edge reaches it as well. Every
    // operand it reads (the slot list, the row width, the gap floor, the lifted counted floor and
    // the density) is loop invariant, and the rewrite is a fixpoint, so the extra passes cannot
    // change the layout.
    method.insertHook(
        index = injectionIndex,
        // The insertion point is the fitting-loop head and the loop's back edge targets it. The
        // spacing setup below must run once, before the loop, so the back edge has to keep jumping
        // to the original instruction instead of re-running the block on every iteration.
        relocateBranchTargets = false,
    ) {
        // The scratch locals come from the frame growth above rather than the scratch pool, so the
        // block addresses them by their reserved numbers.
        move(scratchBase, slotsListRegister, OBJECT_DESCRIPTOR)
        move(scratchBase + 1, availableRegister, "I")
        move(scratchBase + 2, minGapRegister, "I")
        move(scratchBase + 3, countedFloorScratch, "I")
        move(scratchBase + 4, densityCall.second, OBJECT_DESCRIPTOR)
        invokeStatic(methodReference(CLASSIC_GAP_DP_DESCRIPTOR))
        moveResult(scratchBase + 5, "F")
        invokeInterface(densityCall.first, scratchBase + 4, scratchBase + 5)
        moveResult(scratchBase + 5, "I")
        move(scratchBase + 4, scratchBase + 5, "I")
        invokeStatic(
            methodReference(APPLY_CLASSIC_SPACING_DESCRIPTOR),
            scratchBase,
            scratchBase + 1,
            scratchBase + 2,
            scratchBase + 3,
            scratchBase + 4,
        )
    }
    method.insertHook(countedFloorRead, relocateBranchTargets = false) {
        move(countedFloorScratch, countedFloorRegister, "I")
    }
    // The sentinel needs an 8-bit register, so it cannot come from `constInt`: `-1` would select
    // `const/4`, whose four-bit register field cannot address the scratch local.
    method.insertHook(0, relocateBranchTargets = false) {
        add(BuilderInstruction21s(Opcode.CONST_16, countedFloorScratch, -1))
    }
}

/**
 * Reads the measure's minimum gap and the density call it uses. The measure clamps the distributed
 * gap with `min(leftover / gaps, cap)` and then floors it with `max(minGap, ...)`; that `max` is the
 * only place a gap floor is applied and the `min` before it is unique in the method.
 */
private fun readMinGapAndDensity(
    measure: Method,
    instructions: List<Instruction>,
): Pair<Int, Pair<MethodReference, Int>> {
    val minCallIndex = requireExactlyOne(
        "NewX inline-action bar measure gap clamp",
        instructions.mapIndexedNotNull { index, instruction ->
            index.takeIf {
                instruction.opcode == Opcode.INVOKE_STATIC &&
                    instruction.getReference<MethodReference>()?.let { reference ->
                        reference.definingClass == "Ljava/lang/Math;" &&
                            reference.name == "min" &&
                            reference.parameterTypes.map(CharSequence::toString) == listOf("I", "I")
                    } == true
            }
        },
    )
    val densityCallIndex = minCallIndex - 2
    val densityCall = instructions.getOrNull(densityCallIndex)
    val densityReference =
        densityCall?.getReference<MethodReference>()
            ?: throw PatchException("NewX inline-action gap cap has no density call: $measure")
    if (densityCall.opcode != Opcode.INVOKE_INTERFACE ||
        densityReference.parameterTypes.map(CharSequence::toString) != listOf("F") ||
        densityReference.returnType != "I"
    ) {
        throw PatchException("NewX inline-action gap cap density call has odd shape: $measure")
    }
    val densityReceiver = densityCall.registersUsed.firstOrNull()
        ?: throw PatchException("NewX inline-action density call has no receiver: $measure")

    val maxCallIndex = minCallIndex + 2
    val maxCall = instructions.getOrNull(maxCallIndex)
    if (maxCall?.opcode != Opcode.INVOKE_STATIC ||
        maxCall.getReference<MethodReference>()?.let { reference ->
            reference.definingClass == "Ljava/lang/Math;" &&
                reference.name == "max" &&
                reference.parameterTypes.map(CharSequence::toString) == listOf("I", "I")
        } != true
    ) {
        throw PatchException("NewX inline-action gap floor is not applied by max: $measure")
    }
    val minGapRegister = maxCall.registersUsed.firstOrNull()
        ?: throw PatchException("NewX inline-action gap floor has no operands: $measure")
    return minGapRegister to (densityReference to densityReceiver)
}

/**
 * Finds the per-child slot list: the list the measure fills inside the loop that computes counted
 * slot floors. Its `add` follows the `Integer.valueOf` that wraps the computed slot.
 */
private fun readSlotListRegister(
    instructions: List<Instruction>,
    countedFloorIndex: Int,
    linkedHashSetIndex: Int,
    countedFloorRegister: Int,
): Pair<Int, Int> {
    if (countedFloorIndex < 0) {
        throw PatchException("NewX inline-action counted slot floor is never used")
    }
    val valueOfIndex = (countedFloorIndex + 1 until linkedHashSetIndex).firstOrNull { index ->
        val instruction = instructions[index]
        instruction.opcode == Opcode.INVOKE_STATIC &&
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.definingClass == "Ljava/lang/Integer;" &&
                    reference.name == "valueOf" &&
                    reference.parameterTypes.map(CharSequence::toString) == listOf("I")
            } == true
    } ?: throw PatchException("NewX inline-action slot list has no valueOf after the slot floor")
    val addIndex = (valueOfIndex + 1 until linkedHashSetIndex + 1).firstOrNull { index ->
        val instruction = instructions[index]
        instruction.opcode == Opcode.INVOKE_VIRTUAL &&
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.definingClass == "Ljava/util/ArrayList;" &&
                    reference.name == "add" &&
                    reference.parameterTypes.map(CharSequence::toString) ==
                    listOf("Ljava/lang/Object;")
            } == true
    } ?: throw PatchException("NewX inline-action slot list has no ArrayList add")
    val receiver = instructions[addIndex].registersUsed.firstOrNull()
        ?: throw PatchException("NewX inline-action slot list add has no receiver")
    if (receiver == countedFloorRegister) {
        throw PatchException("NewX inline-action slot list shares the counted floor register")
    }
    return receiver to addIndex
}

/**
 * Finds the first instruction of the fitting loop. The measure seeds it right after allocating the
 * set: `LinkedHashSet()`, `List.iterator()`, the accumulated-width local, then the iterator check
 * the loop head re-reads on every pass.
 */
private fun readFittingLoopHead(
    instructions: List<Instruction>,
    linkedHashSetIndex: Int,
): Int {
    val iteratorIndex = (linkedHashSetIndex + 1 until instructions.size).firstOrNull { index ->
        val instruction = instructions[index]
        instruction.opcode == Opcode.INVOKE_INTERFACE &&
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.name == "iterator" &&
                    reference.parameterTypes.isEmpty() &&
                    reference.returnType == "Ljava/util/Iterator;"
            } == true
    } ?: throw PatchException("NewX inline-action fitting loop has no iterator")
    val seed = instructions.getOrNull(iteratorIndex + 2)
    if (seed == null ||
        seed.opcode !in setOf(Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST) ||
        instructions.getOrNull(iteratorIndex + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT
    ) {
        throw PatchException("NewX inline-action fitting loop seed has odd shape")
    }
    return iteratorIndex + 3
}
