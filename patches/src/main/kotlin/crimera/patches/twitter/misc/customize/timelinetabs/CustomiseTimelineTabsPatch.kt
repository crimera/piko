package crimera.patches.twitter.misc.customize.timelinetabs

import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

val customiseTimelineTabsFingerprint = fingerprint {
    returns("V")
    strings(
        "selectedTabStateRepo",
        "pinnedTimelinesRepo",
        "releaseCompletable"
    )
    opcodes(Opcode.CONST_16)
}

@Suppress("unused")
val customiseTimelineTabsPatch = bytecodePatch(
    name = "Customize timeline top bar",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val customiseTimelineTabsFingerprintMatch by customiseTimelineTabsFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {

        val method = customiseTimelineTabsFingerprintMatch.mutableMethod

        val instructions = method.instructions

        val c = instructions.filter { it.opcode == Opcode.CONST_16 }[1].location.index
        val v4 = method.getInstruction<OneRegisterInstruction>(c).registerA
        val v5 = method.getInstruction<OneRegisterInstruction>(c + 1).registerA

        val arr = instructions.first { it.opcode == Opcode.FILLED_NEW_ARRAY }
        val arrLoc = arr.location.index
        val r = method.getInstruction<Instruction35c>(arrLoc)
        val r3 = r.registerC
        val r11 = r.registerD

        method.addInstructionsWithLabels(
            arrLoc, """
            invoke-static {}, ${PREF_DESCRIPTOR};->timelineTab()I
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
        """.trimIndent(), ExternalLabel("escape", arr)
        )

        settingsStatusMatch.enableSettings("timelineTabCustomisation")
    }
}
