package app.crimera.patches.twitter.link.customsharingdomain

import app.crimera.patches.twitter.link.cleartrackingparams.AddSessionTokenFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val TARGET_STRING = "https://x.com/i/status/"

internal object NewShareSheetLinkFingerprint : Fingerprint(
    filters = listOf(
        string(TARGET_STRING)
    )
)

internal object NewShareSheetLinkFingerprint2 : Fingerprint(
    filters = listOf(
        string(TARGET_STRING),
        string("https://x.com/i/trending/"),
        string("https://x.com/i/lists/"),
    )
)

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
                    val strIndx = instructionMatches.first().index
                    val reg = getInstruction<OneRegisterInstruction>(strIndx).registerA

                    addInstructions(
                        strIndx + 1,
                        callStatement.replace(dummyReg, "v$reg"),
                    )
                }
            }

            AddSessionTokenFingerprint.method.addInstructions(
                0,
                callStatement.replace(dummyReg, "p0"),
            )

            try {
                // Should be applied only post 11.48.xx in new share sheet.
                NewShareSheetLinkFingerprint.addCustomDomainFunctionCall()
                NewShareSheetLinkFingerprint2.addCustomDomainFunctionCall()
            } catch (_: Exception) {
            }

            SettingsStatusLoadFingerprint.enableSettings("enableCustomSharingDomain")
        }
    }
