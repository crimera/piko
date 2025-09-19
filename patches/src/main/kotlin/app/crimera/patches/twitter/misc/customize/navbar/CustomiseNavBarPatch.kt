package app.crimera.patches.twitter.misc.customize.navbar

import app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints.featureFlagLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.crimera.utils.flagSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal val customiseNavBarFingerprint =
    fingerprint {
        returns("V")
        strings(
            "tabCustomizationPreferences",
            "communitiesUtils",
            "subscriptionsFeatures",
        )
    }

internal val navBarFixFingerprint =
    fingerprint {
        returns("Ljava/util/List;")
        strings("subscriptions_feature_1008")
    }

@Suppress("unused")
val customiseNavBarPatch =
    bytecodePatch(
        name = "Customize Navigation Bar items",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val method = customiseNavBarFingerprint.classDef.methods.last { it.returnType == "Ljava/util/List;" }
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
            val methods2 = navBarFixFingerprint.method
            val loc2 =
                methods2
                    .instructions
                    .first { it.opcode == Opcode.IF_NEZ }
                    .location.index
            methods2.removeInstruction(loc2)
            methods2.removeInstruction(loc2)
            methods2.removeInstruction(loc2)

            settingsStatusLoadFingerprint.enableSettings("navBarCustomisation")
            featureFlagLoadFingerprint.flagSettings("navbarFix")
        }
    }
