package crimera.patches.twitter.premium.customAppIconAndNavBtns.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object CustomAppIconFingerprint:MethodFingerprint(
    strings = listOf(
        "current_app_icon_id"
    )
)