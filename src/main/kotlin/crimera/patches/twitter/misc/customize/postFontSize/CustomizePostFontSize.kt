package crimera.patches.twitter.misc.customize.postFontSize

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.or
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object CustomizePostFontSizeFingerprint : MethodFingerprint(
    accessFlags = AccessFlags.PUBLIC or AccessFlags.CONSTRUCTOR,
    customFingerprint = { methodDef, _ ->
        methodDef.definingClass.endsWith("TextContentView;")
    },
)

@Patch(
    name = "Customise post font size",
    description = "",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true,
)
object CustomizePostFontSize : BytecodePatch(
    setOf(CustomizePostFontSizeFingerprint, SettingsStatusLoadFingerprint),
) {
    override fun execute(context: BytecodeContext) {
        val result1 =
            CustomizePostFontSizeFingerprint.result
                ?: throw PatchException("CustomizePostFontSizeFingerprint not found")

        val method1 = result1.mutableMethod

        val index1 =
            method1
                .getInstructions()
                .last { it.opcode == Opcode.MOVE_RESULT }
                .location.index
        method1.addInstruction(index1 + 1, "sget p1, ${SettingsPatch.PREF_DESCRIPTOR};->POST_FONT_SIZE:F")

        SettingsStatusLoadFingerprint.enableSettings("customPostFontSize")
    }
}
