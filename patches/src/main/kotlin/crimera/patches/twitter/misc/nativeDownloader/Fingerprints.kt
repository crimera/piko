package crimera.patches.twitter.misc.nativeDownloader

import app.revanced.patcher.fingerprint

internal val nativeDownloaderPatchFingerprint = fingerprint {
    returns("V")
    strings("sandbox://tweetview?id=")
}

internal val nativeDownloaderAlwaysIconFingerprint = fingerprint {
    strings("View in Tweet Sandbox")
}
