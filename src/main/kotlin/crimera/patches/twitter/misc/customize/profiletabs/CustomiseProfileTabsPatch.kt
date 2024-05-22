package crimera.patches.twitter.misc.customize.profiletabs

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object CustomiseProfileTabsFingerprint:MethodFingerprint(
    returnType = "Ljava/util/ArrayList;",
    strings = listOf(
        "fragment_page_number",
        "arg_is_unlimited_timeline",
        "statuses_count",
        "tweets",
        "blue_business_affiliates_list_consumption_ui_enabled",
    )
)

@Patch(
    name = "Customize profile tabs",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false,
    requiresIntegrations = true
)
@Suppress("unused")
object CustomiseProfileTabsPatch:BytecodePatch(
    setOf(CustomiseProfileTabsFingerprint,SettingsStatusLoadFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val results = CustomiseProfileTabsFingerprint.result
            ?:throw PatchException("CustomiseProfileTabsFingerprint not found")

        val method = results.mutableMethod
        val instructions = method.getInstructions()

        val returnObj_loc = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index
        val r0 = method.getInstruction<OneRegisterInstruction>(returnObj_loc).registerA

        val METHOD = """
            invoke-static {v$r0}, ${SettingsPatch.CUSTOMISE_DESCRIPTOR};->profiletabs(Ljava/util/ArrayList;)Ljava/util/ArrayList;
            move-result-object v$r0
        """.trimIndent()

        method.addInstructions(returnObj_loc,METHOD)

        val last_if_eqz = instructions.last { it.opcode == Opcode.IF_EQZ }.location.index
        val r1 = method.getInstruction<OneRegisterInstruction>(last_if_eqz).registerA

        val last_if_nez_loc = instructions.last { it.opcode == Opcode.IF_NEZ }.location.index
        val r2 = method.getInstruction<OneRegisterInstruction>(last_if_nez_loc).registerA

        //it works don't ask me how
        method.removeInstruction(last_if_eqz)
        method.removeInstruction(last_if_eqz)
        method.removeInstruction(last_if_eqz)

        method.addInstructionsWithLabels(last_if_eqz,
            """
                if-eqz v$r1, :check2
                const/4 v$r2, 0x1
                :check2
                if-nez v$r2, :check1
            """.trimIndent(), ExternalLabel("check1",instructions.last { it.opcode == Opcode.INVOKE_STATIC })
        )

        SettingsStatusLoadFingerprint.enableSettings("profileTabCustomisation")
        //end
    }
}