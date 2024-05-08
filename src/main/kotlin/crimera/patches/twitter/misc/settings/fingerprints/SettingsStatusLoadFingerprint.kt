package crimera.patches.twitter.misc.settings.fingerprints

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.PatchException
import crimera.patches.twitter.misc.settings.SettingsPatch

object SettingsStatusLoadFingerprint: MethodFingerprint(
    customFingerprint = { methodDef, _ ->
        methodDef.definingClass.endsWith("Lapp/revanced/integrations/twitter/settings/SettingsStatus;") &&
                methodDef.name == "load"
    }
) {
    fun enableSettings(functionName: String) {
        result?.mutableMethod?.addInstruction(
            0,
            "${SettingsPatch.SSTS_DESCRIPTOR}->$functionName()V"
        ) ?: throw PatchException("SettingsStatusLoadFingerprint not found")
    }
}