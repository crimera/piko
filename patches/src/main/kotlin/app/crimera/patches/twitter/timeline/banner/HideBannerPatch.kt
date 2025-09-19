package app.crimera.patches.twitter.timeline.banner

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

internal val hideBannerFingerprint =
    fingerprint {
        returns("Z")
        opcodes(
            Opcode.RETURN,
        )

        custom { it, _ ->
            it.definingClass == "Lcom/twitter/timeline/newtweetsbanner/BaseNewTweetsBannerPresenter;"
        }
    }

@Suppress("unused")
val hideBannerPatch =
    bytecodePatch(
        name = "Hide Banner",
        description = "Hide new post banner",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method = hideBannerFingerprint.method
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

            settingsStatusLoadFingerprint.enableSettings("hideBanner")
        }
    }
