package crimera.patches.instagram.interaction.download.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

internal object DialogItemClickedFingerprint: MethodFingerprint(
    strings = listOf("Required value was null.", "media_options"),
)