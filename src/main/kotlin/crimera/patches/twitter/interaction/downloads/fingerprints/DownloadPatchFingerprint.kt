package crimera.patches.twitter.interaction.downloads.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object DownloadPatchFingerprint : MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "media_options_sheet",
        "resources.getString(R.string.post_video)",
        "resources.getString(R.string.post_photo)"
    )
)