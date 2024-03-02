package crimera.patches.twitter.interaction.downloads.unlockdownloads.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object FIleDownloaderFingerprint: MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "fileDownloader",
        "dialogOpener"
    )
)