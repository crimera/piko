package app.crimera.patches.twitter.link.customsharingdomain

import app.crimera.patches.twitter.link.cleartrackingparams.addSessionTokenFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

val customSharingDomainFingerprint =
    fingerprint {
        accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
        strings("res.getString(R.string.t…lUsername, id.toString())")
    }

@Suppress("unused")
val customSharingDomainPatch =
    bytecodePatch(
        name = "Custom sharing domain",
        description = "Allows for using domains like fxtwitter when sharing tweets/posts.",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)
        execute {
            val getSharingLinkDescriptor =
                "invoke-static {p0}, $PREF_DESCRIPTOR;->getSharingLink(Ljava/lang/String;)Ljava/lang/String;"

            addSessionTokenFingerprint.method.addInstructions(
                0,
                """
                $getSharingLinkDescriptor
                move-result-object p0
                """.trimIndent(),
            )
            settingsStatusLoadFingerprint.enableSettings("enableCustomSharingDomain")
        }
    }
