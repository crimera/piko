package crimera.patches.twitter.misc.settings.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object UrlInterpreterActivity:MethodFingerprint(
    customFingerprint = { method, _ ->
        method.definingClass == "Lcom/twitter/deeplink/implementation/UrlInterpreterActivity;" &&
                method.name == "onCreate"
    }
)