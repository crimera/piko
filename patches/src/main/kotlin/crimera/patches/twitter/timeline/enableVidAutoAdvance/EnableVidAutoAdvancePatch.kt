package crimera.patches.twitter.timeline.enableVidAutoAdvance

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal val enableVidAutoAdvancePatchFingerprint = fingerprint {
    strings("immersive_video_auto_advance_duration_threshold")
}

@Suppress("unused")
val enableVidAutoAdvancePatch = bytecodePatch(
    name = "Control video auto scroll",
    description = "Control video auto scroll in immersive view",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val result by enableVidAutoAdvancePatchFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        var strLoc: Int = 0
        result.stringMatches?.forEach { match ->
            val str = match.string
            if (str.contains("immersive_video_auto_advance_duration_threshold")) {
                strLoc = match.index
                return@forEach
            }
        } ?: throw PatchException("$name: String not found")

        if (strLoc == 0) {
            throw PatchException("hook not found")
        }

        val method = result.mutableMethod
        val instructions = method.instructions
        val loc = strLoc + 2

        val reg = method.getInstruction<OneRegisterInstruction>(loc).registerA
        method.addInstruction(
            loc, """
            invoke-static {}, ${PREF_DESCRIPTOR};->enableVidAutoAdvance()I
        """.trimIndent()
        )

        settingsStatusMatch.enableSettings("enableVidAutoAdvance")
    }
}