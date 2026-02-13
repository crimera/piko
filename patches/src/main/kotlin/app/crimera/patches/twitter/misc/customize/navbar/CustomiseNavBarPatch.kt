package app.crimera.patches.twitter.misc.customize.navbar

import app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints.FeatureFlagLoadFingerprint
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.crimera.utils.flagSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private object CustomiseNavBarFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf(
        "tabCustomizationPreferences",
        "communitiesUtils",
        "subscriptionsFeatures",
    )
)

private object NavBarFixFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    filters = listOf(
        string("subscriptions_feature_1008")
    )
)

@Suppress("unused")
val customiseNavBarPatch =
    bytecodePatch(
        name = "Customize Navigation Bar items",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val method = CustomiseNavBarFingerprint.classDef.methods.last { it.returnType == "Ljava/util/List;" }
            val instructions = method.instructions

            val returnObj_loc = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index
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
                    .first { it.opcode == Opcode.IF_NEZ }
                    .location.index
            methods2.removeInstruction(loc2)
            methods2.removeInstruction(loc2)
            methods2.removeInstruction(loc2)

            SettingsStatusLoadFingerprint.enableSettings("navBarCustomisation")
            FeatureFlagLoadFingerprint.flagSettings("navbarFix")
        }
    }
