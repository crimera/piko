/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.validateLinks

import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.LINKS_DESCRIPTOR
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.utils.changeFirstString
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

val validateLinksPatch =
    bytecodePatch(
        description = "Fixes app crashing issue while opening links from a different app",
    ) {

        dependsOn(sharedExtensionPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // The following handles the signature check while sharing a link externally and opening a link.
            UriTrustingCheckFingerprint.method.apply {
                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {p1}, ${LINKS_DESCRIPTOR}->signatureCheck(Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :piko
                    return v0
                    """.trimIndent(),
                    ExternalLabel("piko", getInstruction(0)),
                )
            }

            SignatureCheckExtensionFingerprint.changeFirstString(
                AppIdentityToStringFingerprint.instructionMatches.last().fieldReference.name,
            )
        }
    }
