package crimera.patches.twitter.timeline.hideCommunityBadge

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.models.extractDescriptors

object CommModelFingerprint : MethodFingerprint(
    strings =
        listOf(
            "actionResults",
            "role",
        ),
)

@Patch(
    name = "Hide community badges",
    description = "",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    requiresIntegrations = true,
)
@Suppress("unused")
object HideCommunityBadge : BytecodePatch(
    setOf(SettingsStatusLoadFingerprint, CommModelFingerprint),
) {
    override fun execute(context: BytecodeContext) {
        val result = CommModelFingerprint.result ?: throw PatchException("CommModelFingerprint not found")

        val method =
            result.mutableMethod
        val instructions = method.getInstructions()

        val iputObj = instructions.last { it.opcode == Opcode.IPUT_OBJECT }
        val iputObjIns = iputObj as Instruction22c
        val ref = iputObjIns.reference.extractDescriptors()[1]
        val reg = iputObjIns.registerA
        val index = iputObj.location.index

        method.addInstructionsWithLabels(
            index,
            """
            sget-boolean v0, ${SettingsPatch.PREF_DESCRIPTOR};->HIDE_COMM_BADGE:Z
            if-eqz v0, :piko
                sget-object v$reg, $ref->NON_MEMBER:$ref  
            """.trimIndent(),
            ExternalLabel("piko", iputObj),
        )
        SettingsStatusLoadFingerprint.enableSettings("hideCommBadge")
    }
}
