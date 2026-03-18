/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.link.customsharingdomain

import app.crimera.patches.twitter.link.cleartrackingparams.AddSessionTokenFingerprint
import app.crimera.patches.twitter.link.handlemodernsharesheetlinks.handleModernShareSheetLinks
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val customSharingDomainPatch =
    bytecodePatch(
        name = "Custom sharing domain",
        description = "Allows for using domains like fxtwitter when sharing tweets/posts.",
    ) {
        compatibleWith("com.twitter.android")
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

            SettingsStatusLoadFingerprint.enableSettings("enableCustomSharingDomain")
        }
    }
