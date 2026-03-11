/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.link.unshorten

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private object JsonObjectMapperFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/model/json/core/JsonUrlEntity\$\$JsonObjectMapper;",
    name = "parse",
    returnType = "Ljava/lang/Object",
)

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
                "$PATCHES_DESCRIPTOR/links/Urls;->" +
                    "unshort(Lcom/twitter/model/json/core/JsonUrlEntity;)Lcom/twitter/model/json/core/JsonUrlEntity;"

            val methods = JsonObjectMapperFingerprint.method
            val instructions = methods.instructions

            val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

            methods.addInstructions(
                returnObj,
                """
                invoke-static { p1 }, $METHOD_REFERENCE
                move-result-object p1
                """.trimIndent(),
            )
            SettingsStatusLoadFingerprint.enableSettings("unshortenlink")
        }
    }
