package crimera.patches.twitter.premium.customAppIconAndNavBtns.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object NavBarFixFingerprint: MethodFingerprint(
    returnType = "Ljava/util/List;",
    strings = listOf(
        "subscriptions_feature_1008"
    )
)