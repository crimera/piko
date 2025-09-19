package app.crimera.patches.twitter.timeline.live

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal val hideLiveThreadsFingerprint =
    fingerprint {
        opcodes(
            Opcode.IGET_OBJECT,
        )

        custom { it, _ ->
            it.definingClass == "Lcom/twitter/fleets/api/json/JsonFleetsTimelineResponse;"
        }
    }

@Suppress("unused")
val hideLiveThreadsPatch =
    bytecodePatch(
        name = "Hide Live Threads",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method = hideLiveThreadsFingerprint.method
            val instructions = method.instructions

            val loc = instructions.first { it.opcode == Opcode.IGET_OBJECT }.location.index
            val reg = method.getInstruction<OneRegisterInstruction>(loc).registerA

            val HIDE_LIVE_DESCRIPTOR =
                "invoke-static {v$reg}, $PREF_DESCRIPTOR;->liveThread(Ljava/util/ArrayList;)Ljava/util/ArrayList;"

            method.addInstructions(
                loc + 1,
                """
                $HIDE_LIVE_DESCRIPTOR
                move-result-object v$reg
                """.trimIndent(),
            )
            settingsStatusLoadFingerprint.enableSettings("hideLiveThreads")
        }
    }
