package crimera.patches.twitter.interaction.downloads.copyMediaLink

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.PATCHES_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal val downloadCallFingerprint = fingerprint {
    returns("V")
    strings(
        "downloadData",
        "activity.getString(R.str…nload_permission_request)",
        "isUseSnackbar"
    )
}


@Suppress("unused")
val copyMediaLink = bytecodePatch(
    name = "Add ability to copy media link",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val result by downloadCallFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        val method = result.mutableMethod

        val instructions = method.instructions

        val gotoLoc = instructions.first { it.opcode == Opcode.GOTO }.location.index

        val METHOD = """
            invoke-static{p0,p1}, ${PATCHES_DESCRIPTOR}/DownloadPatch;->mediaHandle(Ljava/lang/Object;Ljava/lang/Object;)V
        """.trimIndent()

        method.removeInstruction(gotoLoc - 1)
        method.addInstruction(gotoLoc - 1, METHOD)

        settingsStatusMatch.enableSettings("mediaLinkHandle")
    }
}