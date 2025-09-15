package app.crimera.patches.twitter.misc.customize.profiletabs

import app.crimera.patches.twitter.misc.settings.*
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal val customiseProfileTabsFingerprint =
    fingerprint {
        returns("Ljava/util/ArrayList;")
        strings(
            "fragment_page_number",
            "arg_is_unlimited_timeline",
            "statuses_count",
            "tweets",
            "blue_business_affiliates_list_consumption_ui_enabled",
        )
    }

@Suppress("unused")
val customiseProfileTabsPatch =
    bytecodePatch(
        name = "Customize profile tabs",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val method = customiseProfileTabsFingerprint.method
            val instructions = method.instructions

            val returnObj_loc = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index
            val r0 = method.getInstruction<OneRegisterInstruction>(returnObj_loc).registerA

            val METHOD =
                """
                invoke-static {v$r0}, $CUSTOMISE_DESCRIPTOR;->profiletabs(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                move-result-object v$r0
                """.trimIndent()

            method.addInstructions(returnObj_loc, METHOD)

            val last_invoke_static = instructions.last { it.opcode == Opcode.INVOKE_STATIC }

            val last_if_nez_loc = instructions.last { it.opcode == Opcode.IF_NEZ }.location.index
            val r2 = method.getInstruction<OneRegisterInstruction>(last_if_nez_loc).registerA

            val op = instructions.get(last_if_nez_loc - 1).opcode

            if (op == Opcode.CONST_4) { // before 10.43
                val last_if_eqz = instructions.last { it.opcode == Opcode.IF_EQZ }.location.index
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
            settingsStatusLoadFingerprint.method.enableSettings("profileTabCustomisation")
        }
    }
