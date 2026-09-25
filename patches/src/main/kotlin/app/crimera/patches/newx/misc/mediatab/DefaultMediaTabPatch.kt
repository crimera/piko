/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.mediatab

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXSingleChoice
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.MEDIA_TAB_RESOLVER_DESCRIPTOR
import app.crimera.patches.newx.utils.requireAtMostOne
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Targets the NewX combined profile timeline component constructor
 * (`com/x/profile/timeline/a` — used for the combined Posts+Highlights tab and the
 * combined Photos+Videos media tab). It seeds the selected sub-tab state with
 * `MutableStateFlow(initialSubTab)` — Videos for the combined media tab — which drives
 * both the header tab label/dropdown and the displayed media grid.
 *
 * The seed value is routed through [MediaTabResolver.getEnumDefault] so the configured
 * default (Photos) wins while every other tab keeps its stock primary type.
 *
 * 12.29 adds an optional second tab-type store: when the requested initial sub-tab is not yet
 * in the loaded tab list the constructor stages it in a plain field that the tab-types
 * collector applies once the tab becomes available. That store is routed through the same
 * resolver so the configured default also wins on the deferred path. Older releases seed the
 * selected sub-tab flow directly and store no plain tab-type field.
 */

/** True when [classDef] is a combined timeline component (implements the combined-timeline contract). */
private fun isCombinedTimelineComponent(classDef: ClassDef) =
    classDef.interfaces.any { it.startsWith("Lcom/x/profile/timeline/") }

/** True when the refactored component takes `(tabTypes, initialSubTab, ...)`. */
private fun hasTabTypesAndInitialSubTab(parameterTypes: List<CharSequence>) =
    parameterTypes.size >= 3 &&
        parameterTypes[0].toString() == "Ljava/util/List;" &&
        parameterTypes[1].toString().startsWith("Lcom/x/profile/") &&
        parameterTypes[1].toString().endsWith(";")

/** True when [instruction] is the `MutableStateFlow(seedValue)` factory call. */
private fun isFlowSeed(instruction: Instruction) =
    (instruction.opcode == Opcode.INVOKE_STATIC || instruction.opcode == Opcode.INVOKE_STATIC_RANGE) &&
        ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let { reference ->
            reference.definingClass.startsWith("Lkotlinx/coroutines/flow/") &&
                reference.parameterTypes == listOf("Ljava/lang/Object;")
        } == true

/** True when [instruction] reads the `Boolean.FALSE` post-sorting default. */
private fun isBooleanFalse(instruction: Instruction) =
    instruction.opcode == Opcode.SGET_OBJECT &&
        ((instruction as? ReferenceInstruction)?.reference as? FieldReference)?.let { field ->
            field.definingClass == "Ljava/lang/Boolean;" && field.name == "FALSE"
        } == true

/**
 * True when the flow seed at [index] is the selected sub-tab state. The post-sorting state is also
 * a `MutableStateFlow`, but it is seeded with `Boolean.FALSE`; the sub-tab seed never is.
 */
internal fun isInitialSubTabSeed(
    instructions: List<Instruction>,
    index: Int,
): Boolean {
    if (!isFlowSeed(instructions[index])) return false
    val previous = instructions.getOrNull(index - 1) ?: return true
    return !isBooleanFalse(previous)
}

/**
 * True when [instruction] stores the pending initial sub-tab (12.29 shape): a plain tab-type
 * field written when the requested initial sub-tab is not yet in the loaded tab list. The
 * tab-types collector applies that field once the tab becomes available. Older releases seed
 * the selected-sub-tab flow directly and store no plain tab-type field.
 */
internal fun isPendingSubTabStore(
    instruction: Instruction,
    tabTypeDescriptor: String,
): Boolean {
    if (instruction.opcode != Opcode.IPUT_OBJECT) return false
    val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: return false
    return field.type.toString() == tabTypeDescriptor
}

/** Returns the one argument register for a one-argument flow factory call. */
private fun singleArgumentRegister(instruction: Instruction): Int? =
    when (instruction) {
        is FiveRegisterInstruction -> instruction.takeIf { it.registerCount == 1 }?.registerC
        is RegisterRangeInstruction -> instruction.takeIf { it.registerCount == 1 }?.startRegister
        else -> null
    }

/** Uses `/range` when the selected register cannot be encoded by the 35c invoke form. */
private fun resolverInvoke(register: Int) =
    if (register <= 15) {
        "invoke-static {v$register}, $MEDIA_TAB_RESOLVER_DESCRIPTOR->getEnumDefault(Ljava/lang/Object;)Ljava/lang/Object;"
    } else {
        "invoke-static/range {v$register .. v$register}, " +
            "$MEDIA_TAB_RESOLVER_DESCRIPTOR->getEnumDefault(Ljava/lang/Object;)Ljava/lang/Object;"
    }

