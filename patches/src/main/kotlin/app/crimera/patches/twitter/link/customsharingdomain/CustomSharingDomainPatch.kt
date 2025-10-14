package app.crimera.patches.twitter.link.customsharingdomain

import app.crimera.patches.twitter.link.cleartrackingparams.addSessionTokenFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val customSharingDomainPatch =
    bytecodePatch(
        name = "Custom sharing domain",
        description = "Allows for using domains like fxtwitter when sharing tweets/posts.",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)
        execute {
            addSessionTokenFingerprint.method.addInstructions(
                0,
                """
                invoke-static {p0},  $PATCHES_DESCRIPTOR/links/Urls;->changeDomain(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p0
                """.trimIndent(),
            )
            settingsStatusLoadFingerprint.enableSettings("enableCustomSharingDomain")
        }
    }
