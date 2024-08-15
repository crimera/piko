package crimera.patches.twitter.premium.customAppIcon

import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR


//thanks to @Ouxyl
@Suppress("unused")
internal val tabLayoutFingerprint = fingerprint {
    custom { it, _ ->
        it.definingClass == "Lcom/google/android/material/tabs/TabLayout;" && it.name == "q"
    }
}

val redirectBMTab = bytecodePatch {
    compatibleWith("com.twitter.android")

    val result by tabLayoutFingerprint()

    execute {
        val method = result.mutableMethod
        val instructions = method.instructions

        val first_line = instructions.first { it.opcode == Opcode.IGET_OBJECT }

        val M = "invoke-static {p1}, ${PREF_DESCRIPTOR};->redirect(Lcom/google/android/material/tabs/TabLayout\$g;)Z"

        method.addInstructionsWithLabels(
            0, """
            $M
            move-result v0
            if-eqz v0, :cond_1212
            return-void
        """.trimIndent(),
            ExternalLabel("cond_1212", first_line)
        )
    }
}