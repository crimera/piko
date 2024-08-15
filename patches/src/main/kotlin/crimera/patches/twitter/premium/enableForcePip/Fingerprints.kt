package crimera.patches.twitter.premium.enableForcePip

import app.revanced.patcher.fingerprint

internal val enableForcePip1Fingerprint = fingerprint {
    strings(
        "impl",
        "unsupported",
        "android_immersive_media_player_native_pip_enabled"
    )
}

internal val enableForcePip2Fingerprint = fingerprint {
    returns("Ljava/lang/Object")
    strings("android_immersive_media_player_native_pip_enabled")
}

