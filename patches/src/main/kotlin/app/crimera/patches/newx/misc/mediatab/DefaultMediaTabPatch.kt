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
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

/**
 * Targets the NewX combined profile timeline component constructor
 * (`com/x/profile/timeline/a` — used for the combined Posts+Highlights tab and the
 * combined Photos+Videos media tab). It seeds the selected sub-tab state with
 * `MutableStateFlow(primaryType)` — Videos for the combined media tab — which drives
 * both the header tab label/dropdown and the displayed media grid.
 *
 * The seed value is routed through [MediaTabResolver.getEnumDefault] so the configured
 * default (Photos) wins while every other tab keeps its stock primary type.
 */

/** True when [classDef] is a combined timeline component (implements the combined-timeline contract). */
private fun isCombinedTimelineComponent(classDef: ClassDef) =
    classDef.interfaces.any { it.startsWith("Lcom/x/profile/timeline/") }

/** True when a method takes `(primaryType, secondaryType, ...)` where both leading params share a type. */
private fun hasPrimarySecondaryPair(parameterTypes: List<CharSequence>) =
    parameterTypes.size >= 3 && parameterTypes[0] == parameterTypes[1]

/** True when the refactored component takes `(tabTypes, initialSubTab, ...)`. */
private fun hasTabTypesAndInitialSubTab(parameterTypes: List<CharSequence>) =
    parameterTypes.size >= 3 &&
        parameterTypes[0].toString() == "Ljava/util/List;" &&
        parameterTypes[1].toString().startsWith("Lcom/x/profile/") &&
        parameterTypes[1].toString().endsWith(";")

/** True when [instruction] builds the `arrayOf(primaryType, secondaryType)` used for the grouped tab list. */
private fun isPairArray(instruction: Instruction, elementType: CharSequence) =
    (instruction.opcode == Opcode.FILLED_NEW_ARRAY || instruction.opcode == Opcode.FILLED_NEW_ARRAY_RANGE) &&
        ((instruction as? ReferenceInstruction)?.reference as? TypeReference)?.type == "[$elementType"

/** True when [instruction] is the `MutableStateFlow(seedValue)` factory call. */
private fun isFlowSeed(instruction: Instruction) =
    (instruction.opcode == Opcode.INVOKE_STATIC || instruction.opcode == Opcode.INVOKE_STATIC_RANGE) &&
        ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let { reference ->
            reference.definingClass.startsWith("Lkotlinx/coroutines/flow/") &&
                reference.parameterTypes == listOf("Ljava/lang/Object;")
        } == true

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

private object NewXCombinedProfileTimelineSeedFingerprint : Fingerprint(
    definingClass = "Lcom/x/profile/timeline/",
    custom = { method, classDef ->
        // morphe's Fingerprint `name` is only an identifier — it does NOT filter by
        // method name, so the constructor guard has to live here.
        method.name == "<init>" &&
            isCombinedTimelineComponent(classDef) &&
            hasPrimarySecondaryPair(method.parameterTypes) &&
            method.implementation?.instructions?.any {
                isPairArray(it, method.parameterTypes[0])
            } == true &&
            method.implementation?.instructions?.any { isFlowSeed(it) } == true
    },
)

/**
 * Newer NewX builds changed the combined component contract to `(tabTypes, initialSubTab, ...)`.
 * The selected value is still the first object passed to the flow factory, but the call is now
 * an `/range` invoke from a parameter register instead of a 35c invoke from a local.
 */
private object NewXCombinedProfileTimelineInitialSubTabFingerprint : Fingerprint(
    definingClass = "Lcom/x/profile/timeline/",
    custom = { method, classDef ->
        method.name == "<init>" &&
            isCombinedTimelineComponent(classDef) &&
            hasTabTypesAndInitialSubTab(method.parameterTypes) &&
            method.implementation?.let { implementation ->
                val firstParameterRegister = implementation.registerCount - method.parameterTypes.size
                val initialSubTabRegister = firstParameterRegister + 1
                implementation.instructions.any {
                    isFlowSeed(it) && singleArgumentRegister(it) == initialSubTabRegister
                }
            } == true
    },
)

@Suppress("unused")
val newXDefaultMediaTabPatch =
    bytecodePatch(
        name = "NewX: Customize default media tab",
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
            val legacyMatches =
                NewXCombinedProfileTimelineSeedFingerprint.scopedMatchAllOrNull().orEmpty()
            val refactoredMatches =
                NewXCombinedProfileTimelineInitialSubTabFingerprint.scopedMatchAllOrNull().orEmpty()
            val totalMatches = legacyMatches.size + refactoredMatches.size
            if (totalMatches != 1) {
                throw PatchException(
                    "Expected one combined profile timeline seed across known shapes, found $totalMatches: " +
                        (legacyMatches + refactoredMatches).joinToString { it.originalMethod.toString() },
                )
            }

            val isLegacyShape = legacyMatches.isNotEmpty()
            val method = (legacyMatches + refactoredMatches).single().method
            val tabTypeDescriptor =
                if (isLegacyShape) method.parameterTypes[0].toString()
                else method.parameterTypes[1].toString()

            val methodInstructions = method.instructions
            val seedInvokeIndex =
                if (isLegacyShape) {
                    val pairArrayIndex =
                        methodInstructions.indexOfFirst { isPairArray(it, tabTypeDescriptor) }
                    if (pairArrayIndex == -1) {
                        throw PatchException("Missing combined profile tab array in the NewX media tab seed")
                    }
                    (pairArrayIndex + 1 until methodInstructions.size).firstOrNull {
                        isFlowSeed(methodInstructions[it])
                    } ?: -1
                } else {
                    val implementation = method.implementation
                        ?: throw PatchException("Refactored combined profile timeline component has no implementation")
                    val firstParameterRegister = implementation.registerCount - method.parameterTypes.size
                    val initialSubTabRegister = firstParameterRegister + 1
                    methodInstructions.indexOfFirst {
                        isFlowSeed(it) && singleArgumentRegister(it) == initialSubTabRegister
                    }
                }
            if (seedInvokeIndex == -1) {
                throw PatchException("Missing MutableStateFlow seed in the NewX combined profile timeline component")
            }

            val seedValueRegister =
                singleArgumentRegister(methodInstructions[seedInvokeIndex])
                    ?: throw PatchException(
                        "Combined profile timeline seed is not a one-register invoke: " +
                            methodInstructions[seedInvokeIndex],
                    )

            // The seed value is replaced in place, so it must live in a scratch register — a
            // parameter register may be read again later in the constructor on other targets.
            val implementation = method.implementation
                ?: throw PatchException("Combined profile timeline component has no implementation")
            val parameterRegisterFloor = implementation.registerCount - method.parameterTypes.size
            val initialSubTabRegister = parameterRegisterFloor + 1
            val isRefactoredInitialSubTab =
                !isLegacyShape && seedValueRegister == initialSubTabRegister
            if (seedValueRegister >= parameterRegisterFloor && !isRefactoredInitialSubTab) {
                throw PatchException(
                    "Seed value lives in a parameter register (v$seedValueRegister); adjust the fingerprint for this target",
                )
            }

            method.addInstructions(
                seedInvokeIndex,
                """
                    ${resolverInvoke(seedValueRegister)}
                    move-result-object v$seedValueRegister
                    check-cast v$seedValueRegister, $tabTypeDescriptor
                """.trimIndent(),
            )
        }
    }
