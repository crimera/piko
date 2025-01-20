package crimera.patches.twitter.misc.shareMenu.fingerprints

import app.revanced.patcher.fingerprint

val ShareMenuButtonFuncCallFingerprint =
    fingerprint {
        strings(
            "click",
            "tweet_analytics",
            "sandbox://tweetview?id=",
            "sandbox://spaces?id=",
        )
        returns("V")
    }
