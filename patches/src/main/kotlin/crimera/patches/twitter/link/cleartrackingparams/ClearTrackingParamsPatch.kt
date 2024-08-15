package crimera.patches.twitter.link.cleartrackingparams

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint


internal val addSessionTokenFingerprint = fingerprint {
    strings(
        "<this>",
        "shareParam",
        "sessionToken"
    )
}

// https://github.com/FrozenAlex/revanced-patches-new
@Suppress("unused")
val clearTrackingParamsPatch = bytecodePatch(
    name = "Clear tracking params",
    description = "Removes tracking parameters when sharing links",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val result by addSessionTokenFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        result.mutableMethod.addInstruction(0, "return-object p0")

        settingsStatusMatch.enableSettings("cleartrackingparams")
    }
}