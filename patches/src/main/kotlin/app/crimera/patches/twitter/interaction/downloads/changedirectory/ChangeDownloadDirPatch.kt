package app.crimera.patches.twitter.interaction.downloads.changedirectory

import app.crimera.patches.twitter.interaction.downloads.copyMediaLink.downloadCallFingerprint
import app.crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val hideViewCountPatch =
    bytecodePatch(
        name = "Hide view count",
        description = "Hides the view count of Posts.",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val GETFOLDER_DESCRIPTOR =
                "invoke-static {p1}, $PREF_DESCRIPTOR;->getVideoFolder(Ljava/lang/String;)Ljava/lang/String;"

            val PUBLICFOLDER_DESCRIPTOR =
                "invoke-static {}, $PREF_DESCRIPTOR;->getPublicFolder()Ljava/lang/String;"

            val method =
                downloadCallFingerprint.classDef.methods.findLast { method ->
                    method.implementation
                        ?.instructions
                        ?.map { it.opcode }
                        ?.contains(Opcode.NEW_INSTANCE) == true
                } ?: throw PatchException("Error")

            val insertAt =
                method.instructions
                    .first { it.opcode == Opcode.INVOKE_VIRTUAL }
                    .location.index

            val publicFolderRegister = method.getInstruction<OneRegisterInstruction>(insertAt - 1).registerA
            method.addInstructions(
                insertAt,
                """
                $PUBLICFOLDER_DESCRIPTOR
                move-result-object v$publicFolderRegister
                
                $GETFOLDER_DESCRIPTOR
                move-result-object p1
                """.trimIndent(),
            )
            settingsStatusLoadFingerprint.method.enableSettings("enableDownloadFolder")
        }
    }
