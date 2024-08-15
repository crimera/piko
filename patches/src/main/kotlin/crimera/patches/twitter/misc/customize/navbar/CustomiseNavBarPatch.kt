package crimera.patches.twitter.misc.customize.navbar

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.featureFlag.enableFeatureFlag
import crimera.patches.twitter.featureFlag.featureFlagLoadFingerprint
import crimera.patches.twitter.misc.settings.CUSTOMISE_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal val customiseNavBarFingerprint = fingerprint {
    returns("V")
    strings(
        "tabCustomizationPreferences",
        "communitiesUtils",
        "subscriptionsFeatures",
    )
}

internal val navBarFixFingerprint = fingerprint {
    returns("Ljava/util/List;")
    strings("subscriptions_feature_1008")
}

// TODO: make a separate extensions package
@Suppress("unused")
val customiseNavBarPatch = bytecodePatch(
    name = "Customize Navigation Bar items",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val results by customiseNavBarFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()
    val featureFlagsLoadMatch by featureFlagLoadFingerprint()

    //credits aero
    val result2 by navBarFixFingerprint()

    execute {

        val method = results.mutableClass.methods.last { it.returnType == "Ljava/util/List;" }
        val instructions = method.instructions

        val returnObj_loc = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index
        val r0 = method.getInstruction<OneRegisterInstruction>(returnObj_loc).registerA

        val METHOD = """
            invoke-static {v$r0}, ${CUSTOMISE_DESCRIPTOR};->navBar(Ljava/util/List;)Ljava/util/List;
            move-result-object v$r0
        """.trimIndent()

        method.addInstructions(returnObj_loc, METHOD)

        val methods2 = result2.mutableMethod
        val loc2 = methods2.instructions.first { it.opcode == Opcode.IF_NEZ }.location.index
        methods2.removeInstruction(loc2)
        methods2.removeInstruction(loc2)
        methods2.removeInstruction(loc2)

        featureFlagsLoadMatch.enableFeatureFlag("navbarFix")
        settingsStatusMatch.enableSettings("navBarCustomisation")
    }
}