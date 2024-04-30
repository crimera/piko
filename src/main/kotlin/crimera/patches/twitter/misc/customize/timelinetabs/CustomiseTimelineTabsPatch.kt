package crimera.patches.twitter.misc.customize.timelinetabs

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object CustomiseTimelineTabsFingerprint : MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "selectedTabStateRepo",
        "pinnedTimelinesRepo",
        "releaseCompletable",
    ),
    opcodes = listOf(
        Opcode.CONST_16,
    ),
)
@Patch(
    name = "Customize timeline top bar",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true
)
@Suppress("unused")
object CustomiseTimelineTabsPatch : BytecodePatch(
    setOf(CustomiseTimelineTabsFingerprint,SettingsStatusLoadFingerprint)
){

    override fun execute(context: BytecodeContext) {
        val result = CustomiseTimelineTabsFingerprint.result
            ?: throw PatchException("CustomiseTimelineTabsFingerprint not found")

        val method = result.mutableMethod

        val instructions = method.getInstructions()

        val c = instructions.filter {  it.opcode == Opcode.CONST_16  }[1].location.index
        val v4 = method.getInstruction<OneRegisterInstruction>(c).registerA
        val v5 = method.getInstruction<OneRegisterInstruction>(c+1).registerA

        val arr = instructions.first { it.opcode == Opcode.FILLED_NEW_ARRAY }
        val arrLoc = arr.location.index
        val r = method.getInstruction<Instruction35c>(arrLoc)
        val r3 = r.registerC
        val r11 = r.registerD

        method.addInstructionsWithLabels(arrLoc,"""
            invoke-static {}, ${SettingsPatch.PREF_DESCRIPTOR};->timelineTab()I
            move-result v$v4
           
            const/16 v$v5, 0x1
            if-ne v$v4,v$v5, :no
            move-object v$r3,v$r11
            goto :escape
            :no
            const/16 v$v5, 0x2
            if-ne v$v4,v$v5, :escape
            move-object v$r11,v$r3
            goto :escape
        """.trimIndent(), ExternalLabel("escape",arr)
        )

        SettingsStatusLoadFingerprint.result?.mutableMethod?.addInstruction(
            0,
            "${SettingsPatch.SSTS_DESCRIPTOR}->timelineTabCustomisation()V"
        ) ?: throw PatchException("SettingsStatusLoadFingerprint not found")
    }
}
