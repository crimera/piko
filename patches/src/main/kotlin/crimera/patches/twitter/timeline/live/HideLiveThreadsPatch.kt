package crimera.patches.twitter.timeline.live

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

@Suppress("unused")
val hideLiveThreadsPatch = bytecodePatch(
    name = "Hide Live Threads",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val result by hideLiveThreadsFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        val method = result.mutableMethod
        val instructions = method.instructions

        val loc = instructions.first { it.opcode == Opcode.IGET_OBJECT }.location.index
        val reg = method.getInstruction<OneRegisterInstruction>(loc).registerA

        val HIDE_LIVE_DESCRIPTOR =
            "invoke-static {v$reg}, ${PREF_DESCRIPTOR};->liveThread(Ljava/util/ArrayList;)Ljava/util/ArrayList;"

        method.addInstructions(
            loc + 1, """
            $HIDE_LIVE_DESCRIPTOR
            move-result-object v$reg
        """.trimIndent()
        )

        settingsStatusMatch.enableSettings("hideLiveThreads")
    }
}