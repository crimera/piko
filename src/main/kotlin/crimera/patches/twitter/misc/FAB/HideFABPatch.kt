package crimera.patches.twitter.misc.FAB

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.FAB.fingerprints.HideFABFingerprint

@Patch(
    name = "Hide FAB",
    compatiblePackages = [CompatiblePackage("com.twitter.android")] ,
    use = false
)
@Suppress("unused")
class HideFABPatch :BytecodePatch(
    setOf(HideFABFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val result = HideFABFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val method = result.mutableMethod
        val instructions = method.getInstructions()
        val const_obj = instructions.last { it.opcode == Opcode.CONST_4 }

        method.addInstructionsWithLabels(0,"""
            goto :cond_1212
        """.trimIndent(),
            ExternalLabel("cond_1212",const_obj)
            )

    }
}