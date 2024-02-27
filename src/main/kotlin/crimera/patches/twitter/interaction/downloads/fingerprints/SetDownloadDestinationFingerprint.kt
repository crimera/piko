package crimera.patches.twitter.interaction.downloads.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object SetDownloadDestinationFingerprint: MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "parse(downloadData.url)"
    )
)