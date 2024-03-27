package crimera.patches.twitter.premium.readermode.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object EnableReaderMode1Fingerprint: MethodFingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    strings = listOf(
        "subscriptions_feature_1005"
    )
)