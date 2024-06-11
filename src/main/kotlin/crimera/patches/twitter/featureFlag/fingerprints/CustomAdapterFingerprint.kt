package crimera.patches.twitter.featureFlag.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object CustomAdapterFingerprint: MethodFingerprint(
    customFingerprint = { methodDef, _ ->
        methodDef.definingClass.endsWith("Lapp/revanced/integrations/twitter/settings/featureflags/CustomAdapter;") &&
                methodDef.name == "getCount"
    }
)