package crimera.patches.twitter.settings.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object SettingsFingerprint : MethodFingerprint(
    returnType = "Z",
    strings = listOf(
        "pref_proxy"
    ),
    parameters = listOf(
        "Landroidx/preference/Preference;",
    )
)