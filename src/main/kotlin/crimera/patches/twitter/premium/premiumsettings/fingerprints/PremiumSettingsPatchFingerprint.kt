package crimera.patches.twitter.premium.premiumsettings.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object PremiumSettingsPatchFingerprint: MethodFingerprint(
    parameters = listOf("Lcom/twitter/navigation/subscriptions/ReferringPage"),
    strings = listOf("referringPage")
)