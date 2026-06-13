/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.navbar

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.crimera.patches.twitter.utils.flagSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private object CustomiseNavBarFingerprint : Fingerprint(
    returnType = "V",
    strings =
        listOf(
            "tabCustomizationPreferences",
            "communitiesUtils",
            "subscriptionsFeatures",
        ),
)

private object NavBarFixFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    filters =
        listOf(
            string("subscriptions_feature_1008"),
        ),
)

@Suppress("unused")
val customiseNavBarPatch =
    bytecodePatch(
        name = "Customize Navigation Bar items",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            val method = CustomiseNavBarFingerprint.classDef.methods.lastOrNull { it.returnType == "Ljava/util/List;" }
                ?: throw PatchException("Failed to find method with return type Ljava/util/List; in ${CustomiseNavBarFingerprint.definingClass}")

            val instructions = method.instructions

            val returnObj = instructions.lastOrNull { it.opcode == Opcode.RETURN_OBJECT }
                ?: throw PatchException("Failed to find RETURN_OBJECT in ${CustomiseNavBarFingerprint.definingClass}")

            val returnObj_loc = returnObj.location.index
            val r0 = method.getInstruction<OneRegisterInstruction>(returnObj_loc).registerA

            val METHOD =
                """
                invoke-static {v$r0}, $CUSTOMISE_DESCRIPTOR;->navBar(Ljava/util/List;)Ljava/util/List;
                move-result-object v$r0
                """.trimIndent()

            method.addInstructions(returnObj_loc, METHOD)

            // credits aero
            val methods2 = NavBarFixFingerprint.method
            val loc2 =
                methods2
                    .instructions
                    .firstOrNull { it.opcode == Opcode.IF_NEZ }
                    ?.location?.index
                    ?: throw PatchException("Failed to find IF_NEZ in ${NavBarFixFingerprint.definingClass}")

            methods2.removeInstruction(loc2)
            methods2.removeInstruction(loc2)
            methods2.removeInstruction(loc2)

            enableSettings("navBarCustomisation")
            flagSettings("navbarFix")
        }
    }
