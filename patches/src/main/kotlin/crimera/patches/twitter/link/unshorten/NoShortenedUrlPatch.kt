package crimera.patches.twitter.link.unshorten

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.PATCHES_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal val jsonObjectMapperFingerprint = fingerprint {
    // Lcom/twitter/model/json/core/JsonUrlEntity$$JsonObjectMapper;
    returns("Ljava/lang/Object")
    custom { methodDef, _ ->
        methodDef.name.contains("parse") &&
                methodDef.definingClass == "Lcom/twitter/model/json/core/JsonUrlEntity\$\$JsonObjectMapper;"
    }
}

@Suppress("unused")
val noShortenedUrlPatch = bytecodePatch(
    name = "No shortened URL",
    description = "Get rid of t.co short urls.",
) {
    compatibleWith("com.twitter.android")

    val METHOD_REFERENCE =
        "${PATCHES_DESCRIPTOR}/links/UnshortenUrlsPatch;->" +
                "unshort(Lcom/twitter/model/json/core/JsonUrlEntity;)Lcom/twitter/model/json/core/JsonUrlEntity;"

    val result by jsonObjectMapperFingerprint()

    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        val methods = result.mutableMethod
        val instructions = methods.instructions

        val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

        methods.addInstructions(
            returnObj, """
        invoke-static { p1 }, $METHOD_REFERENCE
        move-result-object p1
        """.trimIndent()
        )

        settingsStatusMatch.enableSettings("unshortenlink")
    }
}
