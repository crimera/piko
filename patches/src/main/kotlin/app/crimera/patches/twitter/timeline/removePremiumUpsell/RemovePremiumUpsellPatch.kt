package app.crimera.patches.twitter.timeline.removePremiumUpsell

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object RemovePremiumUpsellPatchFingerprint : Fingerprint(
    filters = listOf(
        string("subscriptions_upsells_premium_home_nav")
    )
)

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

            val methods = RemovePremiumUpsellPatchFingerprint.method
            val instructions = methods.instructions

            val cond_loc = instructions.first { it.opcode == Opcode.INVOKE_VIRTUAL }.location.index

            methods.addInstruction(cond_loc + 1, PREF)

            SettingsStatusLoadFingerprint.enableSettings("removePremiumUpsell")
        }
    }
