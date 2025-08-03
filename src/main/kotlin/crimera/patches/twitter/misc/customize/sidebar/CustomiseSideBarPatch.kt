package crimera.patches.twitter.misc.customize.sidebar

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
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

object CustomiseSideBarFingerprint : MethodFingerprint(
    returnType = "Ljava/lang/Object",
    strings =
        listOf(
            "android_global_navigation_top_level_monetization_enabled",
        ),
    customFingerprint = { it, _ ->
        it.name == "invoke"
    },
)

@Patch(
    name = "Customize side bar items",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
@Suppress("unused")
object CustomiseSideBarPatch : BytecodePatch(
    setOf(CustomiseSideBarFingerprint, SettingsStatusLoadFingerprint),
) {

    override fun execute(context: BytecodeContext) {
        val result =
            CustomiseSideBarFingerprint.result
                ?: throw PatchException("CustomiseSideBarFingerprint not found")

        val method = result.mutableMethod

        val instructions = method.getInstructions()

        var filledNewArrIndex = instructions.last { it.opcode == Opcode.FILLED_NEW_ARRAY_RANGE }.location.index
        val return_obj = instructions.first { it.opcode == Opcode.RETURN_OBJECT && it.location.index > filledNewArrIndex }.location.index
        val r0 = method.getInstruction<OneRegisterInstruction>(return_obj).registerA

        val METHOD =
            """
            invoke-static {v$r0}, ${SettingsPatch.CUSTOMISE_DESCRIPTOR};->sideBar(Ljava/util/List;)Ljava/util/List;
            move-result-object v$r0
            """.trimIndent()

        method.addInstructionsWithLabels(return_obj, METHOD)

        SettingsStatusLoadFingerprint.enableSettings("sideBarCustomisation")
    }
}
