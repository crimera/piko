package crimera.patches.twitter.timeline.showSourceLabel

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
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11n
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.models.extractDescriptors

object SourceLabelFingerprint : MethodFingerprint(
    strings = listOf("show_tweet_source_disabled"),
)

@Patch(
    name = "Show post source label",
    description = "Source label will be shown only on public posts",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    requiresIntegrations = true,
)
@Suppress("unused")
object ShowSourceLabel : BytecodePatch(
    setOf(SettingsStatusLoadFingerprint, SourceLabelFingerprint),
) {
    override fun execute(context: BytecodeContext) {
        val result = SourceLabelFingerprint.result ?: throw PatchException("SourceLabelFingerprint not found")

        val method = result.mutableMethod

        val instructions = method.getInstructions()

        val igetWide = instructions.first { it.opcode == Opcode.IGET_WIDE }
        val igetWideIns = igetWide as Instruction22c
        val idRef = igetWideIns.reference.extractDescriptors()[0]
        val idReg = igetWideIns.registerB

        val igetWideIndex = igetWide.location.index
        val igetWideP1Ins = instructions[igetWideIndex + 1] as Instruction35c
        val dummyReg1 = igetWideP1Ins.registerC
        val dummyReg2 = igetWideP1Ins.registerD

        val const4 = instructions.filter { it.opcode == Opcode.CONST_4 }[1]
        val const4Ins = const4 as Instruction11n
        val const4Reg = const4Ins.registerA

        val apiMethod = "${SettingsPatch.PATCHES_DESCRIPTOR}/tweet/TweetInfoAPI;"
        method.addInstructionsWithLabels(
            const4.location.index + 1,
            """
            sget-boolean v$dummyReg1, ${SettingsPatch.PREF_DESCRIPTOR};->SHOW_SRC_LBL:Z
            if-eqz v$dummyReg1, :piko
            invoke-virtual {v$idReg}, $idRef->getId()J
            move-result-wide v$dummyReg1
            
            invoke-static {v$dummyReg1, v$dummyReg2}, $apiMethod->sendRequest(J)V
            invoke-static {v$dummyReg1, v$dummyReg2}, $apiMethod->getTweetSource(J)Ljava/lang/String;
            move-result-object v$const4Reg
            """.trimIndent(),
            ExternalLabel("piko", igetWide),
        )

        SettingsStatusLoadFingerprint.enableSettings("showSourceLabel")
    }
}
