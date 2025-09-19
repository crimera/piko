package app.crimera.patches.twitter.interaction.downloads.copyMediaLink

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

val downloadCallFingerprint =
    fingerprint {
        accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
        returns("V")
        strings(
            "getString(...)",
            "isUseSnackbar",
        )
    }

@Suppress("unused")
val copyMediaLink =
    bytecodePatch(
        name = "Add ability to copy media link",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method = downloadCallFingerprint.method

            val instructions = method.instructions

            val gotoLoc = instructions.first { it.opcode == Opcode.GOTO }.location.index

            val METHOD =
                """
                invoke-static{p0,p1}, $PATCHES_DESCRIPTOR/DownloadPatch;->mediaHandle(Ljava/lang/Object;Ljava/lang/Object;)V
                """.trimIndent()

            method.removeInstruction(gotoLoc - 1)
            method.addInstruction(gotoLoc - 1, METHOD)

            settingsStatusLoadFingerprint.enableSettings("mediaLinkHandle")

            // end func
        }
    }
