package crimera.patches.instagram.interaction.download.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object FeedBottomSheetFingerprint: MethodFingerprint(
    strings = listOf("feed", "instagram_shopping_home")
)