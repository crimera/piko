package app.crimera.patches.twitter.link.cleartrackingparams

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.fingerprint
import app.morphe.patcher.patch.bytecodePatch

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
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            addSessionTokenFingerprint.method.addInstruction(0, "return-object p0")

            settingsStatusLoadFingerprint.enableSettings("cleartrackingparams")
        }
    }
