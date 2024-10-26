package crimera.patches.twitter.misc.shareMenu.nativeDownloader.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object NativeDownloaderPatchFingerprint : MethodFingerprint(
    returnType = "V",
    strings =
        listOf(
            "sandbox://tweetview?id=",
        ),
)
