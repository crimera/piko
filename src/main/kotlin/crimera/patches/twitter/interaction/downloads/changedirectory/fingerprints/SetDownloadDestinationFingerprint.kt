package crimera.patches.twitter.interaction.downloads.changedirectory.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object SetDownloadDestinationFingerprint: MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "parse(downloadData.url)"
    )
)
