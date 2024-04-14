package crimera.patches.twitter.featureFlag.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object FeatureFlagLoadFingerprint:MethodFingerprint(
    customFingerprint = { methodDef, _ ->
        methodDef.definingClass.endsWith("Lapp/revanced/integrations/twitter/patches/FeatureSwitchPatch;") &&
                methodDef.name == "load"
    }
)