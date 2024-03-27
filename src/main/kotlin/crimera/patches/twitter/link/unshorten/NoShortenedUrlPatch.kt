package crimera.patches.twitter.link.unshorten

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.link.unshorten.fingerprints.JsonObjectMapperFingerprint

@Patch(
    name = "No shortened URL",
    description = "Get rid of t.co short urls.",
    compatiblePackages = [CompatiblePackage("com.twitter.android")]
)
@Suppress("unused")
object NoShortenedUrlPatch : BytecodePatch(
    setOf(JsonObjectMapperFingerprint)
) {
    private const val METHOD_REFERENCE =
        "Lapp/revanced/integrations/twitter/patches/links/UnshortenUrlsPatch;->" +
                "unshort(Ljava/lang/Object;)V"

    override fun execute(context: BytecodeContext) {

        val result = JsonObjectMapperFingerprint.result
            ?: throw Exception("Fingerprint not found")

        val method = result.mutableMethod
        val instructions = method.getInstructions()

        // somehow targetIndex2 is above :cond_2, inject again before branching
        var targetIndex = -1
        val targetIndex2 = instructions.size - 1
        for (i in 0..targetIndex2) {
            if (instructions[i].opcode == Opcode.IF_EQ) {
                targetIndex = i;
            }
        }

        val inject = """
                invoke-static { v0 }, $METHOD_REFERENCE
            """.trimIndent()

        result.mutableMethod.addInstructions(targetIndex, inject)
        result.mutableMethod.addInstructions(targetIndex2, inject)

    }
}
