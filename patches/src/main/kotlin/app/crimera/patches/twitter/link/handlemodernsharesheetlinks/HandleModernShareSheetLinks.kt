/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.link.handlemodernsharesheetlinks

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string

private object HandleModernShareSheetLinksFingerprint : Fingerprint(
    filters =
        listOf(
            string("Sharing items: %s"),
        ),
)

val handleModernShareSheetLinksPatch =
    bytecodePatch(
        name = "Handle modern share sheet links",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            val classDef = HandleModernShareSheetLinksFingerprint.classDef
            val shareSheetField = classDef.fields.firstOrNull { it.type.contains("components/sharesheet") }
                ?: throw PatchException("Failed to find sharesheet field in ${classDef.type}")

            val method = classDef.methods.firstOrNull { it.name == "invoke" }
                ?: throw PatchException("Failed to find invoke method in ${classDef.type}")

            method.addInstructions(
                0,
                """
                invoke-static {p1}, Lapp/crimera/piko/patches/twitter/LinkPatch;->handleModernShareSheetLinks(Ljava/lang/Object;)V
                """.trimIndent(),
            )
            enableSettings("handleModernShareSheetLinks")
        }
    }
