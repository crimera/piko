package app.crimera.patches.twitter.link.unshorten

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private val jsonObjectMapperFingerprint =
    fingerprint {
        returns("Ljava/lang/Object")
        custom { methodDef, _ ->
            methodDef.name.contains("parse") &&
                methodDef.definingClass == "Lcom/twitter/model/json/core/JsonUrlEntity\$\$JsonObjectMapper;"
        }
    }

@Suppress("unused")
val noShortenedUrlPatch =
    bytecodePatch(
        name = "No shortened URL",
        description = "Get rid of t.co short urls.",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val METHOD_REFERENCE =
                "$PATCHES_DESCRIPTOR/links/UnshortenUrlsPatch;->" +
                    "unshort(Lcom/twitter/model/json/core/JsonUrlEntity;)Lcom/twitter/model/json/core/JsonUrlEntity;"

            val methods = jsonObjectMapperFingerprint.method
            val instructions = methods.instructions

            val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

            methods.addInstructions(
                returnObj,
                """
                invoke-static { p1 }, $METHOD_REFERENCE
                move-result-object p1
                """.trimIndent(),
            )
            settingsStatusLoadFingerprint.enableSettings("unshortenlink")
        }
    }
