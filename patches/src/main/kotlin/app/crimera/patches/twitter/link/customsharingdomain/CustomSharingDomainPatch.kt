package app.crimera.patches.twitter.link.customsharingdomain

import app.crimera.patches.twitter.link.cleartrackingparams.addSessionTokenFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.Fingerprint
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

internal val newShareSheetLinkFingerprint2 =
    fingerprint {
        strings(
            TARGET_STRING,
            "https://x.com/i/trending/",
            "https://x.com/i/lists/",
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
            val dummyReg = "#reg"
            val callStatement =
                """
                invoke-static {$dummyReg}, $methodInvoke
                move-result-object $dummyReg
                """.trimIndent()

            fun Fingerprint.addCustomDomainFunctionCall() {
                method.apply {
                    val strIndx = stringMatches!!.first { it.string == TARGET_STRING }.index
                    val reg = getInstruction<OneRegisterInstruction>(strIndx).registerA

                    addInstructions(
                        strIndx + 1,
                        callStatement.replace(dummyReg, "v$reg"),
                    )
                }
            }

            addSessionTokenFingerprint.method.addInstructions(
                0,
                callStatement.replace(dummyReg, "p0"),
            )

            try {
                // Should be applied only post 11.48.xx in new share sheet.
                newShareSheetLinkFingerprint.addCustomDomainFunctionCall()
                newShareSheetLinkFingerprint2.addCustomDomainFunctionCall()
            } catch (_: Exception) {
            }

            settingsStatusLoadFingerprint.enableSettings("enableCustomSharingDomain")
        }
    }
