/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.interaction.downloads.copyMediaLink

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal object DownloadCallFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/downloader/",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    strings =
        listOf(
            "getString(...)",
            "isUseSnackbar",
        ),
)

@Suppress("unused")
val copyMediaLink =
    bytecodePatch(
        name = "Add ability to copy media link",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)

        dependsOn(settingsPatch)

        execute {
            DownloadCallFingerprint.method.apply {
                val firstIfEqzIndex = indexOfFirstInstruction(Opcode.IF_EQZ)

                addInstructions(
                    firstIfEqzIndex + 1,
                    """
                    invoke-static{p0,p1}, $PATCHES_DESCRIPTOR/DownloadPatch;->mediaHandle(Ljava/lang/Object;Ljava/lang/Object;)V
                    return-void
                    """.trimIndent(),
                )

                enableSettings("mediaLinkHandle")
            }
            // end func
        }
    }
