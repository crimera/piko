package crimera.patches.twitter.interaction.downloads.changedirectory

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal val setDownloadDestinationFingerprint = fingerprint {
    returns("V")
    strings("parse(downloadData.url)")
}

@Suppress("unused")
val changeDownloadDirPatch = bytecodePatch(
    name = "Custom download folder",
    description = "Change the download directory for video downloads",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val GETFOLDER_DESCRIPTOR =
        "invoke-static {p1}, $PREF_DESCRIPTOR;->getVideoFolder(Ljava/lang/String;)Ljava/lang/String;"
    val PUBLICFOLDER_DESCRIPTOR =
        "invoke-static {}, $PREF_DESCRIPTOR;->getPublicFolder()Ljava/lang/String;"

    val result by setDownloadDestinationFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        val method = result.mutableMethod

        val insertAt = method.instructions
            .first { it.opcode == Opcode.INVOKE_VIRTUAL }.location.index

        val publicFolderRegister = method.getInstruction<OneRegisterInstruction>(insertAt - 1).registerA
        method.addInstructions(
            insertAt, """
            $PUBLICFOLDER_DESCRIPTOR
            move-result-object v$publicFolderRegister
            
            $GETFOLDER_DESCRIPTOR
            move-result-object p1
        """.trimIndent()
        )

        settingsStatusMatch.enableSettings("enableDownloadFolder")
    }
}