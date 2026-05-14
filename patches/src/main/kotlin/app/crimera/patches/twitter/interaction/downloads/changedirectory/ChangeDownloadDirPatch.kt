/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.interaction.downloads.changedirectory

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal object DownloadPathFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            string("parse(...)"),
            opcode(Opcode.MOVE_RESULT_OBJECT),
        ),
    strings = listOf("guessFileName(...)", "setNotificationVisibility(...)"),
)

@Suppress("unused")
val changeDownloadDirPatch =
    bytecodePatch(
        name = "Custom download folder",
        description = "Change the download directory for video downloads",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val GETFOLDER_DESCRIPTOR =
                "$PREF_DESCRIPTOR;->getVideoFolder(Ljava/lang/String;)Ljava/lang/String;"

            val PUBLICFOLDER_DESCRIPTOR =
                "invoke-static {}, $PREF_DESCRIPTOR;->getPublicFolder()Ljava/lang/String;"

            DownloadPathFingerprint.method.apply {
                val moveResObjIndex = DownloadPathFingerprint.instructionMatches[1].index
                val fileNameReg = getInstruction<OneRegisterInstruction>(moveResObjIndex).registerA

                val insertAt =
                    instructions
                        .first { it.opcode == Opcode.INVOKE_VIRTUAL }
                        .location.index

                val publicFolderRegister = getInstruction<OneRegisterInstruction>(insertAt - 1).registerA
                addInstructions(
                    insertAt,
                    """
                    $PUBLICFOLDER_DESCRIPTOR
                    move-result-object v$publicFolderRegister
                    
                    invoke-static {v$fileNameReg}, $GETFOLDER_DESCRIPTOR
                    move-result-object v$fileNameReg
                    """.trimIndent(),
                )
                enableSettings("enableDownloadFolder")
            }
        }
    }
