package crimera.patches.instagram.interaction.download.fingerprints.hook

import app.revanced.patcher.fingerprint.MethodFingerprint

object FeedItemIconsFingerprint: MethodFingerprint(
    strings = listOf("HIDE_AD", "DOWNLOAD")
)