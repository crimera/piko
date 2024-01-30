package your.org.patches.twitter.download.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object FIleDownloaderFingerprint: MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "fileDownloader",
        "dialogOpener"
    )
)