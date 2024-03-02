package crimera.patches.twitter.misc.settings.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object SettingsStatusLoadFingerprint: MethodFingerprint(
    customFingerprint = { methodDef, _ ->
        methodDef.definingClass.endsWith("Lapp/revanced/integrations/twitter/settings/SettingsStatus;") &&
                methodDef.name == "load"
    }
)