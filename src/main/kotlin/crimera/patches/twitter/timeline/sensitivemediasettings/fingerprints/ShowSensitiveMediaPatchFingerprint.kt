package crimera.patches.twitter.timeline.sensitivemediasettings.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object SensitiveMediaSettingsPatchFingerprint : MethodFingerprint(
    returnType = "Ljava/lang/Object",
    customFingerprint = { it, _ ->
        it.definingClass == "Lcom/twitter/model/json/core/JsonSensitiveMediaWarning\$\$JsonObjectMapper;" && it.name == "parse"
    },
)
