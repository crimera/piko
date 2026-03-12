/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.interaction.downloads.copyMediaLink

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal object DownloadCallFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/downloader/",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        opcode(Opcode.GOTO),
    ),
    strings = listOf(
        "getString(...)",
        "isUseSnackbar",
    )
)

@Suppress("unused")
val copyMediaLink =
    bytecodePatch(
        name = "Add ability to copy media link",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method = DownloadCallFingerprint.method

            val gotoLoc = DownloadCallFingerprint.instructionMatches.first().index

            val METHOD =
                """
                invoke-static{p0,p1}, $PATCHES_DESCRIPTOR/DownloadPatch;->mediaHandle(Ljava/lang/Object;Ljava/lang/Object;)V
                """.trimIndent()

            method.removeInstruction(gotoLoc - 1)
            method.addInstruction(gotoLoc - 1, METHOD)

            SettingsStatusLoadFingerprint.enableSettings("mediaLinkHandle")

            // end func
        }
    }
