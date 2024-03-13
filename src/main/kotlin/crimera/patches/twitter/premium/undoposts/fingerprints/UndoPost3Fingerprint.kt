package crimera.patches.twitter.premium.undoposts.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object UndoPost3Fingerprint :MethodFingerprint(
    returnType = "Ljava/lang/Object;",
    strings = listOf(
        "subscriptions_feature_1003"
    )
)