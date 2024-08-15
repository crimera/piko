package crimera.patches.twitter.timeline.removePremiumUpsell

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal val removePremiumUpsellPatchFingerprint = fingerprint {
    strings("subscriptions_upsells_premium_home_nav")
}

@Suppress("unused")
val disablePremiumUpsellPatch = bytecodePatch(
    name = "Remove premium upsell",
    description = "Removes premium upsell in home timeline",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val result by removePremiumUpsellPatchFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        val PREF = "invoke-static {}, ${PREF_DESCRIPTOR};->removePremiumUpsell()Z"

        val methods = result.mutableMethod
        val instructions = methods.instructions

        val cond_loc = instructions.first { it.opcode == Opcode.INVOKE_VIRTUAL }.location.index

        methods.addInstruction(cond_loc + 1, PREF)

        settingsStatusMatch.enableSettings("removePremiumUpsell")
    }
}