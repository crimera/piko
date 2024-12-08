package crimera.patches.twitter.misc.customize.searchtabs

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object CustomiseSearchTabsPatchFingerprint : MethodFingerprint(
    returnType = "Ljava/util/List;",
    strings =
        listOf(
            "search_features_media_tab_enabled",
            "search_features_lists_search_enabled",
        ),
)

@Patch(
    name = "Customize search tab items",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    requiresIntegrations = true,
)
@Suppress("unused")
object CustomiseSearchTabsPatch : BytecodePatch(
    setOf(CustomiseSearchTabsPatchFingerprint, SettingsStatusLoadFingerprint),
) {
    override fun execute(context: BytecodeContext) {
        val results =
            CustomiseSearchTabsPatchFingerprint.result
                ?: throw PatchException("CustomiseSearchTabsPatchFingerprint not found")

        val method = results.mutableMethod
        val instructions = method.getInstructions()

        val returnObj_loc = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index
        val r0 = method.getInstruction<OneRegisterInstruction>(returnObj_loc).registerA

        method.addInstructions(
            returnObj_loc,
            """
            invoke-static {v$r0}, ${SettingsPatch.CUSTOMISE_DESCRIPTOR};->searchTabs(Ljava/util/List;)Ljava/util/List;
            move-result-object v$r0
            """.trimIndent(),
        )

        SettingsStatusLoadFingerprint.enableSettings("searchTabCustomisation")

        // end
    }
}
