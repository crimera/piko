package app.crimera.patches.twitter.timeline.enableVidAutoAdvance

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private val enableVidAutoAdvancePatchFingerprint =
    fingerprint {
        strings("immersive_video_auto_advance_duration_threshold")
    }

@Suppress("unused")
val enableVidAutoAdvancePatch =
    bytecodePatch(
        name = "Control video auto scroll",
        description = "Control video auto scroll in immersive view",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            var strLoc: Int = 0
            enableVidAutoAdvancePatchFingerprint.stringMatches?.forEach { match ->
                val str = match.string
                if (str.contains("immersive_video_auto_advance_duration_threshold")) {
                    strLoc = match.index
                    return@forEach
                }
            }
            if (strLoc == 0) {
                throw PatchException("hook not found")
            }

            val method = enableVidAutoAdvancePatchFingerprint.method
            val instructions = method.instructions
            val loc = instructions.first { it.opcode == Opcode.MOVE_RESULT && it.location.index > strLoc }.location.index
            val reg = method.getInstruction<OneRegisterInstruction>(loc).registerA
            method.addInstruction(
                loc,
                """
                invoke-static {}, $PREF_DESCRIPTOR;->enableVidAutoAdvance()I
                """.trimIndent(),
            )

            settingsStatusLoadFingerprint.enableSettings("enableVidAutoAdvance")
        }
    }
