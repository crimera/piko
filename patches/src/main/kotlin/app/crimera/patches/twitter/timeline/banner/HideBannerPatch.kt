package app.crimera.patches.twitter.timeline.banner

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private object HideBannerFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/timeline/newtweetsbanner/BaseNewTweetsBannerPresenter;",
    returnType = "Z",
    filters = listOf(
        opcode(Opcode.RETURN)
    )
)

@Suppress("unused")
val hideBannerPatch =
    bytecodePatch(
        name = "Hide Banner",
        description = "Hide new post banner",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method = HideBannerFingerprint.method
            val instuctions = method.instructions

            val loc = instuctions.first { it.opcode == Opcode.IF_NEZ }.location.index

            val HIDE_BANNER_DESCRIPTOR =
                "invoke-static {}, $PREF_DESCRIPTOR;->hideBanner()Z"

            method.addInstructions(
                loc,
                """
                $HIDE_BANNER_DESCRIPTOR
                move-result v0
                """.trimIndent(),
            )

            SettingsStatusLoadFingerprint.enableSettings("hideBanner")
        }
    }
