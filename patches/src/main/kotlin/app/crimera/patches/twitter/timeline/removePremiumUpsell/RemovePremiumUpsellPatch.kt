package app.crimera.patches.twitter.timeline.removePremiumUpsell

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

internal val removePremiumUpsellPatchFingerprint =
    fingerprint {
        strings("subscriptions_upsells_premium_home_nav")
    }

@Suppress("unused")
val disablePremiumUpsellPatch =
    bytecodePatch(
        name = "Remove premium upsell",
        description = "Removes premium upsell in home timeline",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val PREF = "invoke-static {}, $PREF_DESCRIPTOR;->removePremiumUpsell()Z"

            val methods = removePremiumUpsellPatchFingerprint.method
            val instructions = methods.instructions

            val cond_loc = instructions.first { it.opcode == Opcode.INVOKE_VIRTUAL }.location.index

            methods.addInstruction(cond_loc + 1, PREF)

            settingsStatusLoadFingerprint.enableSettings("removePremiumUpsell")
        }
    }
