package crimera.patches.twitter.timeline.banner


import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint


internal val hideBannerFingerprint = fingerprint {
    returns("Z")
    opcodes(Opcode.RETURN)
    custom { it, _ ->
        it.definingClass == "Lcom/twitter/timeline/newtweetsbanner/BaseNewTweetsBannerPresenter;"
    }
}

@Suppress("unused")
val hideBannerPatch = bytecodePatch(
    name = "Hide Banner",
    description = "Hide new post banner",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val result by hideBannerFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {

        val method = result.mutableMethod
        val instuctions = method.instructions

        val loc = instuctions.first { it.opcode == Opcode.IF_NEZ }.location.index

        val HIDE_BANNER_DESCRIPTOR =
            "invoke-static {}, ${PREF_DESCRIPTOR};->hideBanner()Z"

        method.addInstructions(
            loc, """
            $HIDE_BANNER_DESCRIPTOR
            move-result v0
        """.trimIndent()
        )

        settingsStatusMatch.enableSettings("hideBanner")
    }
}