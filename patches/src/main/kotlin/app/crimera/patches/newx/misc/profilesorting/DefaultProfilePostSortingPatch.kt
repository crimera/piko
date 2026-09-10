/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.profilesorting

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.newXSingleChoice
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.PROFILE_POST_SORTING_RESOLVER_DESCRIPTOR
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private fun isProfileTimelineComponent(classDef: ClassDef) =
    classDef.interfaces.any { it.startsWith("Lcom/x/profile/timeline/") }

private fun isBooleanFalse(instruction: Instruction): Boolean {
    if (instruction.opcode != Opcode.SGET_OBJECT) return false
    val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: return false
    return field.definingClass == "Ljava/lang/Boolean;" &&
        field.name == "FALSE" &&
        field.type == "Ljava/lang/Boolean;"
}

private fun isFlowSeed(instruction: Instruction): Boolean {
    if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
        return false
    }
    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return reference.definingClass.startsWith("Lkotlinx/coroutines/flow/") &&
        reference.parameterTypes == listOf("Ljava/lang/Object;")
}

/** Targets the profile timeline component's Boolean state that selects latest versus popular posts. */
private object NewXProfilePostSortingStateFingerprint : Fingerprint(
    definingClass = "Lcom/x/profile/timeline/",
    custom = { method, classDef ->
        method.name == "<init>" &&
            isProfileTimelineComponent(classDef) &&
            method.parameterTypes.firstOrNull() == "Ljava/util/List;" &&
            method.parameterTypes.getOrNull(1).toString().startsWith("Lcom/x/profile/") &&
            method.implementation?.instructions?.toList()?.let { methodInstructions ->
                methodInstructions.withIndex().any { (index, instruction) ->
                    isBooleanFalse(instruction) &&
                        index + 1 < methodInstructions.size &&
                        isFlowSeed(methodInstructions[index + 1])
                }
            } == true
    },
)

@Suppress("unused")
val newXDefaultProfilePostSortingPatch =
    bytecodePatch(
        name = "NewX: Set default profile post sorting",
        description = "Lets you choose whether profile posts open sorted by the most recent or popular posts.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        newXSingleChoice(
            id = "newx.post_actions_media.profile_post_sort_default",
            category = Categories.POST_ACTIONS_MEDIA,
            strings = settingStrings("piko_newx_profile_post_sort_default"),
            order = 150,
            defaultValue = "Latest",
            options =
                listOf(
                    choice("Latest", "piko_newx_profile_post_sort_latest"),
                    choice("Popular", "piko_newx_profile_post_sort_popular"),
                ),
        )

        execute {
            val matches = NewXProfilePostSortingStateFingerprint.scopedMatchAllOrNull().orEmpty()
            val match = requireExactlyOne("profile post sorting state initializer", matches)
            val method = match.method
            val methodInstructions = method.instructions
            val falseSget =
                requireExactlyOne(
                    label = "profile post sorting Boolean.FALSE seed",
                    candidates = methodInstructions.withIndex().filter { (_, instruction) ->
                        isBooleanFalse(instruction)
                    },
                )
            val falseSgetInstruction = falseSget.value as? OneRegisterInstruction
                ?: throw PatchException("Profile post sorting Boolean.FALSE seed has no destination register")
            val seedIndex = falseSget.index
            if (seedIndex + 1 >= methodInstructions.size || !isFlowSeed(methodInstructions[seedIndex + 1])) {
                throw PatchException("Profile post sorting Boolean.FALSE seed is not consumed by a flow factory")
            }

            method.addInstructions(
                seedIndex + 1,
                """
                    invoke-static {}, $PROFILE_POST_SORTING_RESOLVER_DESCRIPTOR->getDefault()Ljava/lang/Boolean;
                    move-result-object v${falseSgetInstruction.registerA}
                """.trimIndent(),
            )
        }
    }
