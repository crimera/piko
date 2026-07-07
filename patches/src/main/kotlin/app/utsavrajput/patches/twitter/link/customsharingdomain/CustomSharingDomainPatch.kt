/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.utsavrajput.patches.twitter.link.customsharingdomain

import app.utsavrajput.patches.twitter.link.cleartrackingparams.AddSessionTokenFingerprint
import app.utsavrajput.patches.twitter.link.handlemodernsharesheetlinks.handleModernShareSheetLinks
import app.utsavrajput.patches.twitter.misc.settings.settingsPatch
import app.utsavrajput.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.utsavrajput.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.utsavrajput.patches.twitter.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val customSharingDomainPatch =
    bytecodePatch(
        name = "Custom sharing domain",
        description = "Allows for using domains like fxtwitter when sharing tweets/posts.",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, handleModernShareSheetLinks)
        execute {

            val dummyReg = "#reg"
            val callStatement =
                """
                invoke-static {$dummyReg}, $PATCHES_DESCRIPTOR/links/Urls;->changeDomain(Ljava/lang/String;)Ljava/lang/String;
                move-result-object $dummyReg
                """.trimIndent()

            AddSessionTokenFingerprint.method.addInstructions(
                0,
                callStatement.replace(dummyReg, "p0"),
            )

            enableSettings("enableCustomSharingDomain")
        }
    }
