package crimera.patches.twitter.timeline.forceHD

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.or
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.misc.shareMenu.nativeDownloader.exception
import crimera.patches.twitter.models.extractDescriptors

object PlayerSupportFingerprint : MethodFingerprint(
    customFingerprint = { methodDef, classDef ->
        classDef.type.contains("/av/player/support/") &&
            methodDef.parameters.size == 2
    },
    accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
)

@Patch(
    name = "Enable force HD videos",
    description = "Videos will be played in highest quality always",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    requiresIntegrations = true,
)
@Suppress("unused")
object ForceHDPatch : BytecodePatch(
    setOf(SettingsStatusLoadFingerprint, PlayerSupportFingerprint),
) {
    override fun execute(context: BytecodeContext) {
        val result = PlayerSupportFingerprint.result ?: throw PlayerSupportFingerprint.exception

        val method =
            result
                .mutableMethod

        val instructions = method.getInstructions()

        val igetObj = instructions.first { it.opcode == Opcode.IGET_OBJECT }
        val igetObjIns = igetObj as Instruction22c
        val ref = igetObjIns.reference.extractDescriptors()[1]
        val reg = igetObjIns.registerA
        val index = igetObj.location.index + 1

        method.addInstructionsWithLabels(
            index,
            """
            sget-boolean v0, ${SettingsPatch.PREF_DESCRIPTOR};->ENABLE_FORCE_HD:Z
            if-eqz v0, :piko
            sget-object v$reg, $ref->VERY_HIGH:$ref  
            """.trimIndent(),
            ExternalLabel("piko", method.getInstruction(index)),
        )

        SettingsStatusLoadFingerprint.enableSettings("enableForceHD")
    }
}
