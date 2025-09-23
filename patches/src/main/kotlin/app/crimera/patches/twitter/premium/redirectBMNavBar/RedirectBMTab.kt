package app.crimera.patches.twitter.premium.redirectBMNavBar

import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

// Credits @Ouxyl
private val tabLayoutFingerprint =
    fingerprint {

        custom { it, _ ->
            it.definingClass == "Lcom/google/android/material/tabs/TabLayout;" && it.name == "q"
        }
    }

val redirectBMTab =
    bytecodePatch(
        description = "Patch required to redirect bookmark folders to bookmark",
    ) {
        execute {
            val method = tabLayoutFingerprint.method
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
