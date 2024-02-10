package crimera.patches.twitter.googleads

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode


object GoogleAdsPatchFingerprint: MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "ssp_ads_google_dsp_client_context_enabled"
    )
)

@Patch(
    name = "Google Ads Patch",
    description = "Remove Google Ads",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false
)
@Suppress("unused")
object GoogleAdsPatch: BytecodePatch(
    setOf(GoogleAdsPatchFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = GoogleAdsPatchFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val method = result.mutableMethod
        val instructions = method.getInstructions()

        val bro = instructions.last { it.opcode == Opcode.INVOKE_VIRTUAL }.location.index

        method.addInstruction(bro, """
            const v0, false
            move-object v0, p5
        """.trimIndent())
    }
}