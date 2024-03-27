package crimera.patches.twitter.link.unshorten.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint


internal object JsonObjectMapperFingerprint : MethodFingerprint(
    // Lcom/twitter/model/json/core/JsonUrlEntity$$JsonObjectMapper;
    customFingerprint = { methodDef, _ -> methodDef.name.contains("parse") && methodDef.definingClass == "Lcom/twitter/model/json/core/JsonUrlEntity\$\$JsonObjectMapper;" }
)
