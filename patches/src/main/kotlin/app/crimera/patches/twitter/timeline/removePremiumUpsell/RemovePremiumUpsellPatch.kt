/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.timeline.removePremiumUpsell

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object RemovePremiumUpsellPatchFingerprint : Fingerprint(
    filters =
        listOf(
            string("subscriptions_upsells_premium_home_nav"),
        ),
)

@Suppress("unused")
val disablePremiumUpsellPatch =
    bytecodePatch(
        name = "Remove premium upsell",
        description = "Removes premium upsell in home timeline",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val PREF = "invoke-static {}, $PREF_DESCRIPTOR;->removePremiumUpsell()Z"

            val methods = RemovePremiumUpsellPatchFingerprint.method
            val instructions = methods.instructions

            val cond_loc = instructions.first { it.opcode == Opcode.INVOKE_VIRTUAL }.location.index

            methods.addInstruction(cond_loc + 1, PREF)

            enableSettings("removePremiumUpsell")
        }
    }
