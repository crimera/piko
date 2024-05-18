package crimera.patches.instagram.interaction.download.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object VideoModelFingerprint: MethodFingerprint(
    strings = listOf("Video id is not numerical: ")
)