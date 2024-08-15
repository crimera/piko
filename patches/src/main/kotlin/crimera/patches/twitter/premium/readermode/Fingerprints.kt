package crimera.patches.twitter.premium.readermode

import app.revanced.patcher.fingerprint

internal val enableReaderMode1Fingerprint = fingerprint {
    returns("V")
    parameters("Ljava/lang/Object;")
    strings("subscriptions_feature_1005")
}

internal val enableReaderMode2Fingerprint = fingerprint {
    returns("Ljava/lang/Object;")
    strings(
        "id",
        "subscriptions_feature_1005",
        "extra_tweet_id"
    )
}

