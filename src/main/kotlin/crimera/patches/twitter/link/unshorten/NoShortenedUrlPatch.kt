package crimera.patches.twitter.link.unshorten

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.link.unshorten.fingerprints.JsonObjectMapperFingerprint
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

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
                "unshort(Lcom/twitter/model/json/core/JsonUrlEntity;)Lcom/twitter/model/json/core/JsonUrlEntity;"

    override fun execute(context: BytecodeContext) {

        val result = JsonObjectMapperFingerprint.result
            ?: throw Exception("Fingerprint not found")

        val methods = result.mutableMethod
        val instructions = methods.getInstructions()

        val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

        methods.addInstructions(returnObj,"""
        invoke-static { p1 }, $METHOD_REFERENCE
        move-result-object p1
        """.trimIndent()
        )

        SettingsStatusLoadFingerprint.enableSettings("unshortenlink")
        //end

    }
}
