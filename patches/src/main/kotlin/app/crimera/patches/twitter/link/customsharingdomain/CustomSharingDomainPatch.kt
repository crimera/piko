package app.crimera.patches.twitter.link.customsharingdomain

import app.crimera.patches.twitter.link.cleartrackingparams.addSessionTokenFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal const val TARGET_STRING = "https://x.com/i/status/"
internal val newShareSheetLinkFingerprint =
    fingerprint {
        strings(
            TARGET_STRING,
        )
    }

@Suppress("unused")
val customSharingDomainPatch =
    bytecodePatch(
        name = "Custom sharing domain",
        description = "Allows for using domains like fxtwitter when sharing tweets/posts.",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)
        execute {
            val methodInvoke = "$PATCHES_DESCRIPTOR/links/Urls;->changeDomain(Ljava/lang/String;)Ljava/lang/String;"
            addSessionTokenFingerprint.method.addInstructions(
                0,
                """
                invoke-static {p0}, $methodInvoke
                move-result-object p0
                """.trimIndent(),
            )
            settingsStatusLoadFingerprint.enableSettings("enableCustomSharingDomain")

            try {
                // Should be applied only post 11.48.xx in new share sheet.
                newShareSheetLinkFingerprint.method.apply {
                    val strIndx = newShareSheetLinkFingerprint.stringMatches!!.first { it.string == TARGET_STRING }.index
                    val reg = getInstruction<OneRegisterInstruction>(strIndx).registerA

                    addInstructions(
                        strIndx + 1,
                        """
                        invoke-static {v$reg}, $methodInvoke
                        move-result-object v$reg
                        """.trimIndent(),
                    )
                }
            } catch (_: Exception) {
            }
        }
    }
