package crimera.patches.twitter.misc.shareMenu.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object ShareMenuButtonFuncCallFingerprint : MethodFingerprint(
    returnType = "V",
    strings =
        listOf(
            "click",
            "tweet_analytics",
            "sandbox://tweetview?id=",
            "sandbox://spaces?id=",
        ),
)
