package crimera.patches.twitter.featureFlag.fingerprints

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.PatchException
import crimera.patches.twitter.misc.settings.SettingsPatch

object FeatureFlagLoadFingerprint:MethodFingerprint(
    customFingerprint = { methodDef, _ ->
        methodDef.definingClass.endsWith("Lapp/revanced/integrations/twitter/patches/FeatureSwitchPatch;") &&
                methodDef.name == "load"
    }
){
    fun enableSettings(functionName: String) {
        result?.mutableMethod?.addInstruction(
            0,
            "${SettingsPatch.FSTS_DESCRIPTOR}->$functionName()V"
        ) ?: throw PatchException("FeatureFlagLoadFingerprint not found")
    }
}