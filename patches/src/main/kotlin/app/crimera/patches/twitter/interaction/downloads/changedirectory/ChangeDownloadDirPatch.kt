package app.crimera.patches.twitter.interaction.downloads.changedirectory

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal const val targetString = "parse(...)"
val downloadPathFingerprint =
    fingerprint {
        returns("V")
        strings(targetString, "guessFileName(...)", "setNotificationVisibility(...)")
    }

@Suppress("unused")
val changeDownloadDirPatch =
    bytecodePatch(
        name = "Custom download folder",
        description = "Change the download directory for video downloads",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val GETFOLDER_DESCRIPTOR =
                "$PREF_DESCRIPTOR;->getVideoFolder(Ljava/lang/String;)Ljava/lang/String;"

            val PUBLICFOLDER_DESCRIPTOR =
                "invoke-static {}, $PREF_DESCRIPTOR;->getPublicFolder()Ljava/lang/String;"

            downloadPathFingerprint.method.apply {
                val targetStringIndex = downloadPathFingerprint.stringMatches!!.find { it.string == targetString }!!.index
                val moveResObjIndex = indexOfFirstInstruction(targetStringIndex, Opcode.MOVE_RESULT_OBJECT)
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
                settingsStatusLoadFingerprint.enableSettings("enableDownloadFolder")
            }
        }
    }
