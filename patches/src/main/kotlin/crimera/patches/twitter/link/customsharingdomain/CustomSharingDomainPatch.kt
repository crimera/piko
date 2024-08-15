package crimera.patches.twitter.link.customsharingdomain

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
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

@Suppress("unused")
val customSharingDomainPatch = bytecodePatch(
    name = "Custom sharing domain",
    description = "Allows for using domains like fxtwitter when sharing tweets/posts.",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val addSessionTokenResult by addSessionTokenFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        val getSharingLinkDescriptor =
            "invoke-static {p0}, ${PREF_DESCRIPTOR};->getSharingLink(Ljava/lang/String;)Ljava/lang/String;"


        addSessionTokenResult.mutableMethod.addInstructions(
            0,
            """
                $getSharingLinkDescriptor
                move-result-object p0
            """.trimIndent()
        )

        settingsStatusMatch.enableSettings("enableCustomSharingDomain")
    }
}