/** The combined component seeds its selected-sub-tab flow from the initial-sub-tab parameter. */
private object NewXCombinedProfileTimelineInitialSubTabFingerprint : Fingerprint(
    definingClass = "Lcom/x/profile/timeline/",
    custom = { method, classDef ->
        method.name == "<init>" &&
            isCombinedTimelineComponent(classDef) &&
            hasTabTypesAndInitialSubTab(method.parameterTypes) &&
            method.implementation?.let { implementation ->
                val instructions = implementation.instructions.toList()
                instructions.indices.any { index -> isInitialSubTabSeed(instructions, index) }
            } == true
    },
)

@Suppress("unused")
val newXDefaultMediaTabPatch =
    bytecodePatch(
        name = "NewX: Set default media tab",
        description = "Lets you choose the default sub-tab (Photos or Videos) for the NewX profile media tab.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        newXSingleChoice(
            id = "newx.post_actions_media.media_tab_default",
            category = Categories.POST_ACTIONS_MEDIA,
            strings = settingStrings("piko_newx_media_tab_default"),
            order = 100,
            defaultValue = "Photos",
            options =
                listOf(
                    choice("Photos", "piko_newx_media_tab_photos"),
                    choice("Videos", "piko_newx_media_tab_videos"),
                ),
        )

        execute {
            val matches =
                NewXCombinedProfileTimelineInitialSubTabFingerprint.scopedMatchAllOrNull().orEmpty()
            val combinedMatch =
                requireExactlyOne(
                    label = "combined profile timeline seed",
                    candidates = matches,
                )
            val method = combinedMatch.method
            val tabTypeDescriptor = method.parameterTypes[1].toString()

            val methodInstructions = method.instructions
            val seedCandidates =
                methodInstructions.withIndex().filter { (index, _) ->
                    isInitialSubTabSeed(methodInstructions, index)
                }
            val seedInvokeIndex =
                requireExactlyOne(
                    label = "combined profile timeline sub-tab seed",
                    candidates = seedCandidates,
                ).index

            val seedValueRegister =
                singleArgumentRegister(methodInstructions[seedInvokeIndex])
                    ?: throw PatchException(
                        "Combined profile timeline seed is not a one-register invoke: " +
                            methodInstructions[seedInvokeIndex],
                    )

            // 12.29 can stage an initial sub-tab that is not yet in the loaded tab list in a
            // plain tab-type field, which the tab-types collector applies once the tab becomes
            // available. Route that pending store through the same resolver so the configured
            // default also wins on the deferred path. Older releases have no such store. The
            // store sits at a branch merge (the stock value bypasses anything placed immediately
            // before it), so re-read the stored field after the iput and write the resolved value
            // back; every path through the constructor executes that sequence exactly once. The
            // pending injection goes in first (higher index) so the seed index below stays valid.
            val pendingStoreCandidates =
                methodInstructions.withIndex().filter { (_, instruction) ->
                    isPendingSubTabStore(instruction, tabTypeDescriptor)
                }
            val pendingStore =
                requireAtMostOne(
                    label = "combined profile timeline pending sub-tab store",
                    candidates = pendingStoreCandidates,
                )
            if (pendingStore != null) {
                val pendingStoreInstruction =
                    pendingStore.value as? TwoRegisterInstruction
                        ?: throw PatchException(
                            "Combined profile timeline pending sub-tab store is not a " +
                                "two-register iput: ${pendingStore.value}",
                        )
                val pendingField =
                    (pendingStore.value as? ReferenceInstruction)?.reference as? FieldReference
                        ?: throw PatchException(
                            "Combined profile timeline pending sub-tab store has no field " +
                                "reference: ${pendingStore.value}",
                        )
                val pendingValueRegister = pendingStoreInstruction.registerA
                val pendingObjectRegister = pendingStoreInstruction.registerB
                val pendingFieldDescriptor =
                    "${pendingField.definingClass}->${pendingField.name}:${pendingField.type}"

                method.addInstructions(
                    pendingStore.index + 1,
                    """
                        iget-object v$pendingValueRegister, v$pendingObjectRegister, $pendingFieldDescriptor
                        ${resolverInvoke(pendingValueRegister)}
                        move-result-object v$pendingValueRegister
                        check-cast v$pendingValueRegister, $tabTypeDescriptor
                        iput-object v$pendingValueRegister, v$pendingObjectRegister, $pendingFieldDescriptor
                    """.trimIndent(),
                )
            }

            // The 12.29 seed instruction is the `:cond_3` branch target for the "initial sub-tab
            // is in the tab list" path, so a plain insert would land before the label and be
            // bypassed. Inject at the control-flow label so both the branch and the fall-through
            // reach the resolver.
            method.addInstructionsAtControlFlowLabel(
                seedInvokeIndex,
                """
                    ${resolverInvoke(seedValueRegister)}
                    move-result-object v$seedValueRegister
                    check-cast v$seedValueRegister, $tabTypeDescriptor
                """.trimIndent(),
            )
        }
    }
