package crimera.patches.instagram.interaction.download.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object FeedItemIconsFingerprint: MethodFingerprint(
    strings = listOf("HIDE_AD", "DOWNLOAD")
)