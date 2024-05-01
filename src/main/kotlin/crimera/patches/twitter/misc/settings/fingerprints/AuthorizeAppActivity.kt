package crimera.patches.twitter.misc.settings.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object AuthorizeAppActivity: MethodFingerprint(
    customFingerprint = { method, _ ->
        method.definingClass == "Lcom/twitter/android/AuthorizeAppActivity;" &&
                method.name == "onCreate"
    }
)