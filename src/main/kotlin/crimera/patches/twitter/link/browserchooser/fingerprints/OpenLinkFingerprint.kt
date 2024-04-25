package crimera.patches.twitter.link.browserchooser.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object OpenLinkFingerprint : MethodFingerprint(
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Landroid/content/Intent;", "Landroid/os/Bundle;")
)