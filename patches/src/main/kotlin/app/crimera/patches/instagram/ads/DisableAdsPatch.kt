package app.crimera.patches.instagram.ads

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

internal object DisableAdsFingerprint : Fingerprint(
    strings = listOf("SponsoredContentController.insertItem"),
)

@Suppress("unused")
val disableAdsPatch =
    bytecodePatch(
        name = "Disable ads",
        use = true,
    ) {
        compatibleWith("com.instagram.android")
        dependsOn(settingsPatch)
        execute {

            DisableAdsFingerprint.method.apply {
                addInstructions(
                    0,
                    """
                    ${Constants.PREF_CALL_DESCRIPTOR}->disableAds()Z
                    move-result v0
                    return v0
                    """.trimIndent(),
                )

                enableSettings("disableAds")
            }
        }
    }
