/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.premium.redirectBMNavBar

import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

// Credits @Ouxyl
private object TabLayoutFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/material/tabs/TabLayout;",
    name = "q",
)

val redirectBMTab =
    bytecodePatch(
        description = "Patch required to redirect bookmark folders to bookmark",
    ) {
        execute {
            val method = TabLayoutFingerprint.method
            val instructions = method.instructions

            val first_line = instructions.first { it.opcode == Opcode.IGET_OBJECT }

            val M = "invoke-static {p1}, ${PREF_DESCRIPTOR};->redirect(Lcom/google/android/material/tabs/TabLayout\$g;)Z"

            method.addInstructionsWithLabels(
                0,
                """
                $M
                move-result v0
                if-eqz v0, :cond_1212
                return-void
                """.trimIndent(),
                ExternalLabel("cond_1212", first_line),
            )

            // end
        }
    }
