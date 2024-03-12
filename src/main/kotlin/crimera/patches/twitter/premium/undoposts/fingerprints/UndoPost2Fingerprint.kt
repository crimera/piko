package crimera.patches.twitter.premium.undoposts.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object UndoPost2Fingerprint :MethodFingerprint(
    returnType = "Z",
    strings = listOf(
        "userPreferences",
        "draftTweet",
        "subscriptions_feature_1003",
        "allow_undo_replies",
        "allow_undo_tweet"
    )
)