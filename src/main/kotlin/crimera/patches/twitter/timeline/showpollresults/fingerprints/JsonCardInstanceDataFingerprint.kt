package crimera.patches.twitter.timeline.showpollresults.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object JsonCardInstanceDataFingerprint: MethodFingerprint(
    strings = listOf(
        "binding_values"
    ),
    customFingerprint = {methodDef, classDef ->
        methodDef.name == "parseField" && classDef.type.endsWith("JsonCardInstanceData\$\$JsonObjectMapper;")
    }
)