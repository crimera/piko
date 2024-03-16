package crimera.patches.twitter.premium.readermode.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object EnableReaderMode2Fingerprint: MethodFingerprint(
    returnType = "Ljava/lang/Object;",
    strings = listOf(
        "id",
        "subscriptions_feature_1005",
        "extra_tweet_id"
    )
)