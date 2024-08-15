package crimera.patches.twitter.misc.sensitivemediasettings

import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

// Credits to @Cradlesofashes
@Suppress("unused")
val sensitiveMediaPatch = bytecodePatch(
    name = "Show sensitive media",
    description = "Shows sensitive media",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val sensitiveMediaSettingsMatch by sensitiveMediaSettingsPatchFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {

        val method = sensitiveMediaSettingsMatch.mutableMethod
        val instructions = method.instructions

        instructions.filter { it.opcode == Opcode.IPUT_BOOLEAN }.forEach {
            method.removeInstruction(it.location.index)
        }

        settingsStatusMatch.enableSettings("showSensitiveMedia")
    }
}