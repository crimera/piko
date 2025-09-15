package app.crimera.patches.twitter.link.cleartrackingparams

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch

// https://github.com/FrozenAlex/revanced-patches-new
internal val addSessionTokenFingerprint =
    fingerprint {
        strings(
            "<this>",
            "shareParam",
            "sessionToken",
        )
    }

@Suppress("unused")
val clearTrackingParamsPatch =
    bytecodePatch(
        name = "Clear tracking params",
        description = "Removes tracking parameters when sharing links",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            addSessionTokenFingerprint.method.addInstruction(0, "return-object p0")

            settingsStatusLoadFingerprint.method.enableSettings("cleartrackingparams")
        }
    }
