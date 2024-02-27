package crimera.patches.twitter.settings.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object SettingsInitFingerprint : MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "pref_proxy"
    ),
    customFingerprint = { it, _ ->
        it.name == "<clinit>"
    }
)