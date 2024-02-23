package crimera.patches.twitter.link.unshorten.fingerprints

import app.revanced.patcher.extensions.or
import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.AccessFlags


internal object JsonObjectMapperFingerprint : MethodFingerprint(
    // Lcom/twitter/model/json/core/JsonUrlEntity$$JsonObjectMapper;
    customFingerprint = { methodDef, _ -> methodDef.name.startsWith("_parse") && methodDef.definingClass == "Lcom/twitter/model/json/core/JsonUrlEntity\$\$JsonObjectMapper;" }
)
