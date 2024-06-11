package crimera.patches.twitter.premium.customAppIcon

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch.PREF_DESCRIPTOR


//thanks to @Ouxyl
object TabLayoutFingerprint:MethodFingerprint(
    customFingerprint = {it,_->
        it.definingClass == "Lcom/google/android/material/tabs/TabLayout;" && it.name == "q"
    }
)

object RedirectBMTab:BytecodePatch(
    setOf(TabLayoutFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = TabLayoutFingerprint.result
            ?:throw PatchException("TabLayoutFingerprint not found")

        val method = result.mutableMethod
        val instructions = method.getInstructions()

        val first_line = instructions.first { it.opcode == Opcode.IGET_OBJECT }

        val M = "invoke-static {p1}, ${PREF_DESCRIPTOR};->redirect(Lcom/google/android/material/tabs/TabLayout\$g;)Z"

        method.addInstructionsWithLabels(0,"""
            $M
            move-result v0
            if-eqz v0, :cond_1212
            return-void
        """.trimIndent(),
            ExternalLabel("cond_1212", first_line)
        )

        //end
    }
}