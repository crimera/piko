package crimera.patches.twitter.premium.undoposts.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object UndoPost3Fingerprint :MethodFingerprint(
    returnType = "Landroid/content/Intent;",
    strings = listOf(
        "subscriptions_feature_1003"
    )
)