package crimera.patches.twitter.misc.customize.inlinebar

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

object CustomiseInlineBarFingerprint:MethodFingerprint(
    returnType = "Ljava/util/List;",
    strings = listOf(
        "bookmarks_in_timelines_enabled"
    )
)

@Patch(
    name = "Customize Inline action Bar items",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    requiresIntegrations = true
)
@Suppress("unused")
object CustomiseInlineBarPatch:BytecodePatch(
    setOf(CustomiseInlineBarFingerprint,SettingsStatusLoadFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val results = CustomiseInlineBarFingerprint.result
            ?:throw PatchException("CustomiseNavBarFingerprint not found")

        val method = results.mutableMethod
        val instructions = method.getInstructions()

        val returnObj_loc = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index
        val r0 = method.getInstruction<OneRegisterInstruction>(returnObj_loc).registerA

        val METHOD = """
            invoke-static {v$r0}, ${SettingsPatch.CUSTOMISE_DESCRIPTOR};->inlineBar(Ljava/util/List;)Ljava/util/List;
            move-result-object v$r0
        """.trimIndent()

        method.addInstructions(returnObj_loc,METHOD)

        SettingsStatusLoadFingerprint.enableSettings("inlineBarCustomisation")

        //end
    }
}