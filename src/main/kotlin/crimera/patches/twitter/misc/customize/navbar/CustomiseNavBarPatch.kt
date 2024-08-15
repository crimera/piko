package crimera.patches.twitter.misc.customize.navbar

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.featureFlag.fingerprints.FeatureFlagLoadFingerprint
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object CustomiseNavBarFingerprint:MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "tabCustomizationPreferences",
        "communitiesUtils",
        "subscriptionsFeatures",
    )
)

object NavBarFixFingerprint: MethodFingerprint(
    returnType = "Ljava/util/List;",
    strings = listOf(
        "subscriptions_feature_1008"
    )
)

@Patch(
    name = "Customize Navigation Bar items",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true,
    requiresIntegrations = true
)
@Suppress("unused")
object CustomiseNavBarPatch:BytecodePatch(
    setOf(CustomiseNavBarFingerprint,NavBarFixFingerprint,SettingsStatusLoadFingerprint,FeatureFlagLoadFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val results = CustomiseNavBarFingerprint.result
            ?:throw PatchException("CustomiseNavBarFingerprint not found")

        val method = results.mutableClass.methods.last { it.returnType == "Ljava/util/List;" }
        val instructions = method.getInstructions()

        val returnObj_loc = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index
        val r0 = method.getInstruction<OneRegisterInstruction>(returnObj_loc).registerA

        val METHOD = """
            invoke-static {v$r0}, ${SettingsPatch.CUSTOMISE_DESCRIPTOR};->navBar(Ljava/util/List;)Ljava/util/List;
            move-result-object v$r0
        """.trimIndent()

        method.addInstructions(returnObj_loc,METHOD)


        //credits aero
        val result2 = NavBarFixFingerprint.result
            ?:throw PatchException("NavBarFixFingerprint not found")

        val methods2 = result2.mutableMethod
        val loc2 = methods2.getInstructions().first { it.opcode == Opcode.IF_NEZ }.location.index
        methods2.removeInstruction(loc2)
        methods2.removeInstruction(loc2)
        methods2.removeInstruction(loc2)

        SettingsStatusLoadFingerprint.enableSettings("navBarCustomisation")

        FeatureFlagLoadFingerprint.enableSettings("navbarFix")
        //end
    }
}