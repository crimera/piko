/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.profiletabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private object CustomiseProfileTabsFingerprint : Fingerprint(
    returnType = "Ljava/util/ArrayList;",
    strings =
        listOf(
            "fragment_page_number",
            "arg_is_unlimited_timeline",
            "statuses_count",
            "tweets",
            "blue_business_affiliates_list_consumption_ui_enabled",
        ),
)

@Suppress("unused")
val customiseProfileTabsPatch =
    bytecodePatch(
        name = "Customize profile tabs",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            val method = CustomiseProfileTabsFingerprint.method
            val instructions = method.instructions

            val returnObjInstruction = instructions.lastOrNull { it.opcode == Opcode.RETURN_OBJECT }
                ?: throw PatchException("Failed to find RETURN_OBJECT in ${CustomiseProfileTabsFingerprint.definingClass}")

            val returnObj_loc = returnObjInstruction.location.index
            val r0 = method.getInstruction<OneRegisterInstruction>(returnObj_loc).registerA

            val METHOD =
                """
                invoke-static {v$r0}, $CUSTOMISE_DESCRIPTOR;->profiletabs(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                move-result-object v$r0
                """.trimIndent()

            method.addInstructions(returnObj_loc, METHOD)

            val last_invoke_static = instructions.lastOrNull { it.opcode == Opcode.INVOKE_STATIC }
                ?: throw PatchException("Failed to find INVOKE_STATIC in ${CustomiseProfileTabsFingerprint.definingClass}")

            val last_if_nez_instruction = instructions.lastOrNull { it.opcode == Opcode.IF_NEZ }
                ?: throw PatchException("Failed to find IF_NEZ in ${CustomiseProfileTabsFingerprint.definingClass}")

            val last_if_nez_loc = last_if_nez_instruction.location.index
            val r2 = method.getInstruction<OneRegisterInstruction>(last_if_nez_loc).registerA

            val op = instructions.getOrNull(last_if_nez_loc - 1)?.opcode

            if (op == Opcode.CONST_4) { // before 10.43
                val last_if_eqz_instruction = instructions.lastOrNull { it.opcode == Opcode.IF_EQZ }
                    ?: throw PatchException("Failed to find IF_EQZ in ${CustomiseProfileTabsFingerprint.definingClass}")

                val last_if_eqz = last_if_eqz_instruction.location.index
                val r1 = method.getInstruction<OneRegisterInstruction>(last_if_eqz).registerA

                // it works don't ask me how
                method.removeInstruction(last_if_eqz)
                method.removeInstruction(last_if_eqz)
                method.removeInstruction(last_if_eqz)

                method.addInstructionsWithLabels(
                    last_if_eqz,
                    """
                    if-eqz v$r1, :check2
                    const/4 v$r2, 0x1
                    :check2
                    if-nez v$r2, :check1
                    """.trimIndent(),
                    ExternalLabel("check1", last_invoke_static),
                )
            } else { // after 10.43
                method.removeInstruction(last_if_nez_loc)

                method.addInstructionsWithLabels(
                    last_if_nez_loc,
                    """
                    if-nez v$r2, :check1
                    """.trimIndent(),
                    ExternalLabel("check1", last_invoke_static),
                )
            }
            enableSettings("profileTabCustomisation")
        }
    }